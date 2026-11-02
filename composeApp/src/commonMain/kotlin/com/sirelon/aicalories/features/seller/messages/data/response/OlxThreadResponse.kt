package com.sirelon.sellsnap.features.seller.messages.data.response

import com.sirelon.sellsnap.features.seller.messages.domain.OlxThread
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal class OlxThreadsRootResponse(
    @SerialName("data")
    val data: List<OlxThreadResponse>?,
)

/** `Thread` - developer.olx.ua swagger v2, components/schemas/Thread. Only the fields the app reads. */
@Serializable
internal class OlxThreadResponse(
    @SerialName("id")
    val id: Long?,

    @SerialName("advert_id")
    val advertId: Long?,

    @SerialName("interlocutor_id")
    val interlocutorId: Long?,

    @SerialName("unread_count")
    val unreadCount: Int?,

    @SerialName("created_at")
    val createdAt: String?,
) {
    /** A thread without an advert has nothing to open or show, so it is dropped. */
    fun toDomain(): OlxThread? {
        val threadId = id ?: return null
        val advert = advertId ?: return null
        return OlxThread(
            id = threadId,
            advertId = advert,
            interlocutorId = interlocutorId,
            unreadCount = unreadCount ?: 0,
            createdAt = createdAt.orEmpty(),
        )
    }
}
