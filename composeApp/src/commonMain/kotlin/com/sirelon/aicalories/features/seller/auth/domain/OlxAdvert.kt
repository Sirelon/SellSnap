package com.sirelon.sellsnap.features.seller.auth.domain

data class OlxAdvert(
    val id: Long,
    val title: String,
    val status: OlxAdvertStatus,
    val url: String,
    val primaryImageUrl: String?,
    val price: OlxAdvertPrice?,
    val createdAt: String,
    val validTo: String,
)

data class OlxAdvertPrice(
    val value: Float,
    val currency: String,
    val negotiable: Boolean,
)

enum class OlxAdvertStatus {
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
        fun from(value: String): OlxAdvertStatus = when (value) {
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
