package com.sirelon.sellsnap.features.seller.messages.presentation

import com.sirelon.sellsnap.analytics.AnalyticsEvents
import com.sirelon.sellsnap.features.common.presentation.awaitEffect
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountRecord
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
    fun `a deleted advert leaves the header on its fallback and the messages still load`() = runTest(testDispatcher) {
        val engine = mockEngine(testDispatcher) {
            addHandler { request ->
                if (request.url.encodedPath.endsWith("/messages")) {
                    respond(messagesJson(messageJson(1, "received", "Placeholder question")), HttpStatusCode.OK, jsonHeaders())
                } else {
                    respond("""{"error":{"status":404,"title":"Not Found"}}""", HttpStatusCode.NotFound, jsonHeaders())
                }
            }
        }
        val (viewModel, harness) = viewModel(engine)

        viewModel.onEvent(Event.Shown)
        runCurrent()

        val state = viewModel.state.value
        assertNull(state.listing)
        assertEquals(1, state.messages.size)
        assertNull(state.errorMessage)
        assertEquals(emptyList(), harness.analytics.events.filter { it.first == AnalyticsEvents.ERROR_SHOWN })
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
    fun `switching the active account closes the conversation`() = runTest(testDispatcher) {
        val engine = mockEngine(testDispatcher) {
            addHandler { respond(messagesJson(), HttpStatusCode.OK, jsonHeaders()) }
        }
        val (viewModel, harness) = viewModel(engine, accounts = listOf(testAccount(1), testAccount(2, olxUserId = 2)))
        viewModel.onEvent(Event.Shown)
        runCurrent()

        harness.accountRepository.setActiveAccount("ua", 2)
        runCurrent()

        viewModel.effects.awaitEffect<Effect.Close>()
        viewModel.onEvent(Event.Hidden)
    }

    private suspend fun viewModel(
        engine: MockEngine,
        accounts: List<OlxAccountRecord> = listOf(testAccount(1)),
    ): Pair<ConversationViewModel, MessagesHarness> {
        val harness = messagesHarness(engine, accounts, activeIndex = 1)
        val viewModel = ConversationViewModel(
            threadId = 5,
            advertId = 10,
            repository = harness.messagesRepository,
            myAdvertsRepository = harness.myAdvertsRepository,
            accountRepository = harness.accountRepository,
            analytics = harness.analytics,
        )
        return viewModel to harness
    }
}
