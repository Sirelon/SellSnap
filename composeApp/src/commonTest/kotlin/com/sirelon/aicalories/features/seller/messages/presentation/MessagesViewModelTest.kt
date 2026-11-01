package com.sirelon.sellsnap.features.seller.messages.presentation

import com.sirelon.sellsnap.analytics.AnalyticsEvents
import com.sirelon.sellsnap.features.common.presentation.awaitEffect
import com.sirelon.sellsnap.features.seller.ad.publish_success.AdvertStatus
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
                        path.endsWith("/adverts/12") ->
                            respond(AdvertNotFoundJson, HttpStatusCode.NotFound, jsonHeaders())
                        else -> respond(advertsJson(10), HttpStatusCode.OK, jsonHeaders())
                    }
                }
            }
            val (viewModel, harness) = viewModel(engine, listOf(testAccount(1)), activeIndex = 1)

            viewModel.onEvent(Event.Shown)
            runCurrent()

            val rows = viewModel.state.value.page.threads
            assertEquals(listOf(1L, 3L), rows.map { it.threadId })
            assertEquals("Placeholder advert 10", rows.first().title)
            assertEquals("https://example.invalid/10.jpg", rows.first().imageUrl)
            assertEquals(2, rows.first().unreadCount)
            assertNull(rows.last().title)
            assertNull(rows.last().imageUrl)
            assertTrue(rows.last().isListingDeleted)
            // One threads call and one adverts page call, however many rows there are, plus one
            // lookup for the advert that is not on the page.
            assertEquals(1, requests.count { it.endsWith("/threads") })
            assertEquals(1, requests.count { it.endsWith("/adverts") })
            assertEquals(1, requests.count { it.endsWith("/adverts/12") })
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

        assertEquals(listOf(1L, 2L), viewModel.state.value.page.threads.map { it.threadId })
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

        val state = viewModel.state.value.page
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
        val state = viewModel.state.first { it.selectedPage?.errorMessage != null }.page
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

        assertTrue(viewModel.state.value.page.needsReconnect)
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

    // --- Several accounts -----------------------------------------------------------------------

    @Test
    fun `each account has its own page, the active one loads first and another loads when selected`() =
        runTest(testDispatcher) {
            val tokens = mutableListOf<String?>()
            val engine = mockEngine(testDispatcher) {
                addHandler { request ->
                    val token = request.headers["Authorization"]
                    when {
                        !request.url.encodedPath.endsWith("/threads") -> respond(advertsJson(), HttpStatusCode.OK, jsonHeaders())
                        token == "Bearer token-1" -> {
                            tokens += token
                            respond(threadsJson(threadJson(1, 10)), HttpStatusCode.OK, jsonHeaders())
                        }
                        else -> {
                            tokens += token
                            respond(threadsJson(threadJson(7, 70, interlocutorId = BuyerUserId)), HttpStatusCode.OK, jsonHeaders())
                        }
                    }
                }
            }
            val (viewModel, harness) = viewModel(
                engine,
                listOf(testAccount(1), testAccount(2, olxUserId = 2)),
                activeIndex = 2,
            )

            viewModel.onEvent(Event.Shown)
            runCurrent()

            val state = viewModel.state.value
            assertEquals(listOf(1, 2), state.pages.map { it.localIndex })
            assertEquals(2, state.selectedLocalIndex)
            assertEquals(listOf(7L), state.page.threads.map { it.threadId })
            assertFalse(state.pages.first().hasLoaded)
            assertEquals(listOf<String?>("Bearer token-2"), tokens)

            // Switching the active account elsewhere only moves the "Active" badge.
            harness.accountRepository.setActiveAccount("ua", 1)
            runCurrent()
            assertEquals(2, viewModel.state.value.selectedLocalIndex)
            assertEquals(listOf(true, false), viewModel.state.value.pages.map { it.isActiveAccount })

            viewModel.onEvent(Event.PageSelected(1))
            runCurrent()
            assertEquals(listOf(1L), viewModel.state.value.page.threads.map { it.threadId })
            assertEquals(listOf(7L), viewModel.state.value.pages.last().threads.map { it.threadId })
            viewModel.onEvent(Event.Hidden)
        }

    @Test
    fun `only the selected page polls, and selecting a loaded page refreshes it silently`() = runTest(testDispatcher) {
        val calls = mutableMapOf<String?, Int>()
        val engine = mockEngine(testDispatcher) {
            addHandler { request ->
                if (request.url.encodedPath.endsWith("/threads")) {
                    val token = request.headers["Authorization"]
                    calls[token] = (calls[token] ?: 0) + 1
                    respond(threadsJson(threadJson(1, 10)), HttpStatusCode.OK, jsonHeaders())
                } else {
                    respond(advertsJson(10), HttpStatusCode.OK, jsonHeaders())
                }
            }
        }
        val (viewModel, _) = viewModel(engine, listOf(testAccount(1), testAccount(2, olxUserId = 2)), activeIndex = 1)

        viewModel.onEvent(Event.Shown)
        runCurrent()
        advanceTimeBy(PollInterval + 1.seconds)
        runCurrent()
        assertEquals(mapOf<String?, Int>("Bearer token-1" to 2), calls)

        viewModel.onEvent(Event.PageSelected(2))
        runCurrent()
        advanceTimeBy(PollInterval + 1.seconds)
        runCurrent()
        assertEquals(mapOf<String?, Int>("Bearer token-1" to 2, "Bearer token-2" to 2), calls)

        viewModel.onEvent(Event.PageSelected(1))
        assertFalse(viewModel.state.value.page.isLoading, "a loaded page refreshes without blanking")
        runCurrent()
        assertEquals(mapOf<String?, Int>("Bearer token-1" to 3, "Bearer token-2" to 2), calls)
        viewModel.onEvent(Event.Hidden)
    }

    @Test
    fun `a page that arrives after its account is disconnected is dropped`() = runTest(testDispatcher) {
        val secondAccountGate = CompletableDeferred<Unit>()
        val engine = mockEngine(testDispatcher) {
            addHandler { request ->
                val isFirstAccount = request.headers["Authorization"] == "Bearer token-1"
                when {
                    !request.url.encodedPath.endsWith("/threads") -> respond(advertsJson(), HttpStatusCode.OK, jsonHeaders())
                    isFirstAccount -> respond(threadsJson(threadJson(1, 10, unread = 1)), HttpStatusCode.OK, jsonHeaders())
                    else -> {
                        secondAccountGate.await()
                        respond(threadsJson(threadJson(7, 70, unread = 4)), HttpStatusCode.OK, jsonHeaders())
                    }
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
        viewModel.onEvent(Event.PageSelected(2))
        runCurrent()
        assertTrue(viewModel.state.value.page.isLoading)

        harness.accountStore.disconnect(2)
        runCurrent()
        secondAccountGate.complete(Unit)
        runCurrent()

        val state = viewModel.state.value
        assertEquals(listOf(1), state.pages.map { it.localIndex })
        assertEquals(1, state.selectedLocalIndex)
        assertEquals(listOf(1L), state.page.threads.map { it.threadId })
        assertEquals(1, harness.messagesRepository.unreadCount.value)
        viewModel.onEvent(Event.Hidden)
    }

    @Test
    fun `advert lookups belong to their account`() = runTest(testDispatcher) {
        val lookupGate = CompletableDeferred<Unit>()
        val engine = mockEngine(testDispatcher) {
            addHandler { request ->
                val path = request.url.encodedPath
                val isFirstAccount = request.headers["Authorization"] == "Bearer token-1"
                when {
                    path.endsWith("/threads") && isFirstAccount ->
                        respond(threadsJson(threadJson(1, 11)), HttpStatusCode.OK, jsonHeaders())
                    path.endsWith("/threads") ->
                        respond(threadsJson(threadJson(7, 11)), HttpStatusCode.OK, jsonHeaders())
                    path.endsWith("/adverts/11") && isFirstAccount -> {
                        lookupGate.await()
                        respond(AdvertNotFoundJson, HttpStatusCode.NotFound, jsonHeaders())
                    }
                    path.endsWith("/adverts/11") ->
                        respond(advertJson(11, status = "removed_by_user"), HttpStatusCode.OK, jsonHeaders())
                    else -> respond(advertsJson(), HttpStatusCode.OK, jsonHeaders())
                }
            }
        }
        val (viewModel, _) = viewModel(
            engine,
            listOf(testAccount(1), testAccount(2, olxUserId = 2)),
            activeIndex = 1,
        )
        viewModel.onEvent(Event.Shown)
        runCurrent()
        viewModel.onEvent(Event.PageSelected(2))
        runCurrent()
        lookupGate.complete(Unit)
        runCurrent()

        // The first account's 404 for advert 11 marks only its own row deleted.
        val (first, second) = viewModel.state.value.pages.map { it.threads.single() }
        assertTrue(first.isListingDeleted)
        assertFalse(second.isListingDeleted)
        assertEquals(AdvertStatus.RemovedByUser, second.status)
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
            assertEquals(49, viewModel.state.value.page.threads.size)
            assertTrue(viewModel.state.value.page.canLoadMore)

            viewModel.onEvent(Event.LoadMoreClicked)
            runCurrent()

            val state = viewModel.state.value.page
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
            assertEquals(3, short.state.value.page.threads.size)
            assertFalse(short.state.value.page.canLoadMore)
            short.onEvent(Event.LoadMoreClicked)
            runCurrent()
            assertEquals(listOf<String?>("0"), offsets)
            short.onEvent(Event.Hidden)

            val buyerOnlyEngine = pagedEngine(mutableListOf()) { page(1..50, buyerSide = (1..50).toSet()) }
            val (buyerOnly, _) = viewModel(buyerOnlyEngine, listOf(testAccount(1)), activeIndex = 1)
            buyerOnly.onEvent(Event.Shown)
            runCurrent()
            assertTrue(buyerOnly.state.value.page.threads.isEmpty())
            assertTrue(buyerOnly.state.value.page.canLoadMore)
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
        assertEquals(60, viewModel.state.value.page.threads.size)

        advanceTimeBy(PollInterval + 1.seconds)
        runCurrent()

        val state = viewModel.state.value.page
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

        assertEquals(50, viewModel.state.value.page.threads.size)
        assertTrue(viewModel.state.value.page.canLoadMore)
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

        val state = viewModel.state.value.page
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
                } else if (request.url.encodedPath.endsWith("/adverts/11")) {
                    respond("", HttpStatusCode.InternalServerError)
                } else {
                    respond(advertsJson(10), HttpStatusCode.OK, jsonHeaders())
                }
            }
        }
        val (viewModel, _) = viewModel(engine, listOf(testAccount(1)), activeIndex = 1)
        viewModel.onEvent(Event.Shown)
        runCurrent()
        val (withAdvert, withoutAdvert) = viewModel.state.value.page.threads

        viewModel.onEvent(Event.ThreadClicked(1, withAdvert))
        viewModel.onEvent(Event.ThreadClicked(1, withoutAdvert))

        assertEquals(
            Effect.OpenThread(
                localIndex = 1,
                threadId = 1,
                advertId = 10,
                title = "Placeholder advert 10",
                imageUrl = "https://example.invalid/10.jpg",
                status = AdvertStatus.Active,
                isListingDeleted = false,
            ),
            viewModel.effects.awaitEffect<Effect.OpenThread>(),
        )
        assertEquals(
            Effect.OpenThread(1, 2, 11, null, null, status = null, isListingDeleted = false),
            viewModel.effects.awaitEffect<Effect.OpenThread>(),
        )
        viewModel.onEvent(Event.Hidden)
    }

    // --- Listing status on each row -------------------------------------------------------------

    @Test
    fun `a row carries the status of its advert for every wire status`() = runTest(testDispatcher) {
        val statuses = mapOf(
            "new" to AdvertStatus.New,
            "active" to AdvertStatus.Active,
            "limited" to AdvertStatus.Limited,
            "removed_by_user" to AdvertStatus.RemovedByUser,
            "outdated" to AdvertStatus.Outdated,
            "unconfirmed" to AdvertStatus.Unconfirmed,
            "unpaid" to AdvertStatus.Unpaid,
            "moderated" to AdvertStatus.Moderated,
            "blocked" to AdvertStatus.Blocked,
            "disabled" to AdvertStatus.Disabled,
            "removed_by_moderator" to AdvertStatus.RemovedByModerator,
            "something_new" to AdvertStatus.Unknown,
        )
        val adverts = statuses.keys.mapIndexed { index, wire -> (100L + index) to wire }
        val engine = mockEngine(testDispatcher) {
            addHandler { request ->
                if (request.url.encodedPath.endsWith("/threads")) {
                    respond(
                        threadsJson(*adverts.mapIndexed { index, (advertId, _) -> threadJson(index + 1L, advertId) }.toTypedArray()),
                        HttpStatusCode.OK,
                        jsonHeaders(),
                    )
                } else {
                    respond(advertsJsonWithStatus(*adverts.toTypedArray()), HttpStatusCode.OK, jsonHeaders())
                }
            }
        }
        val (viewModel, _) = viewModel(engine, listOf(testAccount(1)), activeIndex = 1)

        viewModel.onEvent(Event.Shown)
        runCurrent()

        val rows = viewModel.state.value.page.threads
        assertEquals(adverts.map { statuses.getValue(it.second) }, rows.map { it.status })
        assertTrue(rows.none { it.isListingDeleted })
        viewModel.onEvent(Event.Hidden)
    }

    @Test
    fun `an advert missing from the page is read once and a 404 marks the row deleted for good`() =
        runTest(testDispatcher) {
            val requests = mutableListOf<String>()
            val engine = listingEngine(requests) { path ->
                when {
                    path.endsWith("/adverts/11") -> respond(AdvertNotFoundJson, HttpStatusCode.NotFound, jsonHeaders())
                    else -> null
                }
            }
            val (viewModel, _) = viewModel(engine, listOf(testAccount(1)), activeIndex = 1)

            viewModel.onEvent(Event.Shown)
            runCurrent()
            val (live, deleted) = viewModel.state.value.page.threads
            assertEquals(AdvertStatus.Active, live.status)
            assertFalse(live.isListingDeleted)
            assertNull(deleted.status)
            assertNull(deleted.title)
            assertTrue(deleted.isListingDeleted)

            // Three polls, and a manual refresh: the deletion is permanent, so nothing asks again.
            repeat(3) {
                advanceTimeBy(PollInterval + 1.seconds)
                runCurrent()
            }
            viewModel.onEvent(Event.RefreshClicked)
            runCurrent()

            assertEquals(1, requests.count { it.endsWith("/adverts/11") })
            assertTrue(viewModel.state.value.page.threads.last().isListingDeleted)
            viewModel.onEvent(Event.Hidden)
        }

    @Test
    fun `a found advert fills its row without blocking the list`() = runTest(testDispatcher) {
        val gate = CompletableDeferred<Unit>()
        val engine = listingEngine(mutableListOf()) { path ->
            if (path.endsWith("/adverts/11")) {
                gate.await()
                respond(advertJson(11, status = "unpaid"), HttpStatusCode.OK, jsonHeaders())
            } else {
                null
            }
        }
        val (viewModel, _) = viewModel(engine, listOf(testAccount(1)), activeIndex = 1)

        viewModel.onEvent(Event.Shown)
        runCurrent()

        // The list is up while the lookup is still waiting.
        val pending = viewModel.state.value.page.threads.last()
        assertTrue(viewModel.state.value.page.hasLoaded)
        assertNull(pending.status)
        assertFalse(pending.isListingDeleted)

        gate.complete(Unit)
        runCurrent()

        val found = viewModel.state.value.page.threads.last()
        assertEquals(AdvertStatus.Unpaid, found.status)
        assertEquals("Placeholder advert 11", found.title)
        assertFalse(found.isListingDeleted)
        viewModel.onEvent(Event.Hidden)
    }

    @Test
    fun `another lookup error leaves the row unknown, the poll does not retry it, a refresh does`() =
        runTest(testDispatcher) {
            val requests = mutableListOf<String>()
            var answerWith = HttpStatusCode.InternalServerError
            val engine = listingEngine(requests) { path ->
                if (path.endsWith("/adverts/11")) {
                    if (answerWith == HttpStatusCode.OK) {
                        respond(advertJson(11, status = "removed_by_user"), HttpStatusCode.OK, jsonHeaders())
                    } else {
                        respond("", answerWith)
                    }
                } else {
                    null
                }
            }
            val (viewModel, _) = viewModel(engine, listOf(testAccount(1)), activeIndex = 1)

            viewModel.onEvent(Event.Shown)
            runCurrent()
            val unknown = viewModel.state.value.page.threads.last()
            assertNull(unknown.status)
            assertFalse(unknown.isListingDeleted)
            assertEquals(1, requests.count { it.endsWith("/adverts/11") })

            repeat(3) {
                advanceTimeBy(PollInterval + 1.seconds)
                runCurrent()
            }
            assertEquals(1, requests.count { it.endsWith("/adverts/11") })

            answerWith = HttpStatusCode.OK
            viewModel.onEvent(Event.RefreshClicked)
            runCurrent()

            assertEquals(2, requests.count { it.endsWith("/adverts/11") })
            assertEquals(AdvertStatus.RemovedByUser, viewModel.state.value.page.threads.last().status)
            viewModel.onEvent(Event.Hidden)
        }

    @Test
    fun `a poll reuses the adverts already read and a refresh and a load more read them again`() =
        runTest(testDispatcher) {
            val requests = mutableListOf<String>()
            val engine = mockEngine(testDispatcher) {
                addHandler { request ->
                    val path = request.url.encodedPath
                    requests += path
                    if (path.endsWith("/threads")) {
                        respond(page(1..50), HttpStatusCode.OK, jsonHeaders())
                    } else {
                        respond(advertsJson(10), HttpStatusCode.OK, jsonHeaders())
                    }
                }
            }
            val (viewModel, _) = viewModel(engine, listOf(testAccount(1)), activeIndex = 1)

            viewModel.onEvent(Event.Shown)
            runCurrent()
            assertEquals(1, requests.count { it.endsWith("/adverts") })

            repeat(3) {
                advanceTimeBy(PollInterval + 1.seconds)
                runCurrent()
            }
            assertEquals(4, requests.count { it.endsWith("/threads") })
            assertEquals(1, requests.count { it.endsWith("/adverts") })
            assertEquals("Placeholder advert 10", viewModel.state.value.page.threads.first().title)

            viewModel.onEvent(Event.RefreshClicked)
            runCurrent()
            assertEquals(2, requests.count { it.endsWith("/adverts") })

            viewModel.onEvent(Event.LoadMoreClicked)
            runCurrent()
            assertEquals(3, requests.count { it.endsWith("/adverts") })
            viewModel.onEvent(Event.Hidden)
        }

    // --- Analytics (SIR-92) ----------------------------------------------------------------------

    private fun FakeAnalytics.messageEvents(name: String) = events.filter { it.first == name }.map { it.second }

    @Test
    fun `opening the tab logs once per visit with the unread total the badge showed`() = runTest(testDispatcher) {
        val engine = mockEngine(testDispatcher) {
            addHandler { respond(threadsJson(), HttpStatusCode.OK, jsonHeaders()) }
        }
        val (viewModel, harness) = viewModel(engine, listOf(testAccount(1)), activeIndex = 1)
        harness.messagesRepository.publishUnread(1, 3)

        viewModel.onEvent(Event.Shown)
        runCurrent()
        // Returning from a conversation, or from the background, shows the screen again.
        viewModel.onEvent(Event.Hidden)
        viewModel.onEvent(Event.Shown)
        advanceTimeBy(PollInterval + 1.seconds)
        runCurrent()

        assertEquals(
            listOf(mapOf<String, Any>("badge_unread_bucket" to "2_5")),
            harness.analytics.messageEvents(AnalyticsEvents.MESSAGES_OPENED),
        )
        viewModel.onEvent(Event.Hidden)
    }

    @Test
    fun `the first load logs counts once the listing lookups settled, and polls and refreshes do not log again`() =
        runTest(testDispatcher) {
            val engine = mockEngine(testDispatcher) {
                addHandler { request ->
                    val path = request.url.encodedPath
                    when {
                        path.endsWith("/threads") -> respond(
                            threadsJson(
                                threadJson(id = 1, advertId = 10, unread = 7),
                                threadJson(id = 2, advertId = 11, interlocutorId = OwnUserId, unread = 4),
                                threadJson(id = 3, advertId = 12),
                                threadJson(id = 4, advertId = 13),
                            ),
                            HttpStatusCode.OK,
                            jsonHeaders(),
                        )
                        path.endsWith("/adverts/12") -> respond(AdvertNotFoundJson, HttpStatusCode.NotFound, jsonHeaders())
                        else -> respond(
                            advertsJsonWithStatus(10L to "active", 13L to "removed_by_user"),
                            HttpStatusCode.OK,
                            jsonHeaders(),
                        )
                    }
                }
            }
            val (viewModel, harness) = viewModel(engine, listOf(testAccount(1)), activeIndex = 1)

            viewModel.onEvent(Event.Shown)
            runCurrent()
            advanceTimeBy(PollInterval + 1.seconds)
            runCurrent()
            viewModel.onEvent(Event.RefreshClicked)
            runCurrent()

            assertEquals(
                listOf(
                    mapOf<String, Any>(
                        "account_index" to 1,
                        "account_count" to 1,
                        "thread_count_bucket" to "2_5",
                        "unread_count_bucket" to "6_10",
                        "buyer_side_count_bucket" to "1",
                        "inactive_listing_count_bucket" to "1",
                        "deleted_listing_count_bucket" to "1",
                    ),
                ),
                harness.analytics.messageEvents(AnalyticsEvents.MESSAGES_LIST_LOADED),
            )
            viewModel.onEvent(Event.Hidden)
        }

    @Test
    fun `each account page logs its own first load`() = runTest(testDispatcher) {
        val engine = mockEngine(testDispatcher) {
            addHandler { request ->
                if (request.url.encodedPath.endsWith("/threads")) {
                    respond(threadsJson(threadJson(1, 10)), HttpStatusCode.OK, jsonHeaders())
                } else {
                    respond(advertsJson(10), HttpStatusCode.OK, jsonHeaders())
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
        viewModel.onEvent(Event.PageSelected(2))
        runCurrent()
        viewModel.onEvent(Event.PageSelected(1))
        runCurrent()

        val logged = harness.analytics.messageEvents(AnalyticsEvents.MESSAGES_LIST_LOADED)
        assertEquals(listOf(1, 2), logged.map { it["account_index"] })
        assertEquals(listOf(2, 2), logged.map { it["account_count"] })
        viewModel.onEvent(Event.Hidden)
    }

    @Test
    fun `a failed first load logs no list outcome, only the shown error`() = runTest(testDispatcher) {
        val engine = mockEngine(testDispatcher) {
            addHandler { respond("", HttpStatusCode.InternalServerError) }
        }
        val (viewModel, harness) = viewModel(engine, listOf(testAccount(1)), activeIndex = 1)

        viewModel.onEvent(Event.Shown)
        runCurrent()
        viewModel.state.first { it.selectedPage?.errorMessage != null }

        assertEquals(emptyList(), harness.analytics.messageEvents(AnalyticsEvents.MESSAGES_LIST_LOADED))
        assertEquals(1, harness.analytics.messageEvents(AnalyticsEvents.ERROR_SHOWN).size)
        viewModel.onEvent(Event.Hidden)
    }

    @Test
    fun `load more logs the page and whether it returned threads`() = runTest(testDispatcher) {
        val offsets = mutableListOf<String?>()
        val engine = pagedEngine(offsets) { offset ->
            when (offset) {
                "0" -> page(1..50)
                "50" -> page(51..100)
                // A full page of threads where the account is the buyer: nothing for the seller.
                else -> page(101..150, buyerSide = (101..150).toSet())
            }
        }
        val (viewModel, harness) = viewModel(engine, listOf(testAccount(1)), activeIndex = 1)
        viewModel.onEvent(Event.Shown)
        runCurrent()
        assertEquals(emptyList(), harness.analytics.messageEvents(AnalyticsEvents.MESSAGES_LOAD_MORE))

        viewModel.onEvent(Event.LoadMoreClicked)
        runCurrent()
        viewModel.onEvent(Event.LoadMoreClicked)
        runCurrent()
        advanceTimeBy(PollInterval + 1.seconds)
        runCurrent()

        assertEquals(
            listOf(
                mapOf<String, Any>("page" to 1, "result" to "threads"),
                mapOf<String, Any>("page" to 2, "result" to "empty"),
            ),
            harness.analytics.messageEvents(AnalyticsEvents.MESSAGES_LOAD_MORE),
        )
        viewModel.onEvent(Event.Hidden)
    }

    @Test
    fun `a failing load more is logged as failed`() = runTest(testDispatcher) {
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

        assertEquals(
            listOf(mapOf<String, Any>("page" to 1, "result" to "failed")),
            harness.analytics.messageEvents(AnalyticsEvents.MESSAGES_LOAD_MORE),
        )
        viewModel.onEvent(Event.Hidden)
    }

    @Test
    fun `opening a row logs the layout, the account and the listing state`() = runTest(testDispatcher) {
        val engine = mockEngine(testDispatcher) {
            addHandler { request ->
                val path = request.url.encodedPath
                when {
                    path.endsWith("/threads") -> respond(
                        threadsJson(threadJson(1, 10, unread = 3), threadJson(2, 11)),
                        HttpStatusCode.OK,
                        jsonHeaders(),
                    )
                    path.endsWith("/adverts/11") -> respond(AdvertNotFoundJson, HttpStatusCode.NotFound, jsonHeaders())
                    else -> respond(advertsJson(10), HttpStatusCode.OK, jsonHeaders())
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
        val (unreadActive, deleted) = viewModel.state.value.page.threads

        viewModel.onEvent(Event.ThreadClicked(1, unreadActive, isTwoPane = true))
        viewModel.onEvent(Event.ThreadClicked(2, deleted))

        assertEquals(
            listOf(
                mapOf<String, Any>(
                    "layout" to "two_pane",
                    "account_count" to 2,
                    "is_active_account" to true,
                    "had_unread" to true,
                    "listing_state" to "active",
                ),
                mapOf<String, Any>(
                    "layout" to "single_pane",
                    "account_count" to 2,
                    "is_active_account" to false,
                    "had_unread" to false,
                    "listing_state" to "deleted",
                ),
            ),
            harness.analytics.messageEvents(AnalyticsEvents.MESSAGES_CONVERSATION_OPENED),
        )
        assertTrue(viewModel.effects.awaitEffect<Effect.OpenThread>().hadUnread)
        assertFalse(viewModel.effects.awaitEffect<Effect.OpenThread>().hadUnread)
        viewModel.onEvent(Event.Hidden)
    }

    @Test
    fun `no event carries anything but codes, counts and flags`() = runTest(testDispatcher) {
        val engine = mockEngine(testDispatcher) {
            addHandler { request ->
                if (request.url.encodedPath.endsWith("/threads")) {
                    respond(threadsJson(threadJson(1, 10, unread = 1)), HttpStatusCode.OK, jsonHeaders())
                } else {
                    respond(advertsJson(10), HttpStatusCode.OK, jsonHeaders())
                }
            }
        }
        val (viewModel, harness) = viewModel(engine, listOf(testAccount(1)), activeIndex = 1)
        viewModel.onEvent(Event.Shown)
        runCurrent()
        viewModel.onEvent(Event.ThreadClicked(1, viewModel.state.value.page.threads.single()))

        val params = harness.analytics.events.flatMap { it.second.entries }
        assertTrue(params.isNotEmpty())
        params.forEach { (key, value) ->
            assertTrue(value is Int || value is Boolean || (value is String && value.length <= 20), "$key=$value")
        }
        viewModel.onEvent(Event.Hidden)
    }

    private val MessagesContract.State.page: MessagesContract.AccountPage get() = selectedPage!!

    /**
     * Two threads, on adverts 10 (on the adverts page, active) and 11 (not on it). [override]
     * answers any path first, and returns null to fall through to the defaults.
     */
    private fun listingEngine(
        requests: MutableList<String>,
        override: suspend io.ktor.client.engine.mock.MockRequestHandleScope.(path: String) -> io.ktor.client.request.HttpResponseData?,
    ) = mockEngine(testDispatcher) {
        addHandler { request ->
            val path = request.url.encodedPath
            requests += path
            override(path) ?: if (path.endsWith("/threads")) {
                respond(threadsJson(threadJson(1, 10), threadJson(2, 11)), HttpStatusCode.OK, jsonHeaders())
            } else {
                respond(advertsJson(10), HttpStatusCode.OK, jsonHeaders())
            }
        }
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
