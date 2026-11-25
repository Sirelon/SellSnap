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
    /**
     * [ANNOUNCEMENT_LINK_STORE][com.sirelon.sellsnap.features.announcements.data.ANNOUNCEMENT_LINK_STORE],
     * an https URL or a known in-app `sellsnap://` link; null when the document has none or it is
     * malformed.
     */
    val link: String? = null,
    /** The link button's label in the device language; null means the generic label. */
    val buttonText: String? = null,
    val dismissible: Boolean,
    val showMode: ShowMode,
    val minVersion: String?,
    val maxVersion: String?,
    val userIds: Set<String>,
    val olxUserIds: Set<Long>,
    val priority: Int,
    /** Lowercase `android`, `ios`, `web` or `desktop`, plus any unknown value the document holds; empty means every platform. */
    val platforms: Set<String> = emptySet(),
    /**
     * Lowercase [OLX_CONNECTION_NONE][com.sirelon.sellsnap.features.announcements.data.OLX_CONNECTION_NONE],
     * `expired` or `connected`, plus any unknown value the document holds; empty means every device.
     */
    val olxConnections: Set<String> = emptySet(),
) {
    enum class ShowMode {
        /** Shown on one launch, then never again on this device. */
        Once,

        /** Shown on every cold start. */
        EveryLaunch,
    }
}
