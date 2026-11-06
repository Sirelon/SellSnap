package com.sirelon.sellsnap.features.seller.messages.presentation

import com.sirelon.sellsnap.analytics.AnalyticsEvents
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountRecord
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountState
import com.sirelon.sellsnap.features.seller.messages.presentation.ConversationContract.Event
import com.sirelon.sellsnap.features.seller.messages.presentation.ConversationContract.PendingStatus
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
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

/**
 * Replying from the conversation (SIR-95): `POST threads/{id}/messages` with the thread's own
 * account, an optimistic bubble, and Retry on failure. OLX answers a send without the message, so
 * the bubble lasts until a load of the thread shows the reply.
 */
class SendMessageTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** A fake OLX thread: what GET messages returns now, and how the next POST answers. */
    private class FakeThread {
        var messages = listOf(messageJson(1, "received", "Placeholder question"))
        var postStatus = HttpStatusCode.OK
        var postBody = ""

        /** When set, a POST waits for it: the send stays in flight. */
        var postGate: CompletableDeferred<Unit>? = null

        /** Applied when a POST is accepted, as OLX storing the reply. */
        var onAccepted: () -> Unit = {}
        val posts = mutableListOf<Pair<String?, String>>()

        /** Thread commands (mark-as-read): the Authorization header and the body. */
        val commands = mutableListOf<Pair<String?, String>>()
        var commandStatus = HttpStatusCode.NoContent
        var messageReads = 0
    }

    private fun engine(thread: FakeThread): MockEngine = mockEngine(testDispatcher) {
        addHandler { request ->
            val path = request.url.encodedPath
            when {
                request.method == HttpMethod.Post && path.endsWith("/messages") -> {
                    thread.posts += request.headers["Authorization"] to (request.body as TextContent).text
                    thread.postGate?.await()
                    if (thread.postStatus.value in 200..299) thread.onAccepted()
                    respond(thread.postBody, thread.postStatus, jsonHeaders())
                }
                request.method == HttpMethod.Post && path.endsWith("/commands") -> {
                    thread.commands += request.headers["Authorization"] to (request.body as TextContent).text
                    respond("", thread.commandStatus, jsonHeaders())
                }
                path.endsWith("/messages") -> {
                    thread.messageReads++
                    respond(messagesJson(*thread.messages.toTypedArray()), HttpStatusCode.OK, jsonHeaders())
                }
                else -> respond(advertJson(10), HttpStatusCode.OK, jsonHeaders())
            }
        }
    }

    private suspend fun conversation(
        thread: FakeThread,
        accounts: List<OlxAccountRecord> = listOf(testAccount(1)),
        localIndex: Int = 1,
        isListingDeleted: Boolean = false,
        hadUnread: Boolean = true,
    ): Pair<ConversationViewModel, MessagesHarness> {
        val harness = messagesHarness(engine(thread), accounts, activeIndex = 1)
        val viewModel = ConversationViewModel(
            keyLocalIndex = localIndex,
            threadId = 5,
            advertId = 10,
            title = null,
            imageUrl = null,
            status = null,
            isListingDeleted = isListingDeleted,
            hadUnread = hadUnread,
            repository = harness.messagesRepository,
            myAdvertsRepository = harness.myAdvertsRepository,
            outcomeStore = harness.outcomeStore,
            accountRepository = harness.accountRepository,
            analytics = harness.analytics,
            openAIClient = harness.openAIClient,
        )
        return viewModel to harness
    }

    private fun sentEvents(harness: MessagesHarness) =
        harness.analytics.events.filter { it.first == AnalyticsEvents.MESSAGE_SENT }.map { it.second }

    @Test
    fun `send clears the draft and shows a sending bubble at once, then the reload shows the reply`() =
        runTest(testDispatcher) {
            val thread = FakeThread()
            thread.onAccepted = {
                thread.messages = thread.messages + messageJson(2, "sent", "Placeholder reply")
            }
            val (viewModel, harness) = conversation(thread)
            viewModel.onEvent(Event.Shown)
            runCurrent()

            viewModel.onEvent(Event.DraftChanged("  Placeholder reply \n"))
            assertTrue(viewModel.state.value.canSend)
            viewModel.onEvent(Event.SendClicked(isTwoPane = false))

            // Before OLX answers.
            assertEquals("", viewModel.state.value.draft)
            val pending = viewModel.state.value.pendingMessages.single()
            assertEquals("Placeholder reply", pending.text)
            assertEquals(PendingStatus.Sending, pending.status)

            runCurrent()

            assertEquals("""{"text":"Placeholder reply"}""", thread.posts.single().second)
            assertEquals(emptyList(), viewModel.state.value.pendingMessages)
            assertEquals(listOf(1L, 2L), viewModel.state.value.messages.map { it.id })
            // The accepted reply marked the thread read.
            assertFalse(viewModel.state.value.canMarkAsRead)
            assertEquals(
                mapOf<String, Any>(
                    "source" to "typed",
                    "result" to "success",
                    "is_retry" to false,
                    "layout" to "single_pane",
                    "account_count" to 1,
                    "had_scam_warning" to false,
                ),
                sentEvents(harness).single(),
            )
            viewModel.onEvent(Event.Hidden)
        }

    @Test
    fun `an accepted reply on an unread thread marks it read with the conversation's account and clears the badge`() =
        runTest(testDispatcher) {
            val thread = FakeThread()
            val harness = messagesHarness(
                engine(thread),
                listOf(testAccount(1), testAccount(2, olxUserId = 2)),
                activeIndex = 1,
            )
            harness.messagesRepository.publishUnread(1, mapOf(5L to 7))
            harness.messagesRepository.publishUnread(2, mapOf(5L to 2))
            val viewModel = ConversationViewModel(
                keyLocalIndex = 2,
                threadId = 5,
                advertId = 10,
                title = null,
                imageUrl = null,
                status = null,
                isListingDeleted = false,
                hadUnread = true,
                repository = harness.messagesRepository,
                myAdvertsRepository = harness.myAdvertsRepository,
                outcomeStore = harness.outcomeStore,
                accountRepository = harness.accountRepository,
                analytics = harness.analytics,
                openAIClient = harness.openAIClient,
            )
            viewModel.onEvent(Event.Shown)
            runCurrent()
            // Opening and polling send nothing.
            assertEquals(emptyList(), thread.commands)
            assertTrue(viewModel.state.value.canMarkAsRead)

            viewModel.onEvent(Event.DraftChanged("Placeholder reply"))
            viewModel.onEvent(Event.SendClicked(isTwoPane = false))
            runCurrent()

            assertEquals(listOf<Pair<String?, String>>("Bearer token-2" to """{"command":"mark-as-read"}"""), thread.commands)
            assertFalse(viewModel.state.value.canMarkAsRead)
            assertFalse(viewModel.state.value.isMarkingAsRead)
            // Account 2's thread is zero; account 1's thread 5 is a different thread and keeps its seven.
            assertEquals(7, harness.messagesRepository.unreadCount.value)
            val action = harness.analytics.events
                .filter { it.first == AnalyticsEvents.MESSAGES_CONVERSATION_ACTION }.map { it.second }.single()
            assertEquals("mark_as_read", action["action"])
            assertEquals("success", action["result"])
            assertEquals("send", action["trigger"])
            viewModel.onEvent(Event.Hidden)
        }

    @Test
    fun `an accepted reply on a thread with nothing unread sends no command`() = runTest(testDispatcher) {
        val thread = FakeThread()
        val (viewModel, harness) = conversation(thread, hadUnread = false)
        viewModel.onEvent(Event.Shown)
        runCurrent()

        viewModel.onEvent(Event.DraftChanged("Placeholder reply"))
        viewModel.onEvent(Event.SendClicked(isTwoPane = false))
        runCurrent()

        assertEquals(1, thread.posts.size)
        assertEquals(emptyList(), thread.commands)
        assertTrue(harness.analytics.events.none { it.first == AnalyticsEvents.MESSAGES_CONVERSATION_ACTION })
        viewModel.onEvent(Event.Hidden)
    }

    @Test
    fun `a refused reply sends no mark-as-read`() = runTest(testDispatcher) {
        val thread = FakeThread()
        thread.postStatus = HttpStatusCode.InternalServerError
        val (viewModel, _) = conversation(thread)
        viewModel.onEvent(Event.Shown)
        runCurrent()

        viewModel.onEvent(Event.DraftChanged("Placeholder reply"))
        viewModel.onEvent(Event.SendClicked(isTwoPane = false))
        runCurrent()

        assertEquals(emptyList(), thread.commands)
        assertTrue(viewModel.state.value.canMarkAsRead)
        viewModel.onEvent(Event.Hidden)
    }

    @Test
    fun `a failed mark-as-read after a reply is silent, keeps the action and is recorded without error_shown`() =
        runTest(testDispatcher) {
            val thread = FakeThread()
            thread.commandStatus = HttpStatusCode.InternalServerError
            val (viewModel, harness) = conversation(thread)
            viewModel.onEvent(Event.Shown)
            runCurrent()

            viewModel.onEvent(Event.DraftChanged("Placeholder reply"))
            viewModel.onEvent(Event.SendClicked(isTwoPane = false))
            runCurrent()

            assertEquals(1, thread.commands.size)
            assertTrue(viewModel.state.value.canMarkAsRead)
            assertFalse(viewModel.state.value.isMarkingAsRead)
            assertTrue(harness.analytics.events.none { it.first == AnalyticsEvents.ERROR_SHOWN })
            assertEquals(1, harness.analytics.exceptions.size)
            val action = harness.analytics.events
                .filter { it.first == AnalyticsEvents.MESSAGES_CONVERSATION_ACTION }.map { it.second }.single()
            assertEquals("failed", action["result"])
            assertEquals("send", action["trigger"])
            // The reply itself went through.
            assertEquals(1, sentEvents(harness).count { it["result"] == "success" })
            viewModel.onEvent(Event.Hidden)
        }

    @Test
    fun `an accepted reply stays as a sent bubble until a load shows it`() = runTest(testDispatcher) {
        val thread = FakeThread()
        val (viewModel, _) = conversation(thread)
        viewModel.onEvent(Event.Shown)
        runCurrent()

        viewModel.onEvent(Event.DraftChanged("Placeholder reply"))
        viewModel.onEvent(Event.SendClicked(isTwoPane = false))
        runCurrent()

        // OLX accepted it, but the re-read right after does not have it yet.
        assertEquals(PendingStatus.Sent, viewModel.state.value.pendingMessages.single().status)

        thread.messages = thread.messages + messageJson(2, "sent", "Placeholder reply")
        advanceTimeBy(PollInterval + 1.seconds)
        runCurrent()

        assertEquals(emptyList(), viewModel.state.value.pendingMessages)
        assertEquals(1, viewModel.state.value.messages.count { it.isSent })
        viewModel.onEvent(Event.Hidden)
    }

    @Test
    fun `a failed send keeps the text with a failed state, reports it, and Retry sends it again`() =
        runTest(testDispatcher) {
            val thread = FakeThread()
            thread.postStatus = HttpStatusCode.InternalServerError
            val (viewModel, harness) = conversation(thread)
            viewModel.onEvent(Event.Shown)
            runCurrent()

            viewModel.onEvent(Event.DraftChanged("Placeholder reply"))
            viewModel.onEvent(Event.SendClicked(isTwoPane = true))
            runCurrent()

            val failed = viewModel.state.value.pendingMessages.single()
            assertEquals(PendingStatus.Failed, failed.status)
            assertEquals("Placeholder reply", failed.text)
            assertNull(failed.olxRefusal)
            val shown = harness.analytics.events.filter { it.first == AnalyticsEvents.ERROR_SHOWN }.map { it.second }.single()
            assertEquals("conversation", shown["screen"])
            assertTrue((shown["reason"] as String).startsWith("send_failed:"))
            assertEquals("failed", sentEvents(harness).single()["result"])

            thread.postStatus = HttpStatusCode.OK
            thread.onAccepted = {
                thread.messages = thread.messages + messageJson(2, "sent", "Placeholder reply")
            }
            viewModel.onEvent(Event.RetrySendClicked(failed.localId, isTwoPane = true))
            assertEquals(PendingStatus.Sending, viewModel.state.value.pendingMessages.single().status)
            runCurrent()

            assertEquals(2, thread.posts.size)
            assertEquals(thread.posts[0].second, thread.posts[1].second)
            assertEquals(emptyList(), viewModel.state.value.pendingMessages)
            assertEquals(listOf(2L), viewModel.state.value.messages.filter { it.isSent }.map { it.id })
            val retry = sentEvents(harness).last()
            assertEquals("success", retry["result"])
            assertEquals(true, retry["is_retry"])
            assertEquals("two_pane", retry["layout"])
            viewModel.onEvent(Event.Hidden)
        }

    @Test
    fun `OLX's refusal is kept on the failed bubble in its own words`() = runTest(testDispatcher) {
        val thread = FakeThread()
        thread.postStatus = HttpStatusCode.BadRequest
        thread.postBody = """{"error":{"status":400,"validation":[{"field":"text","detail":"Placeholder refusal"}]}}"""
        val (viewModel, harness) = conversation(thread)
        viewModel.onEvent(Event.Shown)
        runCurrent()

        viewModel.onEvent(Event.DraftChanged("Placeholder reply"))
        viewModel.onEvent(Event.SendClicked(isTwoPane = false))
        runCurrent()

        val failed = viewModel.state.value.pendingMessages.single()
        assertEquals(PendingStatus.Failed, failed.status)
        assertEquals("text: Placeholder refusal", failed.olxRefusal)
        val shown = harness.analytics.events.single { it.first == AnalyticsEvents.ERROR_SHOWN }.second
        assertEquals("send_failed:validation:text", shown["reason"])
        viewModel.onEvent(Event.Hidden)
    }

    @Test
    fun `blank text cannot be sent`() = runTest(testDispatcher) {
        val thread = FakeThread()
        val (viewModel, _) = conversation(thread)
        viewModel.onEvent(Event.Shown)
        runCurrent()

        viewModel.onEvent(Event.DraftChanged("  \n "))
        assertFalse(viewModel.state.value.canSend)
        viewModel.onEvent(Event.SendClicked(isTwoPane = false))
        runCurrent()

        assertEquals(emptyList(), thread.posts)
        assertEquals(emptyList(), viewModel.state.value.pendingMessages)
        assertEquals("  \n ", viewModel.state.value.draft)
        viewModel.onEvent(Event.Hidden)
    }

    @Test
    fun `a poll during a send neither drops nor duplicates the pending bubble`() = runTest(testDispatcher) {
        val thread = FakeThread()
        val gate = CompletableDeferred<Unit>()
        thread.postGate = gate
        val (viewModel, _) = conversation(thread)
        viewModel.onEvent(Event.Shown)
        runCurrent()

        viewModel.onEvent(Event.DraftChanged("Placeholder reply"))
        viewModel.onEvent(Event.SendClicked(isTwoPane = false))
        runCurrent()
        val readsBefore = thread.messageReads

        // The poll lands while the send is in flight and OLX does not show the reply yet.
        advanceTimeBy(PollInterval + 1.seconds)
        runCurrent()
        assertTrue(thread.messageReads > readsBefore)
        assertEquals(PendingStatus.Sending, viewModel.state.value.pendingMessages.single().status)

        thread.messages = thread.messages + messageJson(2, "sent", "Placeholder reply")
        gate.complete(Unit)
        runCurrent()

        assertEquals(emptyList(), viewModel.state.value.pendingMessages)
        assertEquals(listOf(2L), viewModel.state.value.messages.filter { it.isSent }.map { it.id })
        viewModel.onEvent(Event.Hidden)
    }

    @Test
    fun `a poll that already shows the reply in flight replaces the bubble, and the answer adds nothing`() =
        runTest(testDispatcher) {
            val thread = FakeThread()
            val gate = CompletableDeferred<Unit>()
            thread.postGate = gate
            val (viewModel, _) = conversation(thread)
            viewModel.onEvent(Event.Shown)
            runCurrent()

            viewModel.onEvent(Event.DraftChanged("Placeholder reply"))
            viewModel.onEvent(Event.SendClicked(isTwoPane = false))
            runCurrent()

            thread.messages = thread.messages + messageJson(2, "sent", "Placeholder reply")
            advanceTimeBy(PollInterval + 1.seconds)
            runCurrent()
            assertEquals(emptyList(), viewModel.state.value.pendingMessages)

            gate.complete(Unit)
            runCurrent()

            assertEquals(emptyList(), viewModel.state.value.pendingMessages)
            assertEquals(1, viewModel.state.value.messages.count { it.isSent })
            viewModel.onEvent(Event.Hidden)
        }

    @Test
    fun `an account that needs a reconnect sends nothing`() = runTest(testDispatcher) {
        val thread = FakeThread()
        val (viewModel, harness) = conversation(
            thread,
            accounts = listOf(testAccount(1, state = OlxAccountState.NeedsReconnect)),
        )
        viewModel.onEvent(Event.Shown)
        runCurrent()
        assertTrue(viewModel.state.value.needsReconnect)

        viewModel.onEvent(Event.DraftChanged("Placeholder reply"))
        viewModel.onEvent(Event.SendClicked(isTwoPane = false))
        runCurrent()

        assertEquals(emptyList(), thread.posts)
        assertEquals(emptyList(), viewModel.state.value.pendingMessages)
        // The text waits in the draft for after the reconnect.
        assertEquals("Placeholder reply", viewModel.state.value.draft)
        assertEquals(emptyList(), sentEvents(harness))
        viewModel.onEvent(Event.Hidden)
    }

    @Test
    fun `the reply goes out with the conversation's account, not the active one`() = runTest(testDispatcher) {
        val thread = FakeThread()
        val (viewModel, harness) = conversation(
            thread,
            accounts = listOf(testAccount(1), testAccount(2, olxUserId = 2)),
            localIndex = 2,
        )
        viewModel.onEvent(Event.Shown)
        runCurrent()

        viewModel.onEvent(Event.DraftChanged("Placeholder reply"))
        viewModel.onEvent(Event.SendClicked(isTwoPane = false))
        runCurrent()

        assertEquals("Bearer token-2", thread.posts.single().first)
        assertEquals(2, sentEvents(harness).single()["account_count"])
        viewModel.onEvent(Event.Hidden)
    }

    @Test
    fun `a deleted listing does not stop a reply - OLX decides`() = runTest(testDispatcher) {
        val thread = FakeThread()
        val (viewModel, _) = conversation(thread, isListingDeleted = true)
        viewModel.onEvent(Event.Shown)
        runCurrent()

        viewModel.onEvent(Event.DraftChanged("Placeholder reply"))
        viewModel.onEvent(Event.SendClicked(isTwoPane = false))
        runCurrent()

        assertEquals(1, thread.posts.size)
        viewModel.onEvent(Event.Hidden)
    }
}
