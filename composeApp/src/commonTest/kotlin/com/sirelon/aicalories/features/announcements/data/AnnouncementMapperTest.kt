package com.sirelon.sellsnap.features.announcements.data

import com.sirelon.sellsnap.features.announcements.data.response.AnnouncementContentResponse
import com.sirelon.sellsnap.features.announcements.data.response.AnnouncementResponse
import com.sirelon.sellsnap.features.announcements.data.response.AnnouncementRulesResponse
import com.sirelon.sellsnap.features.announcements.model.Announcement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private fun response(
    active: Boolean? = true,
    title: Map<String, String>? = mapOf("en" to "Title"),
    rules: AnnouncementRulesResponse? = null,
) = AnnouncementResponse(
    active = active,
    content = AnnouncementContentResponse(
        title = title,
        body = mapOf("en" to "Body", "uk" to "Привіт, світе"),
        imageUrl = "https://example.com/a.png",
    ),
    rules = rules,
)

class AnnouncementMapperTest {

    @Test
    fun `maps a complete document`() {
        val announcement = response(
            rules = AnnouncementRulesResponse(
                dismissible = false,
                showMode = "everyLaunch",
                minVersion = "3.0",
                maxVersion = "3.3",
                userIds = listOf("fid-1"),
                olxUserIds = listOf(42L),
                priority = 7,
            ),
        ).toDomain("openai-outage", "en")

        assertEquals("openai-outage", announcement?.id)
        assertEquals("Title", announcement?.title)
        assertEquals("Body", announcement?.body)
        assertEquals("https://example.com/a.png", announcement?.imageUrl)
        assertEquals(false, announcement?.dismissible)
        assertEquals(Announcement.ShowMode.EveryLaunch, announcement?.showMode)
        assertEquals("3.0", announcement?.minVersion)
        assertEquals("3.3", announcement?.maxVersion)
        assertEquals(setOf("fid-1"), announcement?.userIds)
        assertEquals(setOf(42L), announcement?.olxUserIds)
        assertEquals(7, announcement?.priority)
    }

    @Test
    fun `a document with no rules gets the defaults`() {
        val announcement = response(rules = null).toDomain("a", "en")

        assertEquals(true, announcement?.dismissible)
        assertEquals(Announcement.ShowMode.Once, announcement?.showMode)
        assertNull(announcement?.minVersion)
        assertNull(announcement?.maxVersion)
        assertEquals(emptySet(), announcement?.userIds)
        assertEquals(emptySet(), announcement?.olxUserIds)
        assertEquals(0, announcement?.priority)
    }

    @Test
    fun `an unknown show mode reads as once`() {
        val announcement = response(rules = AnnouncementRulesResponse(showMode = "always")).toDomain("a", "en")

        assertEquals(Announcement.ShowMode.Once, announcement?.showMode)
    }

    @Test
    fun `an inactive or active-less document is dropped`() {
        assertNull(response(active = false).toDomain("a", "en"))
        assertNull(response(active = null).toDomain("a", "en"))
    }

    @Test
    fun `uses the device language and falls back to english`() {
        assertEquals("Привіт, світе", response().toDomain("a", "uk")?.body)
        assertEquals("Body", response().toDomain("a", "pl")?.body)
    }

    @Test
    fun `a russian device reads the ukrainian copy`() {
        val announcement = response(title = mapOf("en" to "Title", "uk" to "Вітаємо"))
            .toDomain("a", "ru")

        assertEquals("Вітаємо", announcement?.title)
        assertEquals("Привіт, світе", announcement?.body)
    }

    @Test
    fun `a russian device without ukrainian copy falls back to english`() {
        assertEquals("Title", response().toDomain("a", "ru")?.title)
    }

    @Test
    fun `a document with no title in the device language or english is dropped`() {
        assertNull(response(title = mapOf("uk" to "Вітаємо")).toDomain("a", "pl"))
        assertNull(response(title = null).toDomain("a", "en"))
        assertNull(response(title = mapOf("en" to " ")).toDomain("a", "en"))
    }

    @Test
    fun `a malformed version bound drops the document`() {
        assertNull(response(rules = AnnouncementRulesResponse(minVersion = "3.x")).toDomain("a", "en"))
        assertNull(response(rules = AnnouncementRulesResponse(maxVersion = "v3.3")).toDomain("a", "en"))
    }

    @Test
    fun `a blank version bound is no bound`() {
        val announcement = response(rules = AnnouncementRulesResponse(minVersion = " ")).toDomain("a", "en")

        assertNull(announcement?.minVersion)
    }
}
