package com.sirelon.sellsnap.features.seller.messages.presentation

import com.sirelon.sellsnap.analytics.AnalyticsEvents
import com.sirelon.sellsnap.features.common.presentation.awaitEffect
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Explicit "Mark as read" (SIR-88 finding 12: reading a thread does not clear `unread_count`).
 * The command is sent only on the seller's tap - never on open or poll.
 */
class MarkAsReadTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Every request, with its body when it has one. */
    private class Requests {
        val all = mutableListOf<Triple<HttpMethod, String, String?>>()
        val posts get() = all.filter { it.first == HttpMethod.Post }
        val tokens = mutableListOf<String?>()
    }

    private fun engine(requests: Requests, commandStatus: HttpStatusCode = HttpStatusCode.NoContent): MockEngine =
        mockEngine(testDispatcher) {
            addHandler { request ->
                val path = request.url.encodedPath
                requests.all += Triple(request.method, path, (request.body as? TextContent)?.text)
                when {
                    request.method == HttpMethod.Post -> {
                        requests.tokens += request.headers["Authorization"]
                        respond("", commandStatus)
                    }
                    path.endsWith("/messages") ->
                        respond(messagesJson(messageJson(1, "received", "Placeholder question")), HttpStatusCode.OK, jsonHeaders())
                    path.endsWith("/threads") ->
                        respond(
                            threadsJson(threadJson(5, 10, unread = 2), threadJson(6, 11, unread = 1)),
                            HttpStatusCode.OK,
                            jsonHeaders(),
                        )
                    path.endsWith("/adverts") -> respond(advertsJson(10, 11), HttpStatusCode.OK, jsonHeaders())
                    else -> respond(advertJson(10), HttpStatusCode.OK, jsonHeaders())
                }
            }
        }

    private fun eventsOf(harness: MessagesHarness, name: String) =
        harness.analytics.events.filter { it.first == name }.map { it.second }

    @Test
    fun `the repository zeroes that thread for that account only and sends the command with its token`() =
        runTest(testDispatcher) {
            val requests = Requests()
            val harness = messagesHarness(
                engine(requests),
                listOf(testAccount(1), testAccount(2, olxUserId = 2)),
                activeIndex = 1,
            )
            val repository = harness.messagesRepository
            repository.publishUnread(1, mapOf(5L to 2, 6L to 1))
            repository.publishUnread(2, mapOf(5L to 4))
            assertEquals(7, repository.unreadCount.value)

            repository.markAsRead(localIndex = 2, threadId = 5)

            // Thread 5 of account 1 is a different thread and keeps its two.
            assertEquals(3, repository.unreadCount.value)
            val post = requests.posts.single()
            assertTrue(post.second.endsWith("/threads/5/commands"))
            assertEquals("""{"command":"mark-as-read"}""", post.third)
            assertEquals(listOf<String?>("Bearer token-2"), requests.tokens)
        }

    @Test
    fun `a refused command leaves the unread counts alone`() = runTest(testDispatcher) {
        val requests = Requests()
        val harness = messagesHarness(
            engine(requests, HttpStatusCode.InternalServerError),
            listOf(testAccount(1)),
            activeIndex = 1,
        )
        harness.messagesRepository.publishUnread(1, mapOf(5L to 2))

        assertFailsWith<Exception> { harness.messagesRepository.markAsRead(1, 5) }

        assertEquals(2, harness.messagesRepository.unreadCount.value)
    }

    @Test
    fun `the action is offered only while the thread has unread messages`() = runTest(testDispatcher) {
        val read = conversation(messagesHarness(engine(Requests()), listOf(testAccount(1)), 1), hadUnread = false)
        val unread = conversation(messagesHarness(engine(Requests()), listOf(testAccount(1)), 1), hadUnread = true)

        assertFalse(read.state.value.canMarkAsRead)
        assertTrue(unread.state.value.canMarkAsRead)
    }

    @Test
    fun `a tap disables the action while it is in flight, then removes it and logs success`() =
        runTest(testDispatcher) {
            val requests = Requests()
            val harness = messagesHarness(engine(requests), listOf(testAccount(1)), activeIndex = 1)
            harness.messagesRepository.publishUnread(1, mapOf(5L to 2))
            val viewModel = conversation(harness, hadUnread = true)
            viewModel.onEvent(ConversationContract.Event.Shown)
            runCurrent()

            viewModel.onEvent(ConversationContract.Event.MarkAsReadClicked)
            assertTrue(viewModel.state.value.isMarkingAsRead)
            assertTrue(viewModel.state.value.canMarkAsRead)
            // A second tap while it is in flight sends nothing more.
            viewModel.onEvent(ConversationContract.Event.MarkAsReadClicked)
            runCurrent()

            assertFalse(viewModel.state.value.isMarkingAsRead)
            assertFalse(viewModel.state.value.canMarkAsRead)
            assertEquals(1, requests.posts.size)
            assertEquals(0, harness.messagesRepository.unreadCount.value)
            val logged = eventsOf(harness, AnalyticsEvents.MESSAGES_CONVERSATION_ACTION).single()
            assertEquals("mark_as_read", logged["action"])
            assertEquals("success", logged["result"])
            assertEquals(true, logged["had_unread"])
            viewModel.onEvent(ConversationContract.Event.Hidden)
        }

    @Test
    fun `a failed command keeps the action, reports the error, shows the generic message and logs failed`() =
        runTest(testDispatcher) {
            val requests = Requests()
            val harness = messagesHarness(
                engine(requests, HttpStatusCode.InternalServerError),
                listOf(testAccount(1)),
                activeIndex = 1,
            )
            harness.messagesRepository.publishUnread(1, mapOf(5L to 2))
            val viewModel = conversation(harness, hadUnread = true)
            viewModel.onEvent(ConversationContract.Event.Shown)
            runCurrent()

            viewModel.onEvent(ConversationContract.Event.MarkAsReadClicked)
            runCurrent()

            assertTrue(viewModel.state.value.canMarkAsRead)
            assertFalse(viewModel.state.value.isMarkingAsRead)
            assertEquals(2, harness.messagesRepository.unreadCount.value)
            viewModel.effects.awaitEffect<ConversationContract.Effect.ShowMessage>()
            val shown = eventsOf(harness, AnalyticsEvents.ERROR_SHOWN).single()
            assertEquals("conversation", shown["screen"])
            assertTrue((shown["reason"] as String).startsWith("mark_read_failed:"))
            val logged = eventsOf(harness, AnalyticsEvents.MESSAGES_CONVERSATION_ACTION).single()
            assertEquals("failed", logged["result"])

            // The seller can try again.
            viewModel.onEvent(ConversationContract.Event.MarkAsReadClicked)
            runCurrent()
            assertEquals(2, requests.posts.size)
            viewModel.onEvent(ConversationContract.Event.Hidden)
        }

    @Test
    fun `opening, polling and the list never send a command`() = runTest(testDispatcher) {
        val requests = Requests()
        val harness = messagesHarness(engine(requests), listOf(testAccount(1)), activeIndex = 1)
        val conversation = conversation(harness, hadUnread = true)
        val list = list(harness)

        conversation.onEvent(ConversationContract.Event.Shown)
        list.onEvent(MessagesContract.Event.Shown)
        runCurrent()
        advanceTimeBy(PollInterval * 2 + 1.seconds)
        runCurrent()

        assertTrue(requests.all.any { it.second.endsWith("/messages") })
        assertTrue(requests.all.any { it.second.endsWith("/threads") })
        assertEquals(emptyList(), requests.posts)
        conversation.onEvent(ConversationContract.Event.Hidden)
        list.onEvent(MessagesContract.Event.Hidden)
    }

    @Test
    fun `marking a thread read clears its list row and the badge, and leaves the other rows`() =
        runTest(testDispatcher) {
            val requests = Requests()
            val harness = messagesHarness(engine(requests), listOf(testAccount(1)), activeIndex = 1)
            val list = list(harness)
            list.onEvent(MessagesContract.Event.Shown)
            runCurrent()
            assertEquals(listOf(2, 1), list.state.value.selectedPage!!.threads.map { it.unreadCount })
            assertEquals(3, harness.messagesRepository.unreadCount.value)

            val conversation = conversation(harness, hadUnread = true)
            conversation.onEvent(ConversationContract.Event.MarkAsReadClicked)
            runCurrent()

            assertEquals(listOf(0, 1), list.state.value.selectedPage!!.threads.map { it.unreadCount })
            assertEquals(1, harness.messagesRepository.unreadCount.value)
            list.onEvent(MessagesContract.Event.Hidden)
        }

    private fun conversation(harness: MessagesHarness, hadUnread: Boolean) = ConversationViewModel(
        keyLocalIndex = 1,
        threadId = 5,
        advertId = 10,
        title = null,
        imageUrl = null,
        status = null,
        isListingDeleted = false,
        hadUnread = hadUnread,
        repository = harness.messagesRepository,
        myAdvertsRepository = harness.myAdvertsRepository,
        accountRepository = harness.accountRepository,
        analytics = harness.analytics,
    )

    private fun list(harness: MessagesHarness) = MessagesViewModel(
        repository = harness.messagesRepository,
        myAdvertsRepository = harness.myAdvertsRepository,
        accountRepository = harness.accountRepository,
        analytics = harness.analytics,
    )
}
