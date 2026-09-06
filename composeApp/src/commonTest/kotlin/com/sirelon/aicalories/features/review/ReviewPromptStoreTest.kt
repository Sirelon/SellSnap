package com.sirelon.sellsnap.features.review

import com.sirelon.sellsnap.features.auth.data.InMemoryOlxKeyValueStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReviewPromptStoreTest {

    @Test
    fun `publish count starts at zero and increments`() = runTest {
        val store = ReviewPromptStore(InMemoryOlxKeyValueStore())
        assertEquals(0, store.publishCount())
        store.incrementPublishCount()
        store.incrementPublishCount()
        assertEquals(2, store.publishCount())
    }

    @Test
    fun `prompt timestamp round-trips`() = runTest {
        val store = ReviewPromptStore(InMemoryOlxKeyValueStore())
        assertNull(store.lastPromptEpochSeconds())
        store.markPromptRequested(1_800_000_000L)
        assertEquals(1_800_000_000L, store.lastPromptEpochSeconds())
    }

    @Test
    fun `a value that is not a number reads as absent rather than crashing`() = runTest {
        val storage = InMemoryOlxKeyValueStore()
        storage.putString("successful_publish_count", "not a number")
        storage.putString("last_prompt_epoch_seconds", "")
        val store = ReviewPromptStore(storage)
        assertEquals(0, store.publishCount())
        assertNull(store.lastPromptEpochSeconds())
    }
}
