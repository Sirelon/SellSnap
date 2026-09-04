package com.sirelon.sellsnap.features.seller.auth.data.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `GET adverts/{id}/moderation-reason` - OLX's own explanation of why it put an advert in the
 * state it is in.
 *
 * This exists because the status vocabulary cannot be reasoned about. OLX's spec documents only
 * four values on the `Advert.status` enum (`new`, `active`, `limited`, `removed_by_user`) while
 * its prose mentions eight more, with no per-status definitions and no lifecycle table - and a
 * real account reported `disabled` for a listing that was live and editable on OLX's own site.
 * Rather than encode a guessed meaning per status, ask OLX and quote the answer.
 *
 * Unwrapped, unlike most OLX resources: the schema is the object itself with no `data` key.
 */
@Serializable
internal class OlxModerationReasonResponse(
    /** HTML, per the spec - the same text OLX emails the seller. */
    @SerialName("email_notification")
    val emailNotification: String?,
) {
    /**
     * The reason as plain text, or null when OLX has nothing to say. Tags are stripped rather
     * than rendered: this lands in a sentence inside a bottom sheet, and OLX controls the markup.
     */
    fun toDomain(): String? = emailNotification?.htmlToPlainText()?.takeIf { it.isNotBlank() }
}

private val HtmlTag = Regex("<[^>]*>")
private val WhitespaceRun = Regex("\\s+")

private fun String.htmlToPlainText(): String =
    replace("<br>", "\n")
        .replace("<br/>", "\n")
        .replace("<br />", "\n")
        .replace("</p>", "\n")
        .replace(HtmlTag, "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace(WhitespaceRun, " ")
        .trim()
