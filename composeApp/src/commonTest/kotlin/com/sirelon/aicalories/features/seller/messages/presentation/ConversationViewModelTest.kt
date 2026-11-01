package com.sirelon.sellsnap.features.seller.messages.presentation

import com.sirelon.sellsnap.analytics.AnalyticsEvents
import com.sirelon.sellsnap.features.common.presentation.awaitEffect
import com.sirelon.sellsnap.features.seller.ad.publish_success.AdvertStatus
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountRecord
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountState
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountsRecord
import com.sirelon.sellsnap.features.seller.messages.presentation.ConversationContract.Effect
import com.sirelon.sellsnap.features.seller.messages.presentation.ConversationContract.Event
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
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

class ConversationViewModelTest {

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
    fun `messages keep OLX's order, the header comes from the advert and attachments survive`() = runTest(testDispatcher) {
        val engine = mockEngine(testDispatcher) {
            addHandler { request ->
                val path = request.url.encodedPath
                when {
                    path.endsWith("/threads/5/messages") -> respond(
                        messagesJson(
                            messageJson(1, "received", "Placeholder question"),
                            messageJson(2, "sent", "Placeholder answer"),
                            messageJson(3, "received", "", attachments = """[{"name":"a.jpg","url":"https://example.invalid/a.jpg"}]"""),
                        ),
                        HttpStatusCode.OK,
                        jsonHeaders(),
                    )
                    else -> respond(advertJson(10), HttpStatusCode.OK, jsonHeaders())
                }
            }
        }
        val (viewModel, _) = viewModel(engine)

        viewModel.onEvent(Event.Shown)
        runCurrent()

        val state = viewModel.state.value
        assertEquals(listOf(1L, 2L, 3L), state.messages.map { it.id })
        assertEquals(listOf(false, true, false), state.messages.map { it.isSent })
        assertEquals(listOf("https://example.invalid/a.jpg"), state.messages.last().attachmentUrls)
        assertEquals("Placeholder advert 10", state.listing?.title)
        assertTrue(state.hasLoaded)
        viewModel.onEvent(Event.Hidden)
    }

    @Test
    fun `a 404 advert marks the listing deleted and the messages still load`() = runTest(testDispatcher) {
        val engine = mockEngine(testDispatcher) {
            addHandler { request ->
                if (request.url.encodedPath.endsWith("/messages")) {
                    respond(messagesJson(messageJson(1, "received", "Placeholder question")), HttpStatusCode.OK, jsonHeaders())
                } else {
                    respond(AdvertNotFoundJson, HttpStatusCode.NotFound, jsonHeaders())
                }
            }
        }
        val (viewModel, harness) = viewModel(engine)

        viewModel.onEvent(Event.Shown)
        runCurrent()

        val state = viewModel.state.value
        assertNull(state.listing)
        assertTrue(state.isListingDeleted)
        assertEquals(1, state.messages.size)
        assertNull(state.errorMessage)
        assertEquals(emptyList(), harness.analytics.events.filter { it.first == AnalyticsEvents.ERROR_SHOWN })
        viewModel.onEvent(Event.Hidden)
    }

    @Test
    fun `a row that is already deleted skips the advert request`() = runTest(testDispatcher) {
        val requests = mutableListOf<String>()
        val engine = mockEngine(testDispatcher) {
            addHandler { request ->
                requests += request.url.encodedPath
                respond(messagesJson(), HttpStatusCode.OK, jsonHeaders())
            }
        }
        val (viewModel, _) = viewModel(engine, isListingDeleted = true)

        assertTrue(viewModel.state.value.isListingDeleted)
        viewModel.onEvent(Event.Shown)
        runCurrent()

        assertTrue(requests.any { it.endsWith("/threads/5/messages") })
        assertTrue(requests.none { it.endsWith("/adverts/10") })
        assertTrue(viewModel.state.value.isListingDeleted)
        viewModel.onEvent(Event.Hidden)
    }

    @Test
    fun `an advert error other than 404 leaves the row's status and does not mark it deleted`() =
        runTest(testDispatcher) {
            val engine = mockEngine(testDispatcher) {
                addHandler { request ->
                    if (request.url.encodedPath.endsWith("/messages")) {
                        respond(messagesJson(), HttpStatusCode.OK, jsonHeaders())
                    } else {
                        respond("", HttpStatusCode.InternalServerError)
                    }
                }
            }
            val (viewModel, _) = viewModel(engine, status = AdvertStatus.RemovedByUser)

            viewModel.onEvent(Event.Shown)
            runCurrent()

            val state = viewModel.state.value
            assertNull(state.listing)
            assertFalse(state.isListingDeleted)
            assertEquals(AdvertStatus.RemovedByUser, state.status)
            viewModel.onEvent(Event.Hidden)
        }

    @Test
    fun `the loaded advert supersedes the row's status in the header`() = runTest(testDispatcher) {
        val engine = mockEngine(testDispatcher) {
            addHandler { request ->
                if (request.url.encodedPath.endsWith("/messages")) {
                    respond(messagesJson(), HttpStatusCode.OK, jsonHeaders())
                } else {
                    respond(advertJson(10, status = "removed_by_user"), HttpStatusCode.OK, jsonHeaders())
                }
            }
        }
        val (viewModel, _) = viewModel(engine, status = AdvertStatus.Active)

        viewModel.onEvent(Event.Shown)
        runCurrent()

        assertEquals(AdvertStatus.RemovedByUser, viewModel.state.value.listing?.status)
        viewModel.onEvent(Event.Hidden)
    }

    @Test
    fun `a failing messages call shows an error and reports it`() = runTest(testDispatcher) {
        val engine = mockEngine(testDispatcher) {
            addHandler { respond("", HttpStatusCode.InternalServerError) }
        }
        val (viewModel, harness) = viewModel(engine)

        viewModel.onEvent(Event.Shown)
        runCurrent()

        viewModel.state.first { it.errorMessage != null }
        val reported = harness.analytics.events.filter { it.first == AnalyticsEvents.ERROR_SHOWN }
        assertEquals("conversation", reported.single().second["screen"])
        viewModel.onEvent(Event.Hidden)
    }

    @Test
    fun `while shown it re-reads the messages every poll interval and stops once hidden`() = runTest(testDispatcher) {
        var messageCalls = 0
        val engine = mockEngine(testDispatcher) {
            addHandler { request ->
                if (request.url.encodedPath.endsWith("/messages")) {
                    messageCalls++
                    respond(messagesJson(messageJson(1, "received", "Placeholder question")), HttpStatusCode.OK, jsonHeaders())
                } else {
                    respond(advertJson(10), HttpStatusCode.OK, jsonHeaders())
                }
            }
        }
        val (viewModel, _) = viewModel(engine)

        viewModel.onEvent(Event.Shown)
        runCurrent()
        advanceTimeBy(PollInterval + 1.seconds)
        runCurrent()
        assertEquals(2, messageCalls)

        viewModel.onEvent(Event.Hidden)
        advanceTimeBy(PollInterval * 3)
        runCurrent()
        assertEquals(2, messageCalls)
    }

    @Test
    fun `the conversation reads with its own account's token and stays open when the active account changes`() =
        runTest(testDispatcher) {
            val tokens = mutableListOf<String?>()
            val engine = mockEngine(testDispatcher) {
                addHandler { request ->
                    if (request.url.encodedPath.endsWith("/messages")) tokens += request.headers["Authorization"]
                    respond(messagesJson(messageJson(1, "received", "Placeholder question")), HttpStatusCode.OK, jsonHeaders())
                }
            }
            val (viewModel, harness) = viewModel(
                engine,
                accounts = listOf(testAccount(1), testAccount(2, olxUserId = 2)),
                localIndex = 2,
            )
            viewModel.onEvent(Event.Shown)
            runCurrent()

            harness.accountRepository.setActiveAccount("ua", 2)
            runCurrent()
            harness.accountRepository.setActiveAccount("ua", 1)
            runCurrent()
            advanceTimeBy(PollInterval + 1.seconds)
            runCurrent()

            assertEquals(listOf<String?>("Bearer token-2", "Bearer token-2"), tokens)
            assertEquals(1, viewModel.state.value.messages.size)
            viewModel.onEvent(Event.Hidden)
        }

    @Test
    fun `a key saved without an account reads with the active one`() = runTest(testDispatcher) {
        val tokens = mutableListOf<String?>()
        val engine = mockEngine(testDispatcher) {
            addHandler { request ->
                if (request.url.encodedPath.endsWith("/messages")) tokens += request.headers["Authorization"]
                respond(messagesJson(), HttpStatusCode.OK, jsonHeaders())
            }
        }
        val (viewModel, _) = viewModel(
            engine,
            accounts = listOf(testAccount(1), testAccount(2, olxUserId = 2)),
            localIndex = null,
        )
        viewModel.onEvent(Event.Shown)
        runCurrent()

        assertEquals(listOf<String?>("Bearer token-1"), tokens)
        viewModel.onEvent(Event.Hidden)
    }

    @Test
    fun `a conversation restored before the accounts have loaded waits for them instead of closing`() =
        runTest(testDispatcher) {
            val tokens = mutableListOf<String?>()
            val engine = mockEngine(testDispatcher) {
                addHandler { request ->
                    if (request.url.encodedPath.endsWith("/messages")) tokens += request.headers["Authorization"]
                    respond(messagesJson(), HttpStatusCode.OK, jsonHeaders())
                }
            }
            val (viewModel, harness) = viewModel(engine, accounts = emptyList(), localIndex = 2)
            viewModel.onEvent(Event.Shown)
            runCurrent()

            harness.accountStore.write(
                OlxAccountsRecord(
                    accounts = listOf(testAccount(1), testAccount(2, olxUserId = 2)),
                    activeByCountry = mapOf("ua" to 1),
                    nextLocalIndex = 3,
                ),
            )
            runCurrent()

            assertEquals(listOf<String?>("Bearer token-2"), tokens)
            assertTrue(viewModel.state.value.hasLoaded)
            viewModel.onEvent(Event.Hidden)
        }

    @Test
    fun `its account needing a reconnect shows the reconnect state, and disconnecting it closes the conversation`() =
        runTest(testDispatcher) {
            val requests = mutableListOf<String>()
            val engine = mockEngine(testDispatcher) {
                addHandler { request -> requests += request.url.encodedPath; respond(messagesJson(), HttpStatusCode.OK, jsonHeaders()) }
            }
            val (viewModel, harness) = viewModel(
                engine,
                accounts = listOf(testAccount(1), testAccount(2, olxUserId = 2, state = OlxAccountState.NeedsReconnect)),
                localIndex = 2,
            )
            viewModel.onEvent(Event.Shown)
            runCurrent()

            assertTrue(viewModel.state.value.needsReconnect)
            assertTrue(requests.none { it.endsWith("/messages") })

            harness.accountStore.disconnect(2)
            runCurrent()

            viewModel.effects.awaitEffect<Effect.Close>()
            viewModel.onEvent(Event.Hidden)
        }

    @Test
    fun `the header starts from the list row's title and image, then the advert adds its own`() = runTest(testDispatcher) {
        val engine = mockEngine(testDispatcher) {
            addHandler { request ->
                if (request.url.encodedPath.endsWith("/messages")) {
                    respond(messagesJson(), HttpStatusCode.OK, jsonHeaders())
                } else {
                    respond(advertJson(10), HttpStatusCode.OK, jsonHeaders())
                }
            }
        }
        val (viewModel, _) = viewModel(engine, title = "Row title", imageUrl = "https://example.invalid/row.jpg")

        // Before anything is loaded, with no listing yet.
        assertEquals("Row title", viewModel.state.value.title)
        assertEquals("https://example.invalid/row.jpg", viewModel.state.value.imageUrl)
        assertNull(viewModel.state.value.listing)

        viewModel.onEvent(Event.Shown)
        runCurrent()

        assertEquals("Row title", viewModel.state.value.title)
        assertEquals("Placeholder advert 10", viewModel.state.value.listing?.title)
        viewModel.onEvent(Event.Hidden)
    }

    @Test
    fun `a row without a title leaves the header unseeded`() = runTest(testDispatcher) {
        val engine = mockEngine(testDispatcher) { addHandler { respond(messagesJson(), HttpStatusCode.OK, jsonHeaders()) } }
        val (viewModel, _) = viewModel(engine)

        assertNull(viewModel.state.value.title)
        assertNull(viewModel.state.value.imageUrl)
    }

    private suspend fun viewModel(
        engine: MockEngine,
        accounts: List<OlxAccountRecord> = listOf(testAccount(1)),
        title: String? = null,
        imageUrl: String? = null,
        status: AdvertStatus? = null,
        isListingDeleted: Boolean = false,
        localIndex: Int? = 1,
    ): Pair<ConversationViewModel, MessagesHarness> {
        val harness = messagesHarness(engine, accounts, activeIndex = 1)
        val viewModel = ConversationViewModel(
            keyLocalIndex = localIndex,
            threadId = 5,
            advertId = 10,
            title = title,
            imageUrl = imageUrl,
            status = status,
            isListingDeleted = isListingDeleted,
            repository = harness.messagesRepository,
            myAdvertsRepository = harness.myAdvertsRepository,
            accountRepository = harness.accountRepository,
            analytics = harness.analytics,
        )
        return viewModel to harness
    }
}
