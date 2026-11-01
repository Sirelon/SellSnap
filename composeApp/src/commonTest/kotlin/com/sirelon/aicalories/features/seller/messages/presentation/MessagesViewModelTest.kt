package com.sirelon.sellsnap.features.seller.messages.presentation

import com.sirelon.sellsnap.analytics.AnalyticsEvents
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountState
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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** The Messages tab's list: the account it belongs to, the advert join, the buyer-side filter, polling. */
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
