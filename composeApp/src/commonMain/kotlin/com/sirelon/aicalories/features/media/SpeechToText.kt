package com.sirelon.sellsnap.features.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

/**
 * Dictation through the platform's own speech recognizer (SIR-85). It listens until [stop] and
 * hands every recognized phrase to the `onResult` of [rememberSpeechToText] as it arrives.
 */
@Stable
interface SpeechToText {
    /** True from [start] until listening ends, by [stop], an error, or the recognizer's own limit. */
    val isListening: Boolean

    /**
     * Microphone loudness from 0 (silence) to 1 (loud), about 15 updates a second while
     * [isListening]; 0 otherwise. Read it in a draw or animation block to avoid recomposing.
     */
    val level: Float

    /**
     * The words heard so far that have not reached `onResult` yet, updated as the recognizer
     * hears them. Empty when idle, and emptied once they are delivered.
     */
    val partialText: String

    /** Asks for the microphone and speech permissions if needed, then listens. */
    fun start()

    /** Stops listening. The text heard so far still reaches `onResult`. */
    fun stop()
}

sealed interface SpeechToTextError {
    data object PermissionDenied : SpeechToTextError

    /** Listening ended before any speech was recognized. */
    data object NothingHeard : SpeechToTextError

    /**
     * The recognizer turned down the language only once asked. The mic hides from then on, so
     * this arrives once per language.
     */
    data object LanguageUnsupported : SpeechToTextError

    /** [code] names the platform error, for telemetry only. */
    data class Failed(val code: String) : SpeechToTextError
}

/**
 * Listens in [languageTag], a BCP-47 tag such as `pl-PL`; null listens in the device language.
 * Null where the platform has no speech recognizer, or none for that language, so the caller
 * hides the mic.
 */
@Composable
expect fun rememberSpeechToText(
    languageTag: String?,
    onResult: (String) -> Unit,
    onError: (SpeechToTextError) -> Unit,
): SpeechToText?
