package com.sirelon.sellsnap.features.seller.drafts.presentation

import com.sirelon.sellsnap.analytics.Analytics
import com.sirelon.sellsnap.analytics.AnalyticsEvents
import com.sirelon.sellsnap.features.auth.data.InMemoryOlxKeyValueStore
import com.sirelon.sellsnap.features.common.presentation.awaitEffect
import com.sirelon.sellsnap.features.seller.ad.AdFlowTimerStore
import com.sirelon.sellsnap.features.seller.ad.Advertisement
import com.sirelon.sellsnap.features.seller.ad.AdvertisementWithAttributes
import com.sirelon.sellsnap.features.seller.auth.data.OlxCountryStore
import com.sirelon.sellsnap.features.seller.auth.domain.OlxCountry
import com.sirelon.sellsnap.features.seller.drafts.Draft
import com.sirelon.sellsnap.features.seller.drafts.InMemoryDraftsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * SIR-133: the Drafts screen lists the current country's drafts, removes one at once, and puts the
 * last removed one back on undo. Everything runs on one [StandardTestDispatcher]; effects are
 * awaited with `awaitEffect` and state with `state.first { }`.
 */
class DraftsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `removing a draft deletes it, and undo puts it back where it was`() = runTest(testDispatcher) {
        val harness = buildHarness()
        val older = draft(id = "older", title = "Older listing", countryCode = "ua", updatedAt = 1_000L)
        val newer = draft(id = "newer", title = "Newer listing", countryCode = "ua", updatedAt = 2_000L)
        harness.repository.upsert(older)
        harness.repository.upsert(newer)
        harness.viewModel.state.first { it.drafts?.size == 2 }

        harness.viewModel.onEvent(DraftsContract.DraftsEvent.Remove(newer))
        harness.viewModel.effects.awaitEffect<DraftsContract.DraftsEffect.DraftRemoved>()

        assertEquals(listOf(older), harness.viewModel.state.first { it.drafts?.size == 1 }.drafts)
        assertEquals(1, harness.analytics.events.count { it.first == AnalyticsEvents.DRAFT_REMOVED })

        harness.viewModel.onEvent(DraftsContract.DraftsEvent.UndoRemove)

        assertEquals(listOf(newer, older), harness.viewModel.state.first { it.drafts?.size == 2 }.drafts)
    }

    @Test
    fun `opening a draft posts OpenPreview for its listing and logs draft_opened from the drafts screen`() =
        runTest(testDispatcher) {
            val harness = buildHarness()
            val saved = draft(id = "saved", title = "Nike Air Max 90", countryCode = "ua", updatedAt = 1_000L)
            harness.repository.upsert(saved)
            harness.viewModel.state.first { !it.drafts.isNullOrEmpty() }

            harness.viewModel.onEvent(DraftsContract.DraftsEvent.Open(saved))

            val effect = harness.viewModel.effects.awaitEffect<DraftsContract.DraftsEffect.OpenPreview>()
            assertEquals(saved.listing, effect.listing)
            val opened = harness.analytics.events.single { it.first == AnalyticsEvents.DRAFT_OPENED }
            assertEquals("drafts", opened.second["source"])
        }

    @Test
    fun `only the current country's drafts are shown, and the list follows a country change`() =
        runTest(testDispatcher) {
            val harness = buildHarness()
            harness.repository.upsert(draft(id = "ua-draft", title = "Ukrainian listing", countryCode = "ua", updatedAt = 1_000L))
            harness.repository.upsert(draft(id = "pl-draft", title = "Polish listing", countryCode = "pl", updatedAt = 2_000L))

            try {
                val inUkraine = harness.viewModel.state.first { !it.drafts.isNullOrEmpty() }
                assertEquals(listOf("ua-draft"), inUkraine.drafts?.map { it.id })

                harness.countryStore.save(OlxCountry.PL)

                val inPoland = harness.viewModel.state.first { state -> state.drafts?.map { it.id } == listOf("pl-draft") }
                assertEquals("PLN", inPoland.currency.code)
            } finally {
                // save() writes the process-global current country, which other tests read.
                harness.countryStore.save(OlxCountry.UA)
            }
        }

    // --- test harness -----------------------------------------------------------------------

    private fun draft(id: String, title: String, countryCode: String, updatedAt: Long) = Draft(
        id = id,
        countryCode = countryCode,
        createdAtEpochSeconds = updatedAt,
        updatedAtEpochSeconds = updatedAt,
        listing = AdvertisementWithAttributes(
            advertisement = Advertisement(
                title = title,
                description = "Description of $title",
                images = listOf("https://x/$id.jpg"),
                suggestedPrice = 100f,
                minPrice = 90f,
                maxPrice = 110f,
            ),
            filledAttributes = emptyMap(),
            generationSessionId = id,
        ),
    )

    private suspend fun buildHarness(): Harness {
        val analytics = FakeAnalytics()
        val countryStore = OlxCountryStore(InMemoryOlxKeyValueStore(), analytics).apply { save(OlxCountry.UA) }
        val repository = InMemoryDraftsRepository()
        val viewModel = DraftsViewModel(
            draftsRepository = repository,
            countryStore = countryStore,
            adFlowTimerStore = AdFlowTimerStore(),
            analytics = analytics,
        )
        return Harness(viewModel, repository, countryStore, analytics)
    }

    private data class Harness(
        val viewModel: DraftsViewModel,
        val repository: InMemoryDraftsRepository,
        val countryStore: OlxCountryStore,
        val analytics: FakeAnalytics,
    )

    private class FakeAnalytics : Analytics {
        val events = mutableListOf<Pair<String, Map<String, Any>>>()
        override fun logEvent(name: String, params: Map<String, Any>) {
            events += name to params
        }
        override fun setUserId(userId: String?) {}
        override fun setUserProperty(name: String, value: String?) {}
        override fun recordException(throwable: Throwable, message: String?) {}
        override fun log(message: String) {}
        override fun setAnalyticsCollectionEnabled(enabled: Boolean) {}
        override fun setCrashlyticsCollectionEnabled(enabled: Boolean) {}
    }
}
