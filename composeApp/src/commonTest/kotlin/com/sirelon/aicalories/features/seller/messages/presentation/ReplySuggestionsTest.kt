package com.sirelon.sellsnap.features.seller.messages.presentation

import com.sirelon.sellsnap.features.seller.openai.REPLY_SUGGESTIONS_MODEL_ID
import com.sirelon.sellsnap.analytics.AnalyticsEvents
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountRecord
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountState
import com.sirelon.sellsnap.features.seller.messages.presentation.ConversationContract.Event
import com.sirelon.sellsnap.features.seller.messages.presentation.ConversationContract.PendingStatus
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * "Suggest replies" in a conversation (SIR-96): when the button is offered, what the model is
 * sent, which chip sends and which goes into the field, the price-floor guard, and how a failed
 * request leaves the screen. OpenAI is a mock on the same engine as OLX.
 */
class ReplySuggestionsTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** A fake OLX thread and a fake model. */
    private class Fakes {
        var messages = listOf(messageJson(1, "received", "Is it still available?"))
        var postStatus = HttpStatusCode.OK
        val posts = mutableListOf<String>()
        val openAIBodies = mutableListOf<String>()
        var openAIStatus = HttpStatusCode.OK
        var openAIDelaySeconds = 0
        /** The model's own text; wrapped into a Responses API body unless [openAIStatus] is an error. */
        var modelOutput = DefaultReplies
    }

    private fun engine(fakes: Fakes): MockEngine = mockEngine(testDispatcher) {
        addHandler { request ->
            val path = request.url.encodedPath
            when {
                path.endsWith("/responses") -> {
                    fakes.openAIBodies += request.body.toByteArray().decodeToString()
                    if (fakes.openAIDelaySeconds > 0) delay(fakes.openAIDelaySeconds.seconds)
                    if (fakes.openAIStatus != HttpStatusCode.OK) {
                        respond(
                            """{"error":{"message":"Placeholder failure","type":"server_error"}}""",
                            fakes.openAIStatus,
                            jsonHeaders(),
                        )
                    } else {
                        respond(responsesBody(fakes.modelOutput), HttpStatusCode.OK, jsonHeaders())
                    }
                }
                request.method == HttpMethod.Post && path.endsWith("/messages") -> {
                    fakes.posts += (request.body as TextContent).text
                    respond("", fakes.postStatus, jsonHeaders())
                }
                path.endsWith("/messages") ->
                    respond(messagesJson(*fakes.messages.toTypedArray()), HttpStatusCode.OK, jsonHeaders())
                else -> respond(ListingJson, HttpStatusCode.OK, jsonHeaders())
            }
        }
    }

    private suspend fun conversation(
        fakes: Fakes,
        accounts: List<OlxAccountRecord> = listOf(testAccount(1)),
        suggestionsSupported: Boolean = true,
        withFloor: Boolean = true,
    ): Pair<ConversationViewModel, MessagesHarness> {
        val harness = messagesHarness(engine(fakes), accounts, activeIndex = 1)
        if (withFloor) {
            harness.outcomeStore.recordPublished(10, 2500, 2000, 3000, publishedPrice = 2500, currency = "UAH")
        }
        val viewModel = ConversationViewModel(
            keyLocalIndex = 1,
            threadId = 5,
            advertId = 10,
            title = "Placeholder row title",
            imageUrl = null,
            status = null,
            isListingDeleted = false,
            hadUnread = false,
            repository = harness.messagesRepository,
            myAdvertsRepository = harness.myAdvertsRepository,
            outcomeStore = harness.outcomeStore,
            accountRepository = harness.accountRepository,
            analytics = harness.analytics,
            openAIClient = harness.openAIClient,
            suggestionsSupported = suggestionsSupported,
            timeSource = testDispatcher.scheduler.timeSource,
        )
        return viewModel to harness
    }

    private fun MessagesHarness.eventsOf(name: String) = analytics.events.filter { it.first == name }.map { it.second }

    private fun ConversationViewModel.open() {
        onEvent(Event.Shown)
    }

    // Button visibility.

    @Test
    fun `the button is offered when the buyer wrote last`() = runTest(testDispatcher) {
        val (viewModel, _) = conversation(Fakes())
        viewModel.open()
        runCurrent()

        assertTrue(viewModel.state.value.canSuggestReplies)
        viewModel.onEvent(Event.Hidden)
    }

    @Test
    fun `no button when the seller wrote last`() = runTest(testDispatcher) {
        val fakes = Fakes()
        fakes.messages = fakes.messages + messageJson(2, "sent", "Yes")
        val (viewModel, _) = conversation(fakes)
        viewModel.open()
        runCurrent()

        assertFalse(viewModel.state.value.canSuggestReplies)
        viewModel.onEvent(Event.Hidden)
    }

    @Test
    fun `no button for a scam-flagged buyer message`() = runTest(testDispatcher) {
        val fakes = Fakes()
        fakes.messages = listOf(messageJson(1, "received", "Enter the CVV on https://bit.ly/abc123"))
        val (viewModel, _) = conversation(fakes)
        viewModel.open()
        runCurrent()

        assertEquals(setOf(1L), viewModel.state.value.flaggedMessageIds)
        assertFalse(viewModel.state.value.canSuggestReplies)
        viewModel.onEvent(Event.Hidden)
    }

    @Test
    fun `no button while the field has text`() = runTest(testDispatcher) {
        val (viewModel, _) = conversation(Fakes())
        viewModel.open()
        runCurrent()

        viewModel.onEvent(Event.DraftChanged("Placeholder"))
        assertFalse(viewModel.state.value.canSuggestReplies)
        viewModel.onEvent(Event.DraftChanged(" "))
        assertTrue(viewModel.state.value.canSuggestReplies)
        viewModel.onEvent(Event.Hidden)
    }

    @Test
    fun `no button while the account needs a reconnect`() = runTest(testDispatcher) {
        val (viewModel, _) = conversation(
            Fakes(),
            accounts = listOf(testAccount(1, state = OlxAccountState.NeedsReconnect)),
        )
        viewModel.open()
        runCurrent()

        assertFalse(viewModel.state.value.canSuggestReplies)
        viewModel.onEvent(Event.Hidden)
    }

    @Test
    fun `no button where the platform cannot reach the model`() = runTest(testDispatcher) {
        val (viewModel, _) = conversation(Fakes(), suggestionsSupported = false)
        viewModel.open()
        runCurrent()

        assertFalse(viewModel.state.value.canSuggestReplies)
        viewModel.onEvent(Event.Hidden)
    }

    // The request.

    @Test
    fun `a tap makes one call that carries the listing, the floor and the language, and shows three chips`() =
        runTest(testDispatcher) {
            val fakes = Fakes()
            val (viewModel, harness) = conversation(fakes)
            viewModel.open()
            runCurrent()

            viewModel.onEvent(Event.SuggestRepliesClicked(isTwoPane = false))
            assertTrue(viewModel.state.value.isSuggesting)
            assertFalse(viewModel.state.value.canSuggestReplies)
            runCurrent()

            val body = fakes.openAIBodies.single()
            assertTrue(body.contains("\"model\":\"$REPLY_SUGGESTIONS_MODEL_ID\""), body)
            assertTrue(body.contains("\"store\":false"), body)
            assertTrue(body.contains("\"text\":{\"format\":{\"type\":\"json_object\"}}"), body)
            assertTrue(body.contains("Placeholder lamp"), body)
            assertTrue(body.contains("Placeholder description"), body)
            assertTrue(body.contains("- state: used"), body)
            assertTrue(body.contains("- year: 2019"), body)
            assertTrue(body.contains("lowest acceptable price is 2000 UAH"), body)
            assertTrue(body.contains("asking price is 2500 UAH"), body)
            assertTrue(body.contains("Write in Ukrainian"), body)
            assertTrue(body.contains("Buyer: Is it still available?"), body)

            val state = viewModel.state.value
            assertFalse(state.isSuggesting)
            assertEquals(3, state.suggestions.size)
            assertTrue(state.showsSuggestions)
            assertEquals(
                mapOf<String, Any>("layout" to "single_pane", "has_listing_text" to true, "has_price_floor" to true),
                harness.eventsOf(AnalyticsEvents.SUGGESTIONS_REQUESTED).single(),
            )
            assertEquals(
                mapOf<String, Any>(
                    "count" to 3,
                    "latency_ms_bucket" to "lt_1000",
                    "dropped_below_floor" to 0,
                    "needs_review_count" to 1,
                    "prompt_version" to "v1",
                    "model" to REPLY_SUGGESTIONS_MODEL_ID,
                ),
                harness.eventsOf(AnalyticsEvents.SUGGESTIONS_SHOWN).single(),
            )
            viewModel.onEvent(Event.Hidden)
        }

    @Test
    fun `dismiss hides the chips, and a second tap for the same buyer message calls nothing`() =
        runTest(testDispatcher) {
            val fakes = Fakes()
            val (viewModel, harness) = conversation(fakes)
            viewModel.open()
            runCurrent()
            viewModel.onEvent(Event.SuggestRepliesClicked(isTwoPane = true))
            runCurrent()

            viewModel.onEvent(Event.SuggestionsDismissed)
            assertTrue(viewModel.state.value.suggestions.isEmpty())
            assertEquals(mapOf<String, Any>("count" to 3), harness.eventsOf(AnalyticsEvents.SUGGESTIONS_DISMISSED).single())
            assertTrue(viewModel.state.value.canSuggestReplies)

            viewModel.onEvent(Event.SuggestRepliesClicked(isTwoPane = true))
            runCurrent()

            assertEquals(1, fakes.openAIBodies.size)
            assertEquals(3, viewModel.state.value.suggestions.size)
            assertEquals(1, harness.eventsOf(AnalyticsEvents.SUGGESTIONS_REQUESTED).size)
            viewModel.onEvent(Event.Hidden)
        }

    @Test
    fun `a new buyer message drops the kept suggestions and allows a new call`() = runTest(testDispatcher) {
        val fakes = Fakes()
        val (viewModel, _) = conversation(fakes)
        viewModel.open()
        runCurrent()
        viewModel.onEvent(Event.SuggestRepliesClicked(isTwoPane = false))
        runCurrent()
        assertEquals(3, viewModel.state.value.suggestions.size)

        fakes.messages = fakes.messages + messageJson(2, "received", "Would you take 2200?")
        advanceTimeBy(PollInterval + 1.seconds)
        runCurrent()

        assertTrue(viewModel.state.value.suggestions.isEmpty())
        assertTrue(viewModel.state.value.canSuggestReplies)
        viewModel.onEvent(Event.SuggestRepliesClicked(isTwoPane = false))
        runCurrent()

        assertEquals(2, fakes.openAIBodies.size)
        assertTrue(fakes.openAIBodies.last().contains("Buyer: Would you take 2200?"))
        viewModel.onEvent(Event.Hidden)
    }

    // Tapping a chip.

    @Test
    fun `a chip that fits and needs no review is sent as a suggestion`() = runTest(testDispatcher) {
        val fakes = Fakes()
        val (viewModel, harness) = conversation(fakes)
        viewModel.open()
        runCurrent()
        viewModel.onEvent(Event.SuggestRepliesClicked(isTwoPane = false))
        runCurrent()

        viewModel.onEvent(Event.SuggestionClicked(index = 1, isTruncated = false, isTwoPane = false))
        assertTrue(viewModel.state.value.suggestions.isEmpty())
        runCurrent()

        assertEquals("""{"text":"Yes, it is. Would you like to see more photos?"}""", fakes.posts.single())
        val sent = harness.eventsOf(AnalyticsEvents.MESSAGE_SENT).single()
        assertEquals("suggestion", sent["source"])
        assertEquals(1, sent["suggestion_index"])
        assertFalse(sent.containsKey("suggestion_edited"))
        assertTrue(harness.eventsOf(AnalyticsEvents.SUGGESTION_INSERTED).isEmpty())
        viewModel.onEvent(Event.Hidden)
    }

    @Test
    fun `a truncated chip goes into the field, and Send reports an unedited suggestion`() = runTest(testDispatcher) {
        val fakes = Fakes()
        val (viewModel, harness) = conversation(fakes)
        viewModel.open()
        runCurrent()
        viewModel.onEvent(Event.SuggestRepliesClicked(isTwoPane = false))
        runCurrent()

        viewModel.onEvent(Event.SuggestionClicked(index = 0, isTruncated = true, isTwoPane = false))
        runCurrent()

        assertTrue(fakes.posts.isEmpty())
        assertEquals("Yes, it is still available.", viewModel.state.value.draft)
        assertFalse(viewModel.state.value.showsSuggestions)
        assertEquals(
            mapOf<String, Any>("suggestion_index" to 0, "reason" to "truncated"),
            harness.eventsOf(AnalyticsEvents.SUGGESTION_INSERTED).single(),
        )

        viewModel.onEvent(Event.SendClicked(isTwoPane = false))
        runCurrent()

        assertEquals(1, fakes.posts.size)
        val sent = harness.eventsOf(AnalyticsEvents.MESSAGE_SENT).single()
        assertEquals("edited_suggestion", sent["source"])
        assertEquals(0, sent["suggestion_index"])
        assertEquals(false, sent["suggestion_edited"])
        viewModel.onEvent(Event.Hidden)
    }

    @Test
    fun `a needs-review chip goes into the field even when it fits, and an edit is reported`() =
        runTest(testDispatcher) {
            val fakes = Fakes()
            val (viewModel, harness) = conversation(fakes)
            viewModel.open()
            runCurrent()
            viewModel.onEvent(Event.SuggestRepliesClicked(isTwoPane = false))
            runCurrent()

            viewModel.onEvent(Event.SuggestionClicked(index = 2, isTruncated = false, isTwoPane = false))
            runCurrent()
            assertTrue(fakes.posts.isEmpty())
            assertEquals("needs_review", harness.eventsOf(AnalyticsEvents.SUGGESTION_INSERTED).single()["reason"])

            viewModel.onEvent(Event.DraftChanged("Yes, I can send it by post next week."))
            viewModel.onEvent(Event.SendClicked(isTwoPane = false))
            runCurrent()

            val sent = harness.eventsOf(AnalyticsEvents.MESSAGE_SENT).single()
            assertEquals("edited_suggestion", sent["source"])
            assertEquals(2, sent["suggestion_index"])
            assertEquals(true, sent["suggestion_edited"])
            viewModel.onEvent(Event.Hidden)
        }

    @Test
    fun `clearing the field drops the link to the inserted suggestion`() = runTest(testDispatcher) {
        val fakes = Fakes()
        val (viewModel, harness) = conversation(fakes)
        viewModel.open()
        runCurrent()
        viewModel.onEvent(Event.SuggestRepliesClicked(isTwoPane = false))
        runCurrent()

        viewModel.onEvent(Event.SuggestionClicked(index = 0, isTruncated = true, isTwoPane = false))
        viewModel.onEvent(Event.DraftChanged(""))
        assertTrue(viewModel.state.value.showsSuggestions)
        viewModel.onEvent(Event.DraftChanged("Placeholder typed reply"))
        viewModel.onEvent(Event.SendClicked(isTwoPane = false))
        runCurrent()

        val sent = harness.eventsOf(AnalyticsEvents.MESSAGE_SENT).single()
        assertEquals("typed", sent["source"])
        assertFalse(sent.containsKey("suggestion_index"))
        viewModel.onEvent(Event.Hidden)
    }

    @Test
    fun `Retry of a failed suggestion keeps its source`() = runTest(testDispatcher) {
        val fakes = Fakes()
        fakes.postStatus = HttpStatusCode.InternalServerError
        val (viewModel, harness) = conversation(fakes)
        viewModel.open()
        runCurrent()
        viewModel.onEvent(Event.SuggestRepliesClicked(isTwoPane = false))
        runCurrent()

        viewModel.onEvent(Event.SuggestionClicked(index = 0, isTruncated = false, isTwoPane = false))
        runCurrent()
        val failed = viewModel.state.value.pendingMessages.single()
        assertEquals(PendingStatus.Failed, failed.status)

        fakes.postStatus = HttpStatusCode.OK
        viewModel.onEvent(Event.RetrySendClicked(failed.localId, isTwoPane = false))
        runCurrent()

        val sent = harness.eventsOf(AnalyticsEvents.MESSAGE_SENT)
        assertEquals(listOf("failed", "success"), sent.map { it["result"] })
        assertEquals(listOf<Any?>("suggestion", "suggestion"), sent.map { it["source"] })
        assertEquals(listOf<Any?>(0, 0), sent.map { it["suggestion_index"] })
        assertEquals(true, sent.last()["is_retry"])
        viewModel.onEvent(Event.Hidden)
    }

    // The price floor.

    @Test
    fun `a suggestion below the floor is dropped and counted`() = runTest(testDispatcher) {
        val fakes = Fakes()
        fakes.modelOutput = """{"replies":[""" +
            """{"text":"I can do 1 800.","needs_review":false,"price":null},""" +
            """{"text":"I can do 2100.","needs_review":false,"price":2100},""" +
            """{"text":"Fine, 1500 then.","needs_review":false,"price":1500}]}"""
        val (viewModel, harness) = conversation(fakes)
        viewModel.open()
        runCurrent()
        viewModel.onEvent(Event.SuggestRepliesClicked(isTwoPane = false))
        runCurrent()

        assertEquals(listOf("I can do 2100."), viewModel.state.value.suggestions.map { it.text })
        assertEquals(2, harness.eventsOf(AnalyticsEvents.SUGGESTIONS_SHOWN).single()["dropped_below_floor"])
        viewModel.onEvent(Event.Hidden)
    }

    @Test
    fun `without a floor the prompt holds the asking price and nothing is dropped`() = runTest(testDispatcher) {
        val fakes = Fakes()
        fakes.modelOutput = """{"replies":[{"text":"I can do 1500.","needs_review":false,"price":1500}]}"""
        val (viewModel, harness) = conversation(fakes, withFloor = false)
        viewModel.open()
        runCurrent()
        viewModel.onEvent(Event.SuggestRepliesClicked(isTwoPane = false))
        runCurrent()

        assertTrue(fakes.openAIBodies.single().contains("below the asking price"))
        assertEquals(1, viewModel.state.value.suggestions.size)
        assertEquals(false, harness.eventsOf(AnalyticsEvents.SUGGESTIONS_REQUESTED).single()["has_price_floor"])
        viewModel.onEvent(Event.Hidden)
    }

    // Failures.

    @Test
    fun `an OpenAI 500 shows nothing, logs the status and leaves the input working`() = runTest(testDispatcher) {
        val fakes = Fakes()
        fakes.openAIStatus = HttpStatusCode.InternalServerError
        assertFailureShowsNothing(fakes, expectedReason = "openai_500")
    }

    @Test
    fun `no answer within 15 seconds is a timeout`() = runTest(testDispatcher) {
        val fakes = Fakes()
        fakes.openAIDelaySeconds = 20
        val (viewModel, harness) = conversation(fakes)
        viewModel.open()
        runCurrent()
        viewModel.onEvent(Event.SuggestRepliesClicked(isTwoPane = false))
        runCurrent()
        assertTrue(viewModel.state.value.isSuggesting)

        advanceTimeBy(16.seconds)
        runCurrent()

        assertFalse(viewModel.state.value.isSuggesting)
        assertTrue(viewModel.state.value.suggestions.isEmpty())
        assertTrue(viewModel.state.value.canSuggestReplies)
        val failed = harness.eventsOf(AnalyticsEvents.SUGGESTIONS_FAILED).single()
        assertEquals("timeout", failed["reason"])
        assertEquals("gt_8000", failed["latency_ms_bucket"])
        assertTrue(harness.eventsOf(AnalyticsEvents.ERROR_SHOWN).isEmpty())
        viewModel.onEvent(Event.Hidden)
    }

    @Test
    fun `an answer that is not JSON is a parse failure`() = runTest(testDispatcher) {
        val fakes = Fakes()
        fakes.modelOutput = "Sure! Here are some replies."
        assertFailureShowsNothing(fakes, expectedReason = "parse")
    }

    @Test
    fun `an answer with no replies is empty`() = runTest(testDispatcher) {
        val fakes = Fakes()
        fakes.modelOutput = """{"replies":[]}"""
        assertFailureShowsNothing(fakes, expectedReason = "empty")
    }

    private suspend fun kotlinx.coroutines.test.TestScope.assertFailureShowsNothing(fakes: Fakes, expectedReason: String) {
        val (viewModel, harness) = conversation(fakes)
        viewModel.open()
        runCurrent()
        viewModel.onEvent(Event.SuggestRepliesClicked(isTwoPane = false))
        runCurrent()

        val state = viewModel.state.value
        assertFalse(state.isSuggesting)
        assertTrue(state.suggestions.isEmpty())
        assertTrue(state.canSuggestReplies)
        assertEquals(expectedReason, harness.eventsOf(AnalyticsEvents.SUGGESTIONS_FAILED).single()["reason"])
        assertTrue(harness.eventsOf(AnalyticsEvents.ERROR_SHOWN).isEmpty())
        assertEquals(1, harness.analytics.exceptions.size)

        viewModel.onEvent(Event.DraftChanged("Placeholder typed reply"))
        viewModel.onEvent(Event.SendClicked(isTwoPane = false))
        runCurrent()
        assertEquals(1, fakes.posts.size)
        viewModel.onEvent(Event.Hidden)
    }

    private companion object {
        const val DefaultReplies = """{"replies":[""" +
            """{"text":"Yes, it is still available.","needs_review":false,"price":null},""" +
            """{"text":"Yes, it is. Would you like to see more photos?","needs_review":false,"price":null},""" +
            """{"text":"Yes. I can send it by post.","needs_review":true,"price":null}]}"""

        const val ListingJson = """{"data":{"id":10,"status":"active","title":"Placeholder lamp",""" +
            """"description":"Placeholder description","url":"https://www.olx.ua/d/10",""" +
            """"price":{"value":2500,"currency":"UAH"},""" +
            """"attributes":[{"code":"state","value":null,"values":["used"]},{"code":"year","value":2019},""" +
            """{"code":"empty","value":null,"values":null}],""" +
            """"images":[{"url":"https://example.invalid/10.jpg"}]}}"""

        fun responsesBody(modelOutput: String): String =
            """{"id":"resp_1","status":"completed","output":[{"id":"msg_1","type":"message","role":"assistant",""" +
                """"content":[{"type":"output_text","text":${Json.encodeToString(String.serializer(), modelOutput)}}]}]}"""
    }
}
