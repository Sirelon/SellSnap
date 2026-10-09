package com.sirelon.sellsnap.features.announcements.data

import com.sirelon.sellsnap.features.auth.data.InMemoryOlxKeyValueStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnnouncementsStoreTest {

    @Test
    fun `nothing is seen until it is marked`() = runTest {
        val store = AnnouncementsStore(InMemoryOlxKeyValueStore())

        assertFalse(store.isSeen("a"))
    }

    @Test
    fun `marking one id leaves the others unseen`() = runTest {
        val store = AnnouncementsStore(InMemoryOlxKeyValueStore())

        store.markSeen("a")

        assertTrue(store.isSeen("a"))
        assertFalse(store.isSeen("b"))
    }

    @Test
    fun `seen state survives a new store over the same storage`() = runTest {
        val storage = InMemoryOlxKeyValueStore()
        AnnouncementsStore(storage).markSeen("a")

        assertTrue(AnnouncementsStore(storage).isSeen("a"))
        assertEquals("1", storage.getString("seen_a"))
    }
}
