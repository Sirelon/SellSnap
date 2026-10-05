package com.sirelon.sellsnap.startup

import com.sirelon.sellsnap.features.auth.data.InMemoryOlxKeyValueStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppStartupStoreTest {

    @Test
    fun `first launch is not returning and the second is`() = runTest {
        val storage = InMemoryOlxKeyValueStore()

        assertFalse(AppStartupStore(storage).recordLaunch())
        assertTrue(AppStartupStore(storage).recordLaunch())
    }

    @Test
    fun `an install that only has the onboarding marker is returning on its first resolution`() = runTest {
        val storage = InMemoryOlxKeyValueStore()
        storage.putString("has_seen_onboarding", "true")

        assertTrue(AppStartupStore(storage).recordLaunch())
    }

    @Test
    fun `an interrupted onboarding is still unseen after a restart`() = runTest {
        val storage = InMemoryOlxKeyValueStore()
        AppStartupStore(storage).recordLaunch()

        val afterRestart = AppStartupStore(storage)

        assertFalse(afterRestart.hasSeenOnboarding())
        assertTrue(afterRestart.recordLaunch())
    }

    @Test
    fun `a completed onboarding is not shown again after a restart`() = runTest {
        val storage = InMemoryOlxKeyValueStore()
        val firstRun = AppStartupStore(storage)
        firstRun.recordLaunch()
        firstRun.markOnboardingSeen()

        assertTrue(AppStartupStore(storage).hasSeenOnboarding())
    }
}
