package com.sirelon.sellsnap.features.seller.messages.presentation

import com.sirelon.sellsnap.analytics.AnalyticsEvents
import com.sirelon.sellsnap.features.common.presentation.awaitEffect
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountState
import com.sirelon.sellsnap.features.seller.messages.presentation.MessagesContract.Effect
import com.sirelon.sellsnap.features.seller.messages.presentation.MessagesContract.Event
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** The Messages tab's list: the account it belongs to, the advert join, the buyer-side filter, polling, paging. */
class MessagesViewModelTest {

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
    fun `without a connected account the tab asks for a connection and loads nothing`() = runTest(testDispatcher) {
        val requests = mutableListOf<String>()
        val engine = mockEngine(testDispatcher) {
            addHandler { request -> requests += request.url.encodedPath; respond("") }
        }
        val (viewModel, _) = viewModel(engine, accounts = emptyList(), activeIndex = null)

        viewModel.onEvent(Event.Shown)
        runCurrent()

        assertTrue(viewModel.state.value.requiresOlxConnection)
        assertEquals(emptyList(), requests)
        viewModel.onEvent(Event.Hidden)
    }

    @Test
    fun `rows join their advert by id, fall back without one, and drop threads where the account is the buyer`() =
        runTest(testDispatcher) {
            val requests = mutableListOf<String>()
            val engine = mockEngine(testDispatcher) {
                addHandler { request ->
                    val path = request.url.encodedPath
                    requests += path
                    when {
                        path.endsWith("/threads") -> respond(
                            threadsJson(
                                threadJson(id = 1, advertId = 10, unread = 2),
                                threadJson(id = 2, advertId = 11, interlocutorId = OwnUserId, unread = 5),
                                threadJson(id = 3, advertId = 12),
                            ),
                            HttpStatusCode.OK,
                            jsonHeaders(),
                        )
                        else -> respond(advertsJson(10), HttpStatusCode.OK, jsonHeaders())
                    }
                }
            }
            val (viewModel, harness) = viewModel(engine, listOf(testAccount(1)), activeIndex = 1)

            viewModel.onEvent(Event.Shown)
            runCurrent()

            val rows = viewModel.state.value.threads
            assertEquals(listOf(1L, 3L), rows.map { it.threadId })
            assertEquals("Placeholder advert 10", rows.first().title)
            assertEquals("https://example.invalid/10.jpg", rows.first().imageUrl)
            assertEquals(2, rows.first().unreadCount)
            assertNull(rows.last().title)
            assertNull(rows.last().imageUrl)
            // One threads call and one adverts call, however many rows there are.
            assertEquals(1, requests.count { it.endsWith("/threads") })
            assertEquals(1, requests.count { it.endsWith("/adverts") })
            // The buyer-side thread's unread messages are not counted for the badge.
            assertEquals(2, harness.messagesRepository.unreadCount.value)
            viewModel.onEvent(Event.Hidden)
        }

    @Test
    fun `an account without a known user id keeps every thread`() = runTest(testDispatcher) {
        val engine = mockEngine(testDispatcher) {
            addHandler { request ->
                if (request.url.encodedPath.endsWith("/threads")) {
                    respond(
                        threadsJson(threadJson(1, 10), threadJson(2, 11, interlocutorId = OwnUserId)),
                        HttpStatusCode.OK,
                        jsonHeaders(),
                    )
                } else {
                    respond(advertsJson(), HttpStatusCode.OK, jsonHeaders())
                }
            }
        }
        val account = testAccount(1).let { it.copy(olxUserId = null) }
        val (viewModel, _) = viewModel(engine, listOf(account), activeIndex = 1)

        viewModel.onEvent(Event.Shown)
        runCurrent()

        assertEquals(listOf(1L, 2L), viewModel.state.value.threads.map { it.threadId })
        viewModel.onEvent(Event.Hidden)
    }

    @Test
    fun `a failing adverts call does not fail the list`() = runTest(testDispatcher) {
        val engine = mockEngine(testDispatcher) {
            addHandler { request ->
                if (request.url.encodedPath.endsWith("/threads")) {
                    respond(threadsJson(threadJson(1, 10)), HttpStatusCode.OK, jsonHeaders())
                } else {
                    respond("", HttpStatusCode.InternalServerError)
                }
            }
        }
        val (viewModel, _) = viewModel(engine, listOf(testAccount(1)), activeIndex = 1)

        viewModel.onEvent(Event.Shown)
        runCurrent()

        val state = viewModel.state.value
        assertEquals(listOf(1L), state.threads.map { it.threadId })
        assertNull(state.threads.single().title)
        assertNull(state.errorMessage)
        viewModel.onEvent(Event.Hidden)
    }

    @Test
    fun `a failing threads call shows an error and reports it`() = runTest(testDispatcher) {
        val engine = mockEngine(testDispatcher) {
            addHandler { respond("", HttpStatusCode.InternalServerError) }
        }
        val (viewModel, harness) = viewModel(engine, listOf(testAccount(1)), activeIndex = 1)

        viewModel.onEvent(Event.Shown)
        runCurrent()

        // The message comes from compose-resources getString, which resolves off the test scheduler.
        val state = viewModel.state.first { it.errorMessage != null }
        assertTrue(state.threads.isEmpty())
        val reported = harness.analytics.events.filter { it.first == AnalyticsEvents.ERROR_SHOWN }
        assertEquals("messages", reported.single().second["screen"])
        viewModel.onEvent(Event.Hidden)
    }

    @Test
    fun `an account that needs reconnecting shows the reconnect state and loads nothing`() = runTest(testDispatcher) {
        val requests = mutableListOf<String>()
        val engine = mockEngine(testDispatcher) {
            addHandler { request -> requests += request.url.encodedPath; respond("") }
        }
        val account = testAccount(1, state = OlxAccountState.NeedsReconnect)
        val (viewModel, _) = viewModel(engine, listOf(account), activeIndex = 1)

        viewModel.onEvent(Event.Shown)
        runCurrent()

        assertTrue(viewModel.state.value.needsReconnect)
        assertEquals(emptyList(), requests)
        viewModel.onEvent(Event.Hidden)
    }

    @Test
    fun `while shown it re-reads every poll interval and stops once hidden`() = runTest(testDispatcher) {
        var threadCalls = 0
        val engine = mockEngine(testDispatcher) {
            addHandler { request ->
                if (request.url.encodedPath.endsWith("/threads")) {
                    threadCalls++
                    respond(threadsJson(threadJson(1, 10)), HttpStatusCode.OK, jsonHeaders())
                } else {
                    respond(advertsJson(10), HttpStatusCode.OK, jsonHeaders())
                }
            }
        }
        val (viewModel, _) = viewModel(engine, listOf(testAccount(1)), activeIndex = 1)

        viewModel.onEvent(Event.Shown)
        runCurrent()
        assertEquals(1, threadCalls)

        advanceTimeBy(PollInterval + 1.seconds)
        runCurrent()
        assertEquals(2, threadCalls)

        viewModel.onEvent(Event.Hidden)
        advanceTimeBy(PollInterval * 3)
        runCurrent()
        assertEquals(2, threadCalls)
    }

    @Test
    fun `switching the active account drops the old account's list and loads the new one`() = runTest(testDispatcher) {
        val firstAccountGate = CompletableDeferred<Unit>()
        val engine = mockEngine(testDispatcher) {
            addHandler { request ->
                val isFirstAccount = request.headers["Authorization"] == "Bearer token-1"
                when {
                    request.url.encodedPath.endsWith("/threads") && isFirstAccount -> {
                        firstAccountGate.await()
                        respond(threadsJson(threadJson(1, 10)), HttpStatusCode.OK, jsonHeaders())
                    }
                    request.url.encodedPath.endsWith("/threads") ->
                        respond(threadsJson(threadJson(7, 70)), HttpStatusCode.OK, jsonHeaders())
                    else -> respond(advertsJson(), HttpStatusCode.OK, jsonHeaders())
                }
            }
        }
        val (viewModel, harness) = viewModel(
            engine,
            listOf(testAccount(1), testAccount(2, olxUserId = 2)),
            activeIndex = 1,
        )

        viewModel.onEvent(Event.Shown)
        runCurrent()
        harness.accountRepository.setActiveAccount("ua", 2)
        runCurrent()
        firstAccountGate.complete(Unit)
        runCurrent()

        val state = viewModel.state.value
        assertEquals(2, state.localIndex)
        assertEquals(listOf(7L), state.threads.map { it.threadId })
        viewModel.onEvent(Event.Hidden)
    }

    @Test
    fun `a full page offers more, the next page is appended and the buyer-side filter applies to it too`() =
        runTest(testDispatcher) {
            val offsets = mutableListOf<String?>()
            val engine = pagedEngine(offsets) { offset ->
                when (offset) {
                    "0" -> page(1..50, buyerSide = setOf(3))
                    else -> page(51..60, buyerSide = setOf(55))
                }
            }
            val (viewModel, harness) = viewModel(engine, listOf(testAccount(1)), activeIndex = 1)

            viewModel.onEvent(Event.Shown)
            runCurrent()
            // 50 came back, 49 are shown: the buyer-side one does not count against "full".
            assertEquals(49, viewModel.state.value.threads.size)
            assertTrue(viewModel.state.value.canLoadMore)

            viewModel.onEvent(Event.LoadMoreClicked)
            runCurrent()

            val state = viewModel.state.value
            assertEquals((1L..60L).filter { it != 3L && it != 55L }, state.threads.map { it.threadId })
            assertFalse(state.canLoadMore)
            assertFalse(state.isLoadingMore)
            assertEquals(listOf<String?>("0", "50"), offsets)
            // One unread each; the buyer-side threads' five are left out.
            assertEquals(58, harness.messagesRepository.unreadCount.value)
            viewModel.onEvent(Event.Hidden)
        }

    @Test
    fun `a short first page offers no more, and a page of only buyer-side threads still does`() =
        runTest(testDispatcher) {
            val offsets = mutableListOf<String?>()
            val engine = pagedEngine(offsets) { offset ->
                if (offset == "0") page(1..3) else page(1..0)
            }
            val (short, _) = viewModel(engine, listOf(testAccount(1)), activeIndex = 1)

            short.onEvent(Event.Shown)
            runCurrent()
            assertEquals(3, short.state.value.threads.size)
            assertFalse(short.state.value.canLoadMore)
            short.onEvent(Event.LoadMoreClicked)
            runCurrent()
            assertEquals(listOf<String?>("0"), offsets)
            short.onEvent(Event.Hidden)

            val buyerOnlyEngine = pagedEngine(mutableListOf()) { page(1..50, buyerSide = (1..50).toSet()) }
            val (buyerOnly, _) = viewModel(buyerOnlyEngine, listOf(testAccount(1)), activeIndex = 1)
            buyerOnly.onEvent(Event.Shown)
            runCurrent()
            assertTrue(buyerOnly.state.value.threads.isEmpty())
            assertTrue(buyerOnly.state.value.canLoadMore)
            buyerOnly.onEvent(Event.Hidden)
        }

    @Test
    fun `a poll refreshes the first page and keeps the pages already loaded`() = runTest(testDispatcher) {
        val offsets = mutableListOf<String?>()
        var firstPageCalls = 0
        val engine = pagedEngine(offsets) { offset ->
            when {
                offset != "0" -> page(51..60)
                ++firstPageCalls == 1 -> page(1..50)
                // A new conversation arrived: it leads, and thread 50 is pushed off the page.
                else -> page(listOf(100L) + (1L..49L))
            }
        }
        val (viewModel, _) = viewModel(engine, listOf(testAccount(1)), activeIndex = 1)
        viewModel.onEvent(Event.Shown)
        runCurrent()
        viewModel.onEvent(Event.LoadMoreClicked)
        runCurrent()
        assertEquals(60, viewModel.state.value.threads.size)

        advanceTimeBy(PollInterval + 1.seconds)
        runCurrent()

        val state = viewModel.state.value
        // First page in OLX's new order, then what was loaded before and is not on it: 50, 51..60.
        assertEquals(listOf(100L) + (1L..60L).toList(), state.threads.map { it.threadId })
        assertFalse(state.canLoadMore, "the last page fetched was short, and the poll must not reset that")
        assertEquals(listOf<String?>("0", "50", "0"), offsets)
        viewModel.onEvent(Event.Hidden)
    }

    @Test
    fun `a poll before any load more keeps offering it`() = runTest(testDispatcher) {
        val engine = pagedEngine(mutableListOf()) { page(1..50) }
        val (viewModel, _) = viewModel(engine, listOf(testAccount(1)), activeIndex = 1)
        viewModel.onEvent(Event.Shown)
        runCurrent()

        advanceTimeBy(PollInterval + 1.seconds)
        runCurrent()

        assertEquals(50, viewModel.state.value.threads.size)
        assertTrue(viewModel.state.value.canLoadMore)
        viewModel.onEvent(Event.Hidden)
    }

    @Test
    fun `a page that arrives after an account switch is dropped`() = runTest(testDispatcher) {
        val secondPageGate = CompletableDeferred<Unit>()
        val engine = mockEngine(testDispatcher) {
            addHandler { request ->
                val isFirstAccount = request.headers["Authorization"] == "Bearer token-1"
                val offset = request.url.parameters["offset"]
                when {
                    !request.url.encodedPath.endsWith("/threads") -> respond(advertsJson(), HttpStatusCode.OK, jsonHeaders())
                    isFirstAccount && offset == "0" -> respond(page(1..50), HttpStatusCode.OK, jsonHeaders())
                    isFirstAccount -> {
                        secondPageGate.await()
                        respond(page(51..60), HttpStatusCode.OK, jsonHeaders())
                    }
                    else -> respond(page(listOf(700L)), HttpStatusCode.OK, jsonHeaders())
                }
            }
        }
        val (viewModel, harness) = viewModel(
            engine,
            listOf(testAccount(1), testAccount(2, olxUserId = 2)),
            activeIndex = 1,
        )
        viewModel.onEvent(Event.Shown)
        runCurrent()
        viewModel.onEvent(Event.LoadMoreClicked)
        runCurrent()
        assertTrue(viewModel.state.value.isLoadingMore)

        harness.accountRepository.setActiveAccount("ua", 2)
        runCurrent()
        secondPageGate.complete(Unit)
        runCurrent()

        val state = viewModel.state.value
        assertEquals(2, state.localIndex)
        assertEquals(listOf(700L), state.threads.map { it.threadId })
        assertFalse(state.canLoadMore)
        assertFalse(state.isLoadingMore)
        assertEquals(1, harness.messagesRepository.unreadCount.value)
        viewModel.onEvent(Event.Hidden)
    }

    @Test
    fun `a failing load more leaves the list and the button alone`() = runTest(testDispatcher) {
        val engine = mockEngine(testDispatcher) {
            addHandler { request ->
                when {
                    !request.url.encodedPath.endsWith("/threads") -> respond(advertsJson(), HttpStatusCode.OK, jsonHeaders())
                    request.url.parameters["offset"] == "0" -> respond(page(1..50), HttpStatusCode.OK, jsonHeaders())
                    else -> respond("", HttpStatusCode.InternalServerError)
                }
            }
        }
        val (viewModel, harness) = viewModel(engine, listOf(testAccount(1)), activeIndex = 1)
        viewModel.onEvent(Event.Shown)
        runCurrent()

        viewModel.onEvent(Event.LoadMoreClicked)
        runCurrent()

        val state = viewModel.state.value
        assertEquals(50, state.threads.size)
        assertTrue(state.canLoadMore)
        assertFalse(state.isLoadingMore)
        assertNull(state.errorMessage)
        assertEquals(emptyList(), harness.analytics.events.filter { it.first == AnalyticsEvents.ERROR_SHOWN })
        viewModel.onEvent(Event.Hidden)
    }

    @Test
    fun `opening a row passes its title and image on to the conversation`() = runTest(testDispatcher) {
        val engine = mockEngine(testDispatcher) {
            addHandler { request ->
                if (request.url.encodedPath.endsWith("/threads")) {
                    respond(threadsJson(threadJson(1, 10), threadJson(2, 11)), HttpStatusCode.OK, jsonHeaders())
                } else {
                    respond(advertsJson(10), HttpStatusCode.OK, jsonHeaders())
                }
            }
        }
        val (viewModel, _) = viewModel(engine, listOf(testAccount(1)), activeIndex = 1)
        viewModel.onEvent(Event.Shown)
        runCurrent()
        val (withAdvert, withoutAdvert) = viewModel.state.value.threads

        viewModel.onEvent(Event.ThreadClicked(withAdvert))
        viewModel.onEvent(Event.ThreadClicked(withoutAdvert))

        assertEquals(
            Effect.OpenThread(1, 10, "Placeholder advert 10", "https://example.invalid/10.jpg"),
            viewModel.effects.awaitEffect<Effect.OpenThread>(),
        )
        assertEquals(Effect.OpenThread(2, 11, null, null), viewModel.effects.awaitEffect<Effect.OpenThread>())
        viewModel.onEvent(Event.Hidden)
    }

    /** `GET threads` answers with [threads] for each `offset`; anything else is an empty adverts list. */
    private fun pagedEngine(offsets: MutableList<String?>, threads: (offset: String?) -> String) =
        mockEngine(testDispatcher) {
            addHandler { request ->
                if (request.url.encodedPath.endsWith("/threads")) {
                    offsets += request.url.parameters["offset"]
                    assertEquals("50", request.url.parameters["limit"])
                    respond(threads(request.url.parameters["offset"]), HttpStatusCode.OK, jsonHeaders())
                } else {
                    respond(advertsJson(10), HttpStatusCode.OK, jsonHeaders())
                }
            }
        }

    /** A page of threads; ids in [buyerSide] are threads where the account is the buyer. */
    private fun page(ids: Iterable<Number>, buyerSide: Set<Int> = emptySet()) = threadsJson(
        *ids.map { id ->
            val isBuyer = id.toInt() in buyerSide
            threadJson(
                id = id.toLong(),
                advertId = 10,
                interlocutorId = if (isBuyer) OwnUserId else BuyerUserId,
                unread = if (isBuyer) 5 else 1,
            )
        }.toTypedArray(),
    )

    private suspend fun viewModel(
        engine: MockEngine,
        accounts: List<com.sirelon.sellsnap.features.seller.auth.data.OlxAccountRecord>,
        activeIndex: Int?,
    ): Pair<MessagesViewModel, MessagesHarness> {
        val harness = messagesHarness(engine, accounts, activeIndex)
        val viewModel = MessagesViewModel(
            repository = harness.messagesRepository,
            myAdvertsRepository = harness.myAdvertsRepository,
            accountRepository = harness.accountRepository,
            analytics = harness.analytics,
        )
        return viewModel to harness
    }
}
