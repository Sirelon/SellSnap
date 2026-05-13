package com.sirelon.sellsnap.features.seller.ad.publish_success

import kotlinx.serialization.Serializable

@Serializable
enum class AdvertStatus {
    New,
    Active,
    Limited,
    RemovedByUser,
    Outdated,
    Unconfirmed,
    Unpaid,
    Moderated,
    Blocked,
    Disabled,
    RemovedByModerator,
    Unknown;

    companion object {
        fun from(value: String): AdvertStatus = when (value) {
            "new" -> New
            "active" -> Active
            "limited" -> Limited
            "removed_by_user" -> RemovedByUser
            "outdated" -> Outdated
            "unconfirmed" -> Unconfirmed
            "unpaid" -> Unpaid
            "moderated" -> Moderated
            "blocked" -> Blocked
            "disabled" -> Disabled
            "removed_by_moderator" -> RemovedByModerator
            else -> Unknown
        }
    }
}

@Serializable
data class PublishSuccessData(
    val url: String,
    val title: String,
    val priceFormatted: String,
    val primaryImageUrl: String?,
    val totalElapsedMs: Long,
    val status: AdvertStatus = AdvertStatus.Unknown,
)
