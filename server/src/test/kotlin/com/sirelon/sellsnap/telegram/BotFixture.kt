package com.sirelon.sellsnap.telegram

import com.sirelon.sellsnap.brand.BrandContext
import com.sirelon.sellsnap.brand.ReleaseChange
import com.sirelon.sellsnap.brand.ReleaseNotes
import com.sirelon.sellsnap.draft.DraftChecker
import com.sirelon.sellsnap.draft.DraftPipeline
import com.sirelon.sellsnap.draft.Drafter
import com.sirelon.sellsnap.fakes.FakeImageStore
import com.sirelon.sellsnap.fakes.FakeLlmClient
import com.sirelon.sellsnap.fakes.FakeReleaseNotesSource
import com.sirelon.sellsnap.fakes.FakeTelegramApi
import com.sirelon.sellsnap.fakes.FakeThreadsApi
import com.sirelon.sellsnap.fakes.InMemorySocialStore
import com.sirelon.sellsnap.fakes.MutableClock
import com.sirelon.sellsnap.fakes.testAccount
import com.sirelon.sellsnap.llm.LlmResult
import com.sirelon.sellsnap.model.PostSource
import com.sirelon.sellsnap.model.PostStatus
import com.sirelon.sellsnap.model.SocialPost
import com.sirelon.sellsnap.spend.UsageTracker
import com.sirelon.sellsnap.threads.Me
import com.sirelon.sellsnap.threads.Publisher
import com.sirelon.sellsnap.threads.TokenLifecycle
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

/** The whole Telegram side on fakes, with one staging account that holds a Threads token. */
class BotFixture(val llm: FakeLlmClient = FakeLlmClient()) {
    val clock = MutableClock(NOW)
    val store = InMemorySocialStore()
    val telegram = FakeTelegramApi()
    val threads = FakeThreadsApi(store)
    val images = FakeImageStore()
    val releases = FakeReleaseNotesSource(listOf(RELEASE))
    val usage = UsageTracker(store, clock, dailyCallLimit = 200)
    private val brand = BrandContext.load()
    val pipeline = DraftPipeline(
        Drafter(llm, usage, brand, DRAFT_MODEL),
        DraftChecker(llm, usage, brand, CHECK_MODEL),
    )
    val previews = Previews(images, LINK_TEMPLATE, regenerateAvailable = true)
    val sender = PreviewSender(telegram, previews, store, OWNER)
    val regenerator = Regenerator(store, telegram, pipeline, releases, previews, sender, clock, OWNER)
    val approvals = ApprovalActions(store, telegram, threads, previews, sender, clock, OWNER, regenerator)
    val publisher = Publisher(store, threads, images, clock, LINK_TEMPLATE)
    val tokens = TokenLifecycle(store, threads, clock)
    val commands = Commands(store, telegram, images, publisher, tokens, previews, sender, approvals, clock, OWNER)
    val handler = BotUpdateHandler(store, telegram, approvals, commands, clock, OWNER)

    private var nextUpdateId = 1L
    private var nextIncomingMessageId = 500L

    init {
        store.put(testAccount().copy(token = "stored-token", threadsUserId = "threads-user"))
        threads.me = Me(id = "threads-user", username = "sellsnap.staging")
    }

    fun account(id: String = "staging") = store.allAccounts.getValue(id)

    fun post(id: String) = store.allPosts.getValue(id)

    fun status(id: String): PostStatus = post(id).status

    /** Every text the bot sent or edited, for secret checks. */
    fun everythingSaid(): String = telegram.calls.joinToString("\n") { call ->
        when (call) {
            is FakeTelegramApi.Call.Sent -> call.text.orEmpty() + call.markup
            is FakeTelegramApi.Call.Edited -> call.text.orEmpty() + call.markup
            is FakeTelegramApi.Call.Answered -> call.text.orEmpty()
            is FakeTelegramApi.Call.Deleted -> ""
        }
    }

    fun sentTexts(): List<String> = telegram.callsOf<FakeTelegramApi.Call.Sent>().map { it.text.orEmpty() }

    fun answers(): List<String?> = telegram.callsOf<FakeTelegramApi.Call.Answered>().map { it.text }

    fun keyboardOf(call: FakeTelegramApi.Call.Sent): List<String> =
        (call.markup as? InlineKeyboardMarkup)?.inlineKeyboard.orEmpty().flatten().map { it.text }

    suspend fun tap(data: String, from: Long = OWNER, updateId: Long = nextUpdateId++, messageId: Long = 900) {
        val query = CallbackQuery(
            id = "cb-$updateId",
            from = User(from),
            message = Message(messageId, Chat(OWNER)),
            data = data,
        )
        handler.handle(Update(updateId, callbackQuery = query))
    }

    suspend fun say(
        text: String,
        from: Long = OWNER,
        updateId: Long = nextUpdateId++,
        replyTo: Long? = null,
        photoFileId: String? = null,
        document: Document? = null,
    ): Message {
        val id = nextIncomingMessageId++
        val message = Message(
            messageId = id,
            chat = Chat(OWNER),
            from = User(from),
            text = text.takeIf { photoFileId == null && document == null },
            caption = text.takeIf { photoFileId != null || document != null },
            photo = listOfNotNull(photoFileId?.let { PhotoSize(it, 640, 480) }),
            document = document,
            replyToMessage = replyTo?.let { Message(it, Chat(OWNER)) },
        )
        handler.handle(Update(updateId, message = message))
        return message
    }

    /** A photo or file with no caption at all. */
    suspend fun sendBareImage(photoFileId: String? = null, document: Document? = null) {
        val message = Message(
            messageId = nextIncomingMessageId++,
            chat = Chat(OWNER),
            from = User(OWNER),
            photo = listOfNotNull(photoFileId?.let { PhotoSize(it, 640, 480) }),
            document = document,
        )
        handler.handle(Update(nextUpdateId++, message = message))
    }

    fun putPending(id: String = "stt0001", scheduledAt: Instant? = NOW.plusSeconds(3600), telegramMessageId: Long? = 900): SocialPost =
        com.sirelon.sellsnap.fakes.testPost(id = id, status = PostStatus.Pending, scheduledAt = scheduledAt)
            .copy(telegramMessageId = telegramMessageId)
            .also(store::put)

    /** A pending post that the drafter wrote, with the preview message 900. */
    fun putDraft(
        id: String = "stt0001",
        scheduledAt: Instant? = NOW.plusSeconds(3600),
        change: (SocialPost) -> SocialPost = { it },
    ): SocialPost =
        com.sirelon.sellsnap.fakes.testPost(id = id, status = PostStatus.Pending, scheduledAt = scheduledAt)
            .copy(source = PostSource.Drafter, text = "Old draft text", telegramMessageId = 900)
            .let(change)
            .also(store::put)

    /** The drafter's JSON answer, as the fake model returns it. */
    fun draftReply(text: String, replyText: String? = null): LlmResult = FakeLlmClient.reply(
        DRAFT_MODEL,
        buildJsonObject {
            put("text", text)
            if (replyText != null) put("replyText", replyText)
            put("claimedFeatureIds", JsonArray(emptyList()))
        }.toString(),
    )

    /** The checker's JSON answer; a clean pass unless [findings] or [naturalnessOk] say otherwise. */
    fun checkReply(
        language: String = "uk",
        findings: List<String> = emptyList(),
        backTranslation: String? = null,
        naturalnessOk: Boolean? = null,
    ): LlmResult = FakeLlmClient.reply(
        CHECK_MODEL,
        buildJsonObject {
            put("detectedLanguage", language)
            put("unsupportedClaims", JsonArray(emptyList()))
            put("guardrailFindings", JsonArray(findings.map(::JsonPrimitive)))
            put("backTranslationEn", backTranslation?.let(::JsonPrimitive) ?: JsonNull)
            put("naturalnessOk", naturalnessOk?.let(::JsonPrimitive) ?: JsonNull)
            put("naturalnessIssues", JsonArray(emptyList()))
        }.toString(),
    )

    companion object {
        const val DRAFT_MODEL = "draft-model"
        const val CHECK_MODEL = "check-model"
        const val OWNER = 42L

        val RELEASE = ReleaseNotes(
            version = "3.3",
            date = "2026-10-12",
            changes = listOf(
                ReleaseChange(
                    id = "drafts",
                    title = mapOf("en" to "Drafts", "uk" to "Чернетки", "pl" to "Szkice"),
                    summary = mapOf(
                        "en" to "Unfinished listings are kept.",
                        "uk" to "Незавершені оголошення зберігаються.",
                        "pl" to "Niedokończone ogłoszenia są zapisywane.",
                    ),
                ),
            ),
        )

        const val LINK_TEMPLATE = "https://go.test/go/{code}"

        /** A Monday, 08:00 in Kyiv. */
        val NOW: Instant = Instant.parse("2026-10-12T05:00:00Z")
    }
}
