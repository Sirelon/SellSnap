@file:OptIn(ExperimentalForeignApi::class)

package com.sirelon.sellsnap.features.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.get
import platform.AVFAudio.AVAudioApplication
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionDuckOthers
import platform.AVFAudio.AVAudioSessionCategoryRecord
import platform.AVFAudio.AVAudioSessionModeMeasurement
import platform.AVFAudio.AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation
import platform.AVFAudio.setActive
import platform.Foundation.NSError
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.languageCode
import platform.Foundation.localeIdentifier
import platform.Speech.SFSpeechAudioBufferRecognitionRequest
import platform.Speech.SFSpeechRecognitionTask
import platform.Speech.SFSpeechRecognizer
import platform.Speech.SFSpeechRecognizerAuthorizationStatus
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.math.log10
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

@Composable
actual fun rememberSpeechToText(
    languageTag: String?,
    onResult: (String) -> Unit,
    onError: (SpeechToTextError) -> Unit,
): SpeechToText? {
    // Apple has no recognizer for some listing languages, such as Bulgarian; the mic then hides.
    val recognizer = remember(languageTag) { speechRecognizer(languageTag) } ?: return null
    val currentOnResult by rememberUpdatedState(onResult)
    val currentOnError by rememberUpdatedState(onError)
    val speechToText = remember(recognizer) {
        IosSpeechToText(
            recognizer = recognizer,
            onResult = { currentOnResult(it) },
            onError = { currentOnError(it) },
        )
    }
    DisposableEffect(speechToText) {
        onDispose { speechToText.cancel() }
    }
    return speechToText
}

/**
 * Null for a language Apple has no recognizer for. Its initializer returns nil then, which a
 * Kotlin constructor cannot hold, so the language is checked against the supported list first.
 */
private fun speechRecognizer(languageTag: String?): SFSpeechRecognizer? {
    val supported = SFSpeechRecognizer.supportedLocales()
        .map { (it as NSLocale).localeIdentifier.toLocaleKey() }
    return if (languageTag != null) {
        if (languageTag.toLocaleKey() in supported) SFSpeechRecognizer(NSLocale(languageTag)) else null
    } else {
        // The default recognizer follows the device language, whatever its region.
        val language = NSLocale.currentLocale.languageCode.lowercase()
        if (supported.any { it.substringBefore('-') == language }) SFSpeechRecognizer() else null
    }
}

/** `uk_UA@rg=uazzzz` and `uk-UA` both become `uk-ua`. */
private fun String.toLocaleKey(): String = substringBefore('@').replace('_', '-').lowercase()

private val LevelPostInterval = 66.milliseconds

// Speech into a phone mic sits around -30..-10 dBFS.
private const val LevelFloorDb = -50f
private const val LevelCeilingDb = -10f

/**
 * Streams the microphone into one recognition task until [stop], then hands over the whole
 * transcript at once. Apple caps one task at about a minute; the task then ends by itself and
 * the transcript so far is delivered the same way. The class holds [recognizer] for as long as
 * a task may run, so it is not freed under it.
 */
private class IosSpeechToText(
    private val recognizer: SFSpeechRecognizer,
    private val onResult: (String) -> Unit,
    private val onError: (SpeechToTextError) -> Unit,
) : SpeechToText {

    override var isListening by mutableStateOf(false)
        private set

    // Loudness of the latest posted audio buffer. Zero when idle.
    private var rawLevel by mutableFloatStateOf(0f)
    override val level: Float get() = if (isListening) rawLevel else 0f

    // Only the audio thread's tap touches this.
    private var lastLevelPost = TimeSource.Monotonic.markNow()

    private var audioEngine: AVAudioEngine? = null
    private var request: SFSpeechAudioBufferRecognitionRequest? = null
    private var task: SFSpeechRecognitionTask? = null
    private var transcript = ""

    // Each dictation gets its own number, and the number moves on when the dictation ends. A
    // permission answer or recognition callback that carries an old number is ignored, so a late
    // callback cannot write into the next dictation.
    private var generation = 0

    override fun start() {
        if (isListening) return
        isListening = true
        rawLevel = 0f
        val generation = ++this.generation
        SFSpeechRecognizer.requestAuthorization { status ->
            onMain {
                if (generation != this.generation) return@onMain
                if (status != SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusAuthorized) {
                    finish(SpeechToTextError.PermissionDenied)
                    return@onMain
                }
                AVAudioApplication.requestRecordPermissionWithCompletionHandler { granted ->
                    onMain {
                        if (generation != this.generation) return@onMain
                        if (granted) listen(generation) else finish(SpeechToTextError.PermissionDenied)
                    }
                }
            }
        }
    }

    override fun stop() {
        if (!isListening) return
        if (request == null) {
            // Still behind a permission prompt: nothing is recorded yet, so the stop ends it here.
            generation++
            isListening = false
            return
        }
        stopAudio()
        request?.endAudio()
    }

    fun cancel() {
        if (!isListening) return
        generation++
        task?.cancel()
        task = null
        request = null
        stopAudio()
        isListening = false
    }

    private fun listen(generation: Int) {
        if (!recognizer.isAvailable()) {
            finish(SpeechToTextError.Failed("ios_recognizer_unavailable"))
            return
        }

        val session = AVAudioSession.sharedInstance()
        session.setCategory(
            AVAudioSessionCategoryRecord,
            mode = AVAudioSessionModeMeasurement,
            options = AVAudioSessionCategoryOptionDuckOthers,
            error = null,
        )
        session.setActive(true, withOptions = AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation, error = null)

        val request = SFSpeechAudioBufferRecognitionRequest().apply {
            shouldReportPartialResults = true
        }
        val engine = AVAudioEngine()
        val input = engine.inputNode
        input.installTapOnBus(0u, bufferSize = 1024u, format = input.outputFormatForBus(0u)) { buffer, _ ->
            buffer?.let {
                request.appendAudioPCMBuffer(it)
                postLevel(it, generation)
            }
        }
        engine.prepare()
        if (!engine.startAndReturnError(null)) {
            input.removeTapOnBus(0u)
            deactivateAudioSession()
            finish(SpeechToTextError.Failed("ios_audio_engine_start"))
            return
        }

        this.request = request
        this.audioEngine = engine
        transcript = ""
        task = recognizer.recognitionTaskWithRequest(request) { result, error ->
            val text = result?.bestTranscription?.formattedString
            val isFinal = result?.isFinal() == true
            onMain {
                if (generation != this.generation) return@onMain
                if (!text.isNullOrBlank()) transcript = text
                if (error != null || isFinal) complete(error)
            }
        }
    }

    /**
     * Runs on the audio thread for every buffer. Takes the RMS of the first channel, maps
     * [LevelFloorDb]..[LevelCeilingDb] to 0..1, and posts it to the main thread at most
     * every [LevelPostInterval].
     */
    private fun postLevel(buffer: AVAudioPCMBuffer, generation: Int) {
        if (lastLevelPost.elapsedNow() < LevelPostInterval) return
        val samples = buffer.floatChannelData?.get(0) ?: return
        val frames = buffer.frameLength.toInt()
        if (frames == 0) return
        var sumOfSquares = 0f
        for (i in 0 until frames) {
            val sample = samples[i]
            sumOfSquares += sample * sample
        }
        val db = 20f * log10(sqrt(sumOfSquares / frames).coerceAtLeast(1e-6f))
        val level = ((db - LevelFloorDb) / (LevelCeilingDb - LevelFloorDb)).coerceIn(0f, 1f)
        lastLevelPost = TimeSource.Monotonic.markNow()
        onMain {
            if (generation == this.generation) rawLevel = level
        }
    }

    private fun complete(error: NSError?) {
        if (!isListening) return
        generation++
        task = null
        request = null
        stopAudio()
        val text = transcript.trim()
        when {
            text.isNotEmpty() -> {
                isListening = false
                onResult(text)
            }
            // kAFAssistantErrorDomain 1110: no speech detected.
            error == null || error.code == 1110L -> finish(SpeechToTextError.NothingHeard)
            else -> finish(SpeechToTextError.Failed("ios_${error.domain}_${error.code}"))
        }
    }

    /**
     * Only the instance that started an engine releases the audio session. The polish sheet runs
     * its own instance, and a description dictation that ends late must not cut off its audio.
     */
    private fun stopAudio() {
        val engine = audioEngine ?: return
        engine.stop()
        engine.inputNode.removeTapOnBus(0u)
        audioEngine = null
        deactivateAudioSession()
    }

    private fun deactivateAudioSession() {
        AVAudioSession.sharedInstance()
            .setActive(false, withOptions = AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation, error = null)
    }

    private fun finish(error: SpeechToTextError) {
        generation++
        isListening = false
        onError(error)
    }

    private fun onMain(block: () -> Unit) {
        dispatch_async(dispatch_get_main_queue(), block)
    }
}
