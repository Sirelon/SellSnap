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
     * Asks for the microphone and speech permissions if needed, then listens. [languageTag] is a
     * BCP-47 tag such as `pl-PL`; null listens in the device language.
     */
    fun start(languageTag: String?)

    /** Stops listening. The text heard so far still reaches `onResult`. */
    fun stop()
}

sealed interface SpeechToTextError {
    data object PermissionDenied : SpeechToTextError

    /** Listening ended before any speech was recognized. */
    data object NothingHeard : SpeechToTextError

    /** [code] names the platform error, for telemetry only. */
    data class Failed(val code: String) : SpeechToTextError
}

/** Null where the platform has no speech recognizer, so the caller hides the mic. */
@Composable
expect fun rememberSpeechToText(
    onResult: (String) -> Unit,
    onError: (SpeechToTextError) -> Unit,
): SpeechToText?
