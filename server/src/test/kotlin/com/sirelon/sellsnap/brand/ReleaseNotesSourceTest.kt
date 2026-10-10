package com.sirelon.sellsnap.brand

import com.sirelon.sellsnap.fakes.FakeReleaseNotesSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ReleaseNotesSourceTest {
    // The document shape `scripts/publish-release-notes.mjs` writes, as the Firestore client returns it.
    private fun doc(active: Boolean? = true) = buildMap<String, Any?> {
        put("version", "3.3")
        put("date", "2026-10-01")
        if (active != null) put("active", active)
        put(
            "changes",
            listOf(
                mapOf(
                    "id" to "drafts",
                    "icon" to "feature",
                    "title" to mapOf("en" to "Drafts", "uk" to "Чернетки", "pl" to "Szkice", "bg" to "Чернови", "ro" to "Ciorne"),
                    "summary" to mapOf("en" to "Keep unfinished ads.", "uk" to "Зберігайте чернетки.", "pl" to "Zachowaj szkice."),
                    "detail" to mapOf("en" to "More text."),
                ),
            ),
        )
    }

    @Test
    fun readsOnlyEnglishUkrainianAndPolishTexts() {
        val release = assertNotNull(releaseFromMap("3.3", doc()))

        assertEquals("3.3", release.version)
        assertEquals("2026-10-01", release.date)
        val change = release.changes.single()
        assertEquals("drafts", change.id)
        assertEquals(mapOf("en" to "Drafts", "uk" to "Чернетки", "pl" to "Szkice"), change.title)
        assertEquals(mapOf("en" to "Keep unfinished ads.", "uk" to "Зберігайте чернетки.", "pl" to "Zachowaj szkice."), change.summary)
    }

    @Test
    fun inactiveReleasesAreDroppedAndAMissingFlagCountsAsActive() {
        assertNull(releaseFromMap("3.3", doc(active = false)))
        assertNotNull(releaseFromMap("3.3", doc(active = null)))
    }

    @Test
    fun theFakeReturnsWhatItHolds() = runTest {
        val release = assertNotNull(releaseFromMap("3.3", doc()))
        val source: ReleaseNotesSource = FakeReleaseNotesSource(listOf(release))

        assertEquals(listOf("3.3"), source.activeReleases().map { it.version })
    }
}
