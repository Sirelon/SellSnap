package com.sirelon.sellsnap.features.seller.my_ads.presentation

import com.sirelon.sellsnap.analytics.AnalyticsEvents
import com.sirelon.sellsnap.features.auth.data.InMemoryOlxKeyValueStore
import com.sirelon.sellsnap.features.seller.messages.presentation.FakeAnalytics
import com.sirelon.sellsnap.features.seller.messages.presentation.advertsJson
import com.sirelon.sellsnap.features.seller.messages.presentation.jsonHeaders
import com.sirelon.sellsnap.features.seller.messages.presentation.messagesHarness
import com.sirelon.sellsnap.features.seller.messages.presentation.mockEngine
import com.sirelon.sellsnap.features.seller.messages.presentation.testAccount
import com.sirelon.sellsnap.features.seller.messages.presentation.threadJson
import com.sirelon.sellsnap.features.seller.messages.presentation.threadsJson
import com.sirelon.sellsnap.features.seller.my_ads.data.AdvertLifecycleRepository
import com.sirelon.sellsnap.features.seller.my_ads.data.AdvertOutcomeStore
import com.sirelon.sellsnap.features.seller.my_ads.presentation.MyAdvertsContract.Effect
import com.sirelon.sellsnap.features.seller.my_ads.presentation.MyAdvertsContract.Event
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class MyAdvertsUnreadTest {

    private val testDispatcher = StandardTestDispatcher()
    private var analytics = FakeAnalytics()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `threads of one advert are summed and threads of adverts off the page are ignored`() = runTest(testDispatcher) {
        val engine = mockEngine(testDispatcher) {
            addHandler { request ->
                if (request.url.encodedPath.contains("threads")) {
                    respond(
                        threadsJson(
                            threadJson(1, 10, unread = 2),
                            threadJson(2, 10, unread = 3),
                            threadJson(3, 11, unread = 0),
                            threadJson(4, 99, unread = 6),
                        ),
                        HttpStatusCode.OK,
                        jsonHeaders(),
                    )
                } else {
                    respond(advertsJson(10, 11, 12), HttpStatusCode.OK, jsonHeaders())
                }
            }
        }

        val viewModel = newViewModel(engine, accounts = listOf(1))
        runCurrent()

        val page = viewModel.state.value.pages.single()
        assertEquals(listOf(10L, 11L, 12L), page.adverts.map { it.id })
        // Adverts 11 and 12 show nothing; advert 99 is not on the page, so no row looks it up.
        assertEquals(5, page.unreadByAdvert[10])
        assertEquals(null, page.unreadByAdvert[11])
        assertEquals(null, page.unreadByAdvert[12])
    }

    @Test
    fun `each account keeps its own counts`() = runTest(testDispatcher) {
        val engine = mockEngine(testDispatcher) {
            addHandler { request ->
                val token = request.headers["Authorization"]
                if (request.url.encodedPath.contains("threads")) {
                    val unread = if (token == "Bearer token-1") 2 else 7
                    respond(threadsJson(threadJson(1, 10, unread = unread)), HttpStatusCode.OK, jsonHeaders())
                } else {
                    respond(advertsJson(10), HttpStatusCode.OK, jsonHeaders())
                }
            }
        }

        val viewModel = newViewModel(engine, accounts = listOf(1, 2))
        runCurrent()
        viewModel.onEvent(Event.PageSelected(2))
        runCurrent()

        val pages = viewModel.state.value.pages.associateBy { it.localIndex }
        assertEquals(mapOf(10L to 2), pages.getValue(1).unreadByAdvert)
        assertEquals(mapOf(10L to 7), pages.getValue(2).unreadByAdvert)
    }

    @Test
    fun `a threads failure leaves the list intact with no marker and no error`() = runTest(testDispatcher) {
        val engine = mockEngine(testDispatcher) {
            addHandler { request ->
                if (request.url.encodedPath.contains("threads")) {
                    respond("", HttpStatusCode.InternalServerError)
                } else {
                    respond(advertsJson(10), HttpStatusCode.OK, jsonHeaders())
                }
            }
        }

        val viewModel = newViewModel(engine, accounts = listOf(1))
        runCurrent()

        val page = viewModel.state.value.pages.single()
        assertEquals(listOf(10L), page.adverts.map { it.id })
        assertTrue(page.unreadByAdvert.isEmpty())
        assertEquals(null, page.errorMessage)
        assertTrue(analytics.events.isEmpty())
    }

    @Test
    fun `a refresh re-reads the threads so a thread marked read loses its marker`() = runTest(testDispatcher) {
        var unread = 4
        val engine = mockEngine(testDispatcher) {
            addHandler { request ->
                if (request.url.encodedPath.contains("threads")) {
                    respond(threadsJson(threadJson(1, 10, unread = unread)), HttpStatusCode.OK, jsonHeaders())
                } else {
                    respond(advertsJson(10), HttpStatusCode.OK, jsonHeaders())
                }
            }
        }
        val viewModel = newViewModel(engine, accounts = listOf(1))
        runCurrent()
        assertEquals(mapOf(10L to 4), viewModel.state.value.pages.single().unreadByAdvert)

        unread = 0
        viewModel.onEvent(Event.RefreshClicked(1))
        runCurrent()

        assertTrue(viewModel.state.value.pages.single().unreadByAdvert.isEmpty())
    }

    @Test
    fun `a slow older threads answer cannot overwrite a newer one`() = runTest(testDispatcher) {
        var threadCalls = 0
        val engine = mockEngine(testDispatcher) {
            addHandler { request ->
                if (request.url.encodedPath.contains("threads")) {
                    // The first read is slow and still shows 4 unread; the second shows none.
                    val isFirst = ++threadCalls == 1
                    if (isFirst) delay(5.seconds)
                    respond(threadsJson(threadJson(1, 10, unread = if (isFirst) 4 else 0)), HttpStatusCode.OK, jsonHeaders())
                } else {
                    respond(advertsJson(10), HttpStatusCode.OK, jsonHeaders())
                }
            }
        }
        val viewModel = newViewModel(engine, accounts = listOf(1))
        runCurrent()

        viewModel.onEvent(Event.RefreshClicked(1))
        runCurrent()
        assertTrue(viewModel.state.value.pages.single().unreadByAdvert.isEmpty())

        advanceTimeBy(6.seconds)
        runCurrent()

        assertEquals(2, threadCalls)
        assertTrue(viewModel.state.value.pages.single().unreadByAdvert.isEmpty())
    }

    @Test
    fun `tapping the marker logs a bucket, no ids, and opens Messages`() = runTest(testDispatcher) {
        val engine = mockEngine(testDispatcher) {
            addHandler { respond(advertsJson(10), HttpStatusCode.OK, jsonHeaders()) }
        }
        val viewModel = newViewModel(engine, accounts = listOf(1))
        runCurrent()

        viewModel.onEvent(Event.UnreadMarkerClicked(unreadCount = 4))
        runCurrent()

        assertEquals(Effect.OpenMessages, viewModel.effects.first())
        assertEquals(
            listOf(AnalyticsEvents.MY_ADS_UNREAD_MARKER_TAPPED to mapOf<String, Any>("unread_bucket" to "2_5")),
            analytics.events,
        )
    }

    private suspend fun newViewModel(engine: MockEngine, accounts: List<Int>): MyAdvertsViewModel {
        val harness = messagesHarness(
            engine,
            accounts.map { testAccount(it, olxUserId = it.toLong()) },
            activeIndex = accounts.first(),
        )
        analytics = harness.analytics
        return MyAdvertsViewModel(
            repository = harness.myAdvertsRepository,
            accountRepository = harness.accountRepository,
            messagesRepository = harness.messagesRepository,
            lifecycleRepository = AdvertLifecycleRepository(
                accountRepository = harness.accountRepository,
                unauthenticatedOlxApiClient = harness.unauthenticatedOlxApiClient,
                myAdvertsRepository = harness.myAdvertsRepository,
            ),
            outcomeStore = AdvertOutcomeStore(
                InMemoryOlxKeyValueStore(),
                Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false },
            ),
            analytics = harness.analytics,
        )
    }
}
