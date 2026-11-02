package com.sirelon.sellsnap.features.seller.messages.data.response

import com.sirelon.sellsnap.features.seller.messages.domain.OlxMessage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal class OlxMessagesRootResponse(
    @SerialName("data")
    val data: List<OlxMessageResponse>?,
)

/** `Message` - developer.olx.ua swagger v2, components/schemas/Message. Only the fields the app reads. */
@Serializable
internal class OlxMessageResponse(
    @SerialName("id")
    val id: Long?,

    @SerialName("created_at")
    val createdAt: String?,

    @SerialName("type")
    val type: String?,

    @SerialName("text")
    val text: String?,

    @SerialName("attachments")
    val attachments: List<OlxMessageAttachmentResponse>?,
) {
    fun toDomain(): OlxMessage? {
        val messageId = id ?: return null
        return OlxMessage(
            id = messageId,
            createdAt = createdAt.orEmpty(),
            // Spec enum: sent | received.
            isSent = type == "sent",
            text = text.orEmpty(),
            attachmentUrls = attachments.orEmpty().mapNotNull { it.url?.takeIf(String::isNotBlank) },
        )
    }
}

@Serializable
internal class OlxMessageAttachmentResponse(
    @SerialName("url")
    val url: String?,
)
