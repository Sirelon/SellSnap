package com.sirelon.sellsnap.features.seller.messages.domain

/**
 * One conversation between this account and a buyer about one advert. The list endpoint carries no
 * last-message time or preview, so the row has only [createdAt] (the thread's creation time).
 */
data class OlxThread(
    val id: Long,
    val advertId: Long,
    /** The other party's numeric id. Equals the account's own id on threads where the account is the buyer. */
    val interlocutorId: Long?,
    val unreadCount: Int,
    val createdAt: String,
)
