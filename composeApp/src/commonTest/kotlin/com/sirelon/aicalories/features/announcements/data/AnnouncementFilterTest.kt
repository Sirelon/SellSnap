package com.sirelon.sellsnap.features.announcements.data

import com.sirelon.sellsnap.features.announcements.model.Announcement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private fun announcement(
    id: String = "a",
    showMode: Announcement.ShowMode = Announcement.ShowMode.Once,
    minVersion: String? = null,
    maxVersion: String? = null,
    userIds: Set<String> = emptySet(),
    olxUserIds: Set<Long> = emptySet(),
    priority: Int = 0,
) = Announcement(
    id = id,
    title = "Title",
    body = null,
    imageUrl = null,
    dismissible = true,
    showMode = showMode,
    minVersion = minVersion,
    maxVersion = maxVersion,
    userIds = userIds,
    olxUserIds = olxUserIds,
    priority = priority,
)

private fun select(
    vararg announcements: Announcement,
    appVersion: String = "3.3",
    installationId: String? = "fid-1",
    olxUserIds: Set<Long> = emptySet(),
    seenIds: Set<String> = emptySet(),
) = selectAnnouncement(
    announcements = announcements.toList(),
    appVersion = appVersion,
    installationId = installationId,
    olxUserIds = olxUserIds,
    seenIds = seenIds,
)?.id

class AnnouncementFilterTest {

    @Test
    fun `compares versions numerically per segment`() {
        assertEquals(-1, compareVersions("3.9", "3.10"))
        assertEquals(1, compareVersions("3.10", "3.9"))
        assertEquals(0, compareVersions("3.3", "3.3.0"))
        assertEquals(1, compareVersions("3.3.1", "3.3"))
    }

    @Test
    fun `a malformed version cannot be compared`() {
        assertNull(compareVersions("3.x", "3.3"))
        assertNull(compareVersions("3.3", ""))
        assertNull(compareVersions("3..3", "3.3"))
        assertNull(compareVersions("3.-1", "3.3"))
    }

    @Test
    fun `version bounds are inclusive`() {
        val ranged = announcement(minVersion = "3.2", maxVersion = "3.3")

        assertEquals("a", select(ranged, appVersion = "3.2"))
        assertEquals("a", select(ranged, appVersion = "3.3"))
        assertEquals("a", select(ranged, appVersion = "3.3.0"))
        assertNull(select(ranged, appVersion = "3.1.9"))
        assertNull(select(ranged, appVersion = "3.3.1"))
    }

    @Test
    fun `a bound is compared numerically, not as text`() {
        assertEquals("a", select(announcement(minVersion = "3.9"), appVersion = "3.10"))
        assertNull(select(announcement(maxVersion = "3.9"), appVersion = "3.10"))
    }

    @Test
    fun `an app version that cannot be compared hides a bounded announcement only`() {
        assertNull(select(announcement(minVersion = "3.0"), appVersion = "dev"))
        assertEquals("a", select(announcement(), appVersion = "dev"))
    }

    @Test
    fun `empty targeting is everyone`() {
        assertEquals("a", select(announcement(), installationId = null))
    }

    @Test
    fun `an installation id in userIds matches`() {
        val targeted = announcement(userIds = setOf("fid-1"))

        assertEquals("a", select(targeted, installationId = "fid-1"))
        assertNull(select(targeted, installationId = "fid-2"))
        assertNull(select(targeted, installationId = null))
    }

    @Test
    fun `any olx account in olxUserIds matches`() {
        val targeted = announcement(olxUserIds = setOf(42L))

        assertEquals("a", select(targeted, olxUserIds = setOf(7L, 42L)))
        assertNull(select(targeted, olxUserIds = setOf(7L)))
        assertNull(select(targeted, olxUserIds = emptySet()))
    }

    @Test
    fun `either kind of targeting is enough`() {
        val targeted = announcement(userIds = setOf("fid-1"), olxUserIds = setOf(42L))

        assertEquals("a", select(targeted, installationId = "fid-1", olxUserIds = emptySet()))
        assertEquals("a", select(targeted, installationId = "other", olxUserIds = setOf(42L)))
        assertNull(select(targeted, installationId = "other", olxUserIds = setOf(7L)))
    }

    @Test
    fun `a seen once announcement is dropped`() {
        assertNull(select(announcement(), seenIds = setOf("a")))
    }

    @Test
    fun `every launch ignores the seen set`() {
        val everyLaunch = announcement(showMode = Announcement.ShowMode.EveryLaunch)

        assertEquals("a", select(everyLaunch, seenIds = setOf("a")))
    }

    @Test
    fun `the highest priority wins`() {
        assertEquals("high", select(announcement(id = "low", priority = 1), announcement(id = "high", priority = 10)))
    }

    @Test
    fun `a seen announcement does not block a lower priority one`() {
        assertEquals(
            "low",
            select(
                announcement(id = "high", priority = 10),
                announcement(id = "low", priority = 1),
                seenIds = setOf("high"),
            ),
        )
    }

    @Test
    fun `a priority tie goes to the lowest id`() {
        assertEquals("a", select(announcement(id = "b"), announcement(id = "a")))
    }

    @Test
    fun `nothing eligible is null`() {
        assertNull(select())
    }
}
