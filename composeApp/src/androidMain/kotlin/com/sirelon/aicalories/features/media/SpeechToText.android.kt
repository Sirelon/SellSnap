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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
actual fun rememberSpeechToText(
    onResult: (String) -> Unit,
    onError: (SpeechToTextError) -> Unit,
): SpeechToText? {
    val context = LocalContext.current
    // Needs the RecognitionService <queries> entry in the manifest, or this is false on API 30+.
    val isAvailable = remember(context) { SpeechRecognizer.isRecognitionAvailable(context) }
    if (!isAvailable) return null

    val currentOnResult by rememberUpdatedState(onResult)
    val currentOnError by rememberUpdatedState(onError)
    val speechToText = remember(context) {
        AndroidSpeechToText(
            context = context,
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
 * One tap covers as many sentences as the seller says. On API 33+ that is one segmented session,
 * which delivers each phrase through `onSegmentResults` and keeps listening across pauses until
 * [SegmentedSessionSilenceMillis] of silence or a stop. Where the recognizer ignores segmentation,
 * or below API 33, a session ends at the first pause and each result starts the next one; some
 * recognizers play their start sound on every restart.
 */
// RecognizerIntent.EXTRA_SEGMENTED_SESSION (developer.android.com/reference/android/speech/RecognizerIntent):
// the extra named as the session type must be set in the same intent.
private const val SegmentedSessionSilenceMillis = 5_000

private class AndroidSpeechToText(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onError: (SpeechToTextError) -> Unit,
) : SpeechToText {

    override var isListening by mutableStateOf(false)
        private set

    var requestPermission: () -> Unit = {}

    private var languageTag: String? = null
    private var recognizer: SpeechRecognizer? = null
    private var stopRequested = false
    private var heardAnything = false

    override fun start(languageTag: String?) {
        if (isListening) return
        this.languageTag = languageTag
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) listen() else requestPermission()
    }

    fun listen() {
        stopRequested = false
        heardAnything = false
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
                // A pause after some speech, or a stop tap, ends the dictation the seller asked for.
                (silence || stopRequested) && heardAnything -> isListening = false
                silence || stopRequested -> finishWith(SpeechToTextError.NothingHeard)
                error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    finishWith(SpeechToTextError.PermissionDenied)

                else -> finishWith(SpeechToTextError.Failed("android_$error"))
            }
        }

        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
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
