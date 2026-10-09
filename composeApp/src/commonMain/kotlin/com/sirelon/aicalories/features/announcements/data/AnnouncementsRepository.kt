package com.sirelon.sellsnap.features.announcements.data

import com.sirelon.sellsnap.features.announcements.model.Announcement

interface AnnouncementsRepository {
    /**
     * Every active announcement, content resolved for the device language and unfiltered by
     * version or targeting. Empty when the fetch failed or nothing is active - callers show
     * nothing, never an error.
     */
    suspend fun getAnnouncements(): List<Announcement>

    /**
     * The Firebase installation ID of this app install, the value an announcement's `userIds`
     * targets. Null when unavailable (offline first launch, or a platform without Firebase).
     */
    suspend fun getInstallationId(): String?
}
