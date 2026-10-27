package com.sirelon.sellsnap.features.media

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
actual fun rememberSpeechToText(
    languageTag: String?,
    onResult: (String) -> Unit,
    onError: (SpeechToTextError) -> Unit,
): SpeechToText? {
    val context = LocalContext.current
    // Needs the RecognitionService <queries> entry in the manifest, or this is false on API 30+.
    val isAvailable = remember(context) { SpeechRecognizer.isRecognitionAvailable(context) }
    if (!isAvailable || languageTag in unsupportedLanguageTags) return null

    val currentOnResult by rememberUpdatedState(onResult)
    val currentOnError by rememberUpdatedState(onError)
    val speechToText = remember(context, languageTag) {
        AndroidSpeechToText(
            context = context,
            languageTag = languageTag,
            onResult = { currentOnResult(it) },
            onError = { currentOnError(it) },
        )
    }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) speechToText.listen() else currentOnError(SpeechToTextError.PermissionDenied)
        }
    speechToText.requestPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }

    DisposableEffect(speechToText) {
        onDispose { speechToText.destroy() }
    }
    return speechToText
}

/**
 * Languages the recognizer turned down with ERROR_LANGUAGE_NOT_SUPPORTED. Android has no
 * up-front check that works below API 33, so the mic hides after the first refusal, for as long
 * as the app runs.
 */
private val unsupportedLanguageTags = mutableStateSetOf<String?>()

/**
 * One tap covers as many sentences as the seller says. On API 33+ that is one segmented session,
 * which delivers each phrase through `onSegmentResults` and keeps listening across pauses until
 * [SegmentedSessionSilenceMillis] of silence or a stop. Where the recognizer ignores segmentation,
 * or below API 33, a session ends at the first pause and each result starts the next one; some
 * recognizers play their start sound on every restart.
 */
// RecognizerIntent.EXTRA_SEGMENTED_SESSION (developer.android.com/reference/android/speech/RecognizerIntent):
// the extra named as the session type must be set in the same intent.
private const val SegmentedSessionSilenceMillis = 5_000

private const val RmsSilenceDb = -2f
private const val RmsLoudDb = 10f

private class AndroidSpeechToText(
    private val context: Context,
    private val languageTag: String?,
    private val onResult: (String) -> Unit,
    private val onError: (SpeechToTextError) -> Unit,
) : SpeechToText {

    override var isListening by mutableStateOf(false)
        private set

    // Mic loudness from the recognizer, reset to silence when a session starts. Zero when idle.
    private var rawLevel by mutableFloatStateOf(0f)
    override val level: Float get() = if (isListening) rawLevel else 0f

    var requestPermission: () -> Unit = {}

    private var recognizer: SpeechRecognizer? = null
    private var stopRequested = false
    private var heardAnything = false

    override fun start() {
        if (isListening) return
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) listen() else requestPermission()
    }

    fun listen() {
        stopRequested = false
        heardAnything = false
        rawLevel = 0f
        isListening = true
        startSession()
    }

    override fun stop() {
        if (!isListening) return
        stopRequested = true
        recognizer?.stopListening()
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
        isListening = false
    }

    private fun startSession() {
        val recognizer = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also {
            it.setRecognitionListener(listener)
            recognizer = it
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            languageTag?.let { putExtra(RecognizerIntent.EXTRA_LANGUAGE, it) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                putExtra(
                    RecognizerIntent.EXTRA_SEGMENTED_SESSION,
                    RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                )
                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                    SegmentedSessionSilenceMillis,
                )
            }
        }
        recognizer.startListening(intent)
    }

    private val listener = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            val text = results?.recognizedText().orEmpty()
            if (text.isNotEmpty()) {
                heardAnything = true
                onResult(text)
            }
            when {
                !stopRequested -> startSession()
                heardAnything -> isListening = false
                else -> finishWith(SpeechToTextError.NothingHeard)
            }
        }

        override fun onError(error: Int) {
            val silence = error == SpeechRecognizer.ERROR_NO_MATCH ||
                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            when {
                // Once some text is in, any end is the end of the dictation the seller asked for:
                // a pause, a stop tap, or a restart from onResults that some recognizers refuse
                // with ERROR_CLIENT or ERROR_RECOGNIZER_BUSY.
                heardAnything -> isListening = false
                silence || stopRequested -> finishWith(SpeechToTextError.NothingHeard)
                error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    finishWith(SpeechToTextError.PermissionDenied)

                error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> {
                    unsupportedLanguageTags += languageTag
                    finishWith(SpeechToTextError.LanguageUnsupported)
                }

                else -> finishWith(SpeechToTextError.Failed("android_$error"))
            }
        }

        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit

        // SpeechRecognizer documents no range; about -2 dB (silence) to 10 dB (loud) is typical.
        override fun onRmsChanged(rmsdB: Float) {
            rawLevel = ((rmsdB - RmsSilenceDb) / (RmsLoudDb - RmsSilenceDb)).coerceIn(0f, 1f)
        }

        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onPartialResults(partialResults: Bundle?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onSegmentResults(segmentResults: Bundle) {
            val text = segmentResults.recognizedText()
            if (text.isNotEmpty()) {
                heardAnything = true
                onResult(text)
            }
        }

        override fun onEndOfSegmentedSession() {
            if (heardAnything) isListening = false else finishWith(SpeechToTextError.NothingHeard)
        }
    }

    private fun Bundle.recognizedText(): String =
        getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()

    private fun finishWith(error: SpeechToTextError) {
        isListening = false
        onError(error)
    }
}
