package com.sirelon.sellsnap.telegram

import com.sirelon.sellsnap.fakes.FakeImageStore
import com.sirelon.sellsnap.fakes.FakeTelegramApi
import com.sirelon.sellsnap.fakes.FakeThreadsApi
import com.sirelon.sellsnap.fakes.InMemorySocialStore
import com.sirelon.sellsnap.fakes.MutableClock
import com.sirelon.sellsnap.fakes.testAccount
import com.sirelon.sellsnap.model.PostStatus
import com.sirelon.sellsnap.model.SocialPost
import com.sirelon.sellsnap.threads.Me
import com.sirelon.sellsnap.threads.Publisher
import com.sirelon.sellsnap.threads.TokenLifecycle
import java.time.Instant

/** The whole Telegram side on fakes, with one staging account that holds a Threads token. */
class BotFixture {
    val clock = MutableClock(NOW)
    val store = InMemorySocialStore()
    val telegram = FakeTelegramApi()
    val threads = FakeThreadsApi(store)
    val images = FakeImageStore()
    val previews = Previews(images, BASE_URL)
    val sender = PreviewSender(telegram, previews, store, OWNER)
    val approvals = ApprovalActions(store, telegram, threads, previews, sender, clock, OWNER)
    val publisher = Publisher(store, threads, images, clock, BASE_URL)
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
    ): Message {
        val id = nextIncomingMessageId++
        val message = Message(
            messageId = id,
            chat = Chat(OWNER),
            from = User(from),
            text = text.takeIf { photoFileId == null },
            caption = text.takeIf { photoFileId != null },
            photo = listOfNotNull(photoFileId?.let { PhotoSize(it, 640, 480) }),
            replyToMessage = replyTo?.let { Message(it, Chat(OWNER)) },
        )
        handler.handle(Update(updateId, message = message))
        return message
    }

    fun putPending(id: String = "stt0001", scheduledAt: Instant? = NOW.plusSeconds(3600), telegramMessageId: Long? = 900): SocialPost =
        com.sirelon.sellsnap.fakes.testPost(id = id, status = PostStatus.Pending, scheduledAt = scheduledAt)
            .copy(telegramMessageId = telegramMessageId)
            .also(store::put)

    companion object {
        const val OWNER = 42L
        const val BASE_URL = "https://go.test"

        /** A Monday, 08:00 in Kyiv. */
        val NOW: Instant = Instant.parse("2026-10-12T05:00:00Z")
    }
}
