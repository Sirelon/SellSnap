package com.sirelon.sellsnap.features.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import platform.AVFAudio.AVAudioApplication
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionDuckOthers
import platform.AVFAudio.AVAudioSessionCategoryRecord
import platform.AVFAudio.AVAudioSessionModeMeasurement
import platform.AVFAudio.AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation
import platform.AVFAudio.setActive
import platform.Foundation.NSError
import platform.Foundation.NSLocale
import platform.Speech.SFSpeechAudioBufferRecognitionRequest
import platform.Speech.SFSpeechRecognitionTask
import platform.Speech.SFSpeechRecognizer
import platform.Speech.SFSpeechRecognizerAuthorizationStatus
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

@Composable
actual fun rememberSpeechToText(
    onResult: (String) -> Unit,
    onError: (SpeechToTextError) -> Unit,
): SpeechToText? {
    val currentOnResult by rememberUpdatedState(onResult)
    val currentOnError by rememberUpdatedState(onError)
    val speechToText = remember {
        IosSpeechToText(
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
 * Streams the microphone into one recognition task until [stop], then hands over the whole
 * transcript at once. Apple caps one task at about a minute; the task then ends by itself and
 * the transcript so far is delivered the same way.
 */
private class IosSpeechToText(
    private val onResult: (String) -> Unit,
    private val onError: (SpeechToTextError) -> Unit,
) : SpeechToText {

    override var isListening by mutableStateOf(false)
        private set

    private var audioEngine: AVAudioEngine? = null
    private var request: SFSpeechAudioBufferRecognitionRequest? = null
    private var task: SFSpeechRecognitionTask? = null
    private var transcript = ""

    override fun start(languageTag: String?) {
        if (isListening) return
        isListening = true
        SFSpeechRecognizer.requestAuthorization { status ->
            onMain {
                if (status != SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusAuthorized) {
                    finish(SpeechToTextError.PermissionDenied)
                    return@onMain
                }
                AVAudioApplication.requestRecordPermissionWithCompletionHandler { granted ->
                    onMain {
                        if (!isListening) return@onMain
                        if (granted) listen(languageTag) else finish(SpeechToTextError.PermissionDenied)
                    }
                }
            }
        }
    }

    override fun stop() {
        if (!isListening) return
        stopAudio()
        request?.endAudio()
    }

    fun cancel() {
        if (!isListening) return
        task?.cancel()
        task = null
        stopAudio()
        isListening = false
    }

    private fun listen(languageTag: String?) {
        val recognizer = if (languageTag != null) {
            SFSpeechRecognizer(locale = NSLocale(localeIdentifier = languageTag))
        } else {
            SFSpeechRecognizer()
        }
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
            buffer?.let(request::appendAudioPCMBuffer)
        }
        engine.prepare()
        if (!engine.startAndReturnError(null)) {
            input.removeTapOnBus(0u)
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
                if (!text.isNullOrBlank()) transcript = text
                if (error != null || isFinal) complete(error)
            }
        }
    }

    private fun complete(error: NSError?) {
        if (!isListening) return
        task = null
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

    private fun stopAudio() {
        audioEngine?.let { engine ->
            engine.stop()
            engine.inputNode.removeTapOnBus(0u)
        }
        audioEngine = null
        AVAudioSession.sharedInstance()
            .setActive(false, withOptions = AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation, error = null)
    }

    private fun finish(error: SpeechToTextError) {
        isListening = false
        onError(error)
    }

    private fun onMain(block: () -> Unit) {
        dispatch_async(dispatch_get_main_queue(), block)
    }
}
