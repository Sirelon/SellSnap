package com.sirelon.sellsnap.features.whatsnew.data

import com.sirelon.sellsnap.features.whatsnew.data.response.ReleaseChangeResponse
import com.sirelon.sellsnap.features.whatsnew.data.response.ReleaseResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReleaseMapperTest {

    @Test
    fun `maps a complete document to a release`() {
        val release = ReleaseResponse(
            version = "2.3",
            date = "2026-08-25",
            active = true,
            changes = listOf(
                ReleaseChangeResponse(
                    id = "faster-uploads",
                    icon = "upload",
                    title = mapOf("en" to "Faster photo uploads"),
                    summary = mapOf("en" to "Photos upload in the background now."),
                    detail = mapOf("en" to "More detail here."),
                ),
            ),
        ).toDomain("en")

        assertEquals("2.3", release?.version)
        assertEquals("2026-08-25", release?.date)
        assertEquals(1, release?.changes?.size)
        assertEquals("faster-uploads", release?.changes?.get(0)?.id)
        assertEquals("upload", release?.changes?.get(0)?.icon)
        assertEquals("Faster photo uploads", release?.changes?.get(0)?.title)
        assertEquals("Photos upload in the background now.", release?.changes?.get(0)?.summary)
        assertEquals("More detail here.", release?.changes?.get(0)?.detail)
    }

    @Test
    fun `drops a release with a blank version`() {
        val release = ReleaseResponse(
            version = "",
            date = "2026-08-25",
            changes = listOf(ReleaseChangeResponse(title = mapOf("en" to "Something"))),
        ).toDomain("en")

        assertNull(release)
    }

    @Test
    fun `drops a release with a missing date`() {
        val release = ReleaseResponse(
            version = "2.3",
            date = null,
            changes = listOf(ReleaseChangeResponse(title = mapOf("en" to "Something"))),
        ).toDomain("en")

        assertNull(release)
    }

    @Test
    fun `drops a release explicitly marked inactive`() {
        val release = ReleaseResponse(
            version = "2.3",
            date = "2026-08-25",
            active = false,
            changes = listOf(ReleaseChangeResponse(title = mapOf("en" to "Something"))),
        ).toDomain("en")

        assertNull(release)
    }

    @Test
    fun `drops a release whose only changes have blank titles`() {
        val release = ReleaseResponse(
            version = "2.3",
            date = "2026-08-25",
            changes = listOf(
                ReleaseChangeResponse(title = mapOf("en" to "")),
                ReleaseChangeResponse(title = null),
            ),
        ).toDomain("en")

        assertNull(release)
    }

    @Test
    fun `keeps valid changes and drops only the invalid ones`() {
        val release = ReleaseResponse(
            version = "2.3",
            date = "2026-08-25",
            changes = listOf(
                ReleaseChangeResponse(title = mapOf("en" to "Kept")),
                ReleaseChangeResponse(title = mapOf("en" to "")),
            ),
        ).toDomain("en")

        assertEquals(1, release?.changes?.size)
        assertEquals("Kept", release?.changes?.get(0)?.title)
    }

    @Test
    fun `defaults a missing change id to its title and a missing icon to blank`() {
        val release = ReleaseResponse(
            version = "2.3",
            date = "2026-08-25",
            changes = listOf(ReleaseChangeResponse(id = null, icon = null, title = mapOf("en" to "Kept"))),
        ).toDomain("en")

        assertEquals("Kept", release?.changes?.get(0)?.id)
        assertEquals("", release?.changes?.get(0)?.icon)
    }

    @Test
    fun `resolves text for the requested language`() {
        val release = ReleaseResponse(
            version = "2.3",
            date = "2026-08-25",
            changes = listOf(
                ReleaseChangeResponse(
                    title = mapOf("en" to "Faster uploads", "uk" to "Швидше завантаження"),
                    summary = mapOf("en" to "Uploads are faster.", "uk" to "Завантаження стало швидшим."),
                ),
            ),
        ).toDomain("uk")

        assertEquals("Швидше завантаження", release?.changes?.get(0)?.title)
        assertEquals("Завантаження стало швидшим.", release?.changes?.get(0)?.summary)
    }

    @Test
    fun `falls back to english when the requested language is missing`() {
        val release = ReleaseResponse(
            version = "2.3",
            date = "2026-08-25",
            changes = listOf(
                ReleaseChangeResponse(
                    title = mapOf("en" to "Faster uploads"),
                    summary = mapOf("en" to "Uploads are faster."),
                ),
            ),
        ).toDomain("uk")

        assertEquals("Faster uploads", release?.changes?.get(0)?.title)
        assertEquals("Uploads are faster.", release?.changes?.get(0)?.summary)
    }

    @Test
    fun `drops a change with no title in the requested language or english`() {
        val release = ReleaseResponse(
            version = "2.3",
            date = "2026-08-25",
            changes = listOf(ReleaseChangeResponse(title = mapOf("uk" to "Тільки українською"))),
        ).toDomain("pl")

        assertNull(release)
    }
}
