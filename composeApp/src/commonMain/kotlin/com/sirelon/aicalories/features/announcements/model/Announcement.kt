package com.sirelon.sellsnap.features.announcements.model

/**
 * One announcement, already resolved for this device: [title] and [body] are in the device
 * language and the version bounds are known to be well-formed.
 */
data class Announcement(
    val id: String,
    val title: String,
    val body: String?,
    val imageUrl: String?,
    /** [ANNOUNCEMENT_LINK_STORE][com.sirelon.sellsnap.features.announcements.data.ANNOUNCEMENT_LINK_STORE] or an https URL; null when the document has none or it is malformed. */
    val link: String? = null,
    val dismissible: Boolean,
    val showMode: ShowMode,
    val minVersion: String?,
    val maxVersion: String?,
    val userIds: Set<String>,
    val olxUserIds: Set<Long>,
    val priority: Int,
) {
    enum class ShowMode {
        /** Shown on one launch, then never again on this device. */
        Once,

        /** Shown on every cold start. */
        EveryLaunch,
    }
}
