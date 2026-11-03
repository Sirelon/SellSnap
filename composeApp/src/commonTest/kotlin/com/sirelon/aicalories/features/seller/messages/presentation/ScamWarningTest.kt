package com.sirelon.sellsnap.features.seller.messages.presentation

import com.sirelon.sellsnap.analytics.AnalyticsEvents
import com.sirelon.sellsnap.features.seller.messages.presentation.ConversationContract.Event
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
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
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * The scam warning in a conversation (SIR-176): which messages are flagged, and the analytics
 * that say how often it shows and whether sellers still reply.
 */
class ScamWarningTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val scamText =
        "Ваш товар придбано з OLX Доставкою. Підтвердіть протягом 3 годин: https://t.me/olx_pay_bot"

    private class FakeThread(var messages: List<String>)

    private fun engine(thread: FakeThread): MockEngine = mockEngine(testDispatcher) {
        addHandler { request ->
            val path = request.url.encodedPath
            when {
                request.method == HttpMethod.Post -> respond("", HttpStatusCode.OK, jsonHeaders())
                path.endsWith("/messages") ->
                    respond(messagesJson(*thread.messages.toTypedArray()), HttpStatusCode.OK, jsonHeaders())
                else -> respond(advertJson(10), HttpStatusCode.OK, jsonHeaders())
            }
        }
    }

    private suspend fun conversation(thread: FakeThread): Pair<ConversationViewModel, MessagesHarness> {
        val harness = messagesHarness(engine(thread), listOf(testAccount(1)), activeIndex = 1)
        val viewModel = ConversationViewModel(
            keyLocalIndex = 1,
            threadId = 5,
            advertId = 10,
            title = null,
            imageUrl = null,
            status = null,
            isListingDeleted = false,
            hadUnread = false,
            repository = harness.messagesRepository,
            myAdvertsRepository = harness.myAdvertsRepository,
            outcomeStore = harness.outcomeStore,
            accountRepository = harness.accountRepository,
            analytics = harness.analytics,
        )
        return viewModel to harness
    }

    private fun MessagesHarness.eventsOf(name: String) = analytics.events.filter { it.first == name }.map { it.second }

    @Test
    fun `only a received message that matches a rule is flagged`() = runTest(testDispatcher) {
        // The seller's own message repeats the scam text: it is never checked.
        val thread = FakeThread(
            listOf(
                messageJson(1, "received", "Is it still available?"),
                messageJson(2, "received", scamText),
                messageJson(3, "sent", scamText),
            ),
        )
        val (viewModel, _) = conversation(thread)

        viewModel.onEvent(Event.Shown)
        runCurrent()

        assertEquals(setOf(2L), viewModel.state.value.flaggedMessageIds)
        viewModel.onEvent(Event.Hidden)
    }

    @Test
    fun `the event fires once per open with the sorted signals and a bucketed count, polls do not repeat it`() =
        runTest(testDispatcher) {
            val thread = FakeThread(
                listOf(
                    messageJson(1, "received", scamText),
                    messageJson(2, "received", "Enter the CVV on https://bit.ly/abc123"),
                ),
            )
            val (viewModel, harness) = conversation(thread)

            viewModel.onEvent(Event.Shown)
            runCurrent()
            advanceTimeBy(PollInterval * 2 + 1.seconds)
            runCurrent()

            val logged = harness.eventsOf(AnalyticsEvents.MESSAGES_SCAM_WARNING_SHOWN).single()
            assertEquals(
                mapOf<String, Any>(
                    "signals" to "card_request,fake_olx_delivery,messenger_link,short_link",
                    "flagged_count_bucket" to "2_5",
                ),
                logged,
            )
            viewModel.onEvent(Event.Hidden)
        }

    @Test
    fun `a clean conversation logs nothing`() = runTest(testDispatcher) {
        val thread = FakeThread(listOf(messageJson(1, "received", "Can you do 1400?")))
        val (viewModel, harness) = conversation(thread)

        viewModel.onEvent(Event.Shown)
        runCurrent()

        assertEquals(emptySet(), viewModel.state.value.flaggedMessageIds)
        assertEquals(emptyList(), harness.eventsOf(AnalyticsEvents.MESSAGES_SCAM_WARNING_SHOWN))
        viewModel.onEvent(Event.Hidden)
    }

    @Test
    fun `message_sent says whether a warning was on screen`() = runTest(testDispatcher) {
        val flagged = FakeThread(listOf(messageJson(1, "received", scamText)))
        val (flaggedModel, flaggedHarness) = conversation(flagged)
        flaggedModel.onEvent(Event.Shown)
        runCurrent()
        flaggedModel.onEvent(Event.DraftChanged("Placeholder reply"))
        flaggedModel.onEvent(Event.SendClicked(isTwoPane = false))
        runCurrent()
        assertEquals(true, flaggedHarness.eventsOf(AnalyticsEvents.MESSAGE_SENT).single()["had_scam_warning"])
        flaggedModel.onEvent(Event.Hidden)

        val clean = FakeThread(listOf(messageJson(1, "received", "Is it still available?")))
        val (cleanModel, cleanHarness) = conversation(clean)
        cleanModel.onEvent(Event.Shown)
        runCurrent()
        cleanModel.onEvent(Event.DraftChanged("Placeholder reply"))
        cleanModel.onEvent(Event.SendClicked(isTwoPane = false))
        runCurrent()
        assertEquals(false, cleanHarness.eventsOf(AnalyticsEvents.MESSAGE_SENT).single()["had_scam_warning"])
        cleanModel.onEvent(Event.Hidden)
    }

    @Test
    fun `a flagged message that arrives on a later poll is flagged and logged once`() = runTest(testDispatcher) {
        val thread = FakeThread(listOf(messageJson(1, "received", "Can you do 1400?")))
        val (viewModel, harness) = conversation(thread)
        viewModel.onEvent(Event.Shown)
        runCurrent()
        assertTrue(viewModel.state.value.flaggedMessageIds.isEmpty())

        thread.messages = thread.messages + messageJson(2, "received", scamText)
        advanceTimeBy(PollInterval + 1.seconds)
        runCurrent()

        assertEquals(setOf(2L), viewModel.state.value.flaggedMessageIds)
        assertEquals(1, harness.eventsOf(AnalyticsEvents.MESSAGES_SCAM_WARNING_SHOWN).size)
        viewModel.onEvent(Event.Hidden)
    }
}
