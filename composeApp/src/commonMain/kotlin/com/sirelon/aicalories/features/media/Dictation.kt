package com.sirelon.sellsnap.features.media

import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.error_dictation_failed
import com.sirelon.sellsnap.generated.resources.error_dictation_permission
import org.jetbrains.compose.resources.StringResource

/**
 * Adds a dictated [phrase] at the end of [existing], a space apart. The recognizer writes each
 * phrase lower-case, so one that opens a sentence gets a capital.
 */
internal fun appendDictatedText(existing: String, phrase: String): String {
    val trimmed = existing.trimEnd()
    val opensSentence = trimmed.isEmpty() || trimmed.last() in ".!?"
    val separator = if (existing.isEmpty() || existing.last().isWhitespace()) "" else " "
    return existing + separator + if (opensSentence) phrase.replaceFirstChar { it.uppercaseChar() } else phrase
}

/** The `reason` suffix of a shown dictation error: `dictation_<code>`. */
internal val SpeechToTextError.analyticsCode: String
    get() = when (this) {
        SpeechToTextError.PermissionDenied -> "permission_denied"
        SpeechToTextError.NothingHeard -> "nothing_heard"
        SpeechToTextError.LanguageUnsupported -> "language_unsupported"
        is SpeechToTextError.Failed -> "failed:$code"
    }

internal val SpeechToTextError.messageRes: StringResource
    get() = when (this) {
        SpeechToTextError.PermissionDenied -> Res.string.error_dictation_permission
        SpeechToTextError.NothingHeard,
        SpeechToTextError.LanguageUnsupported,
        is SpeechToTextError.Failed -> Res.string.error_dictation_failed
    }

/** Silence and a refused permission are normal use, not defects. */
internal val SpeechToTextError.isDefect: Boolean
    get() = this is SpeechToTextError.Failed || this == SpeechToTextError.LanguageUnsupported
