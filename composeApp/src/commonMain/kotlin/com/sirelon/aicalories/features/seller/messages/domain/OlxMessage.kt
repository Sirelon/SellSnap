package com.sirelon.sellsnap.features.seller.messages.domain

data class OlxMessage(
    val id: Long,
    val createdAt: String,
    /** True for `type: sent` (written by the account), false for `received`. */
    val isSent: Boolean,
    val text: String,
    val attachmentUrls: List<String>,
)
