package com.sirelon.sellsnap.telegram

import com.sirelon.sellsnap.fakes.FakeTelegramApi
import com.sirelon.sellsnap.fakes.testPost
import com.sirelon.sellsnap.model.BatchAccount
import com.sirelon.sellsnap.model.EventType
import com.sirelon.sellsnap.model.Pillar
import com.sirelon.sellsnap.model.PostKind
import com.sirelon.sellsnap.model.PostSource
import com.sirelon.sellsnap.model.PostStatus
import com.sirelon.sellsnap.model.SocialBatch
import com.sirelon.sellsnap.telegram.BotFixture.Companion.NOW
import com.sirelon.sellsnap.threads.ThreadsFailure
import kotlinx.coroutines.test.runTest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CommandsTest {
    private val bot = BotFixture()

    // --- Parsing

    @Test
    fun parsesACommandWithOrWithoutABotSuffix() {
        assertEquals("post", parseCommand("/post staging hi")?.name)
        assertEquals("staging hi", parseCommand("/post@SellSnapBot staging hi")?.args)
        assertEquals("approval", parseCommand("/Approval")?.name)
        assertEquals("", parseCommand("/resume")?.args)
        assertNull(parseCommand("hello"))
    }

    @Test
    fun parsesPostArgumentsWithNowAndPollOptions() {
        val plain = parsePost("staging A tip")!!
        assertEquals("staging", plain.accountId)
        assertFalse(plain.now)
        assertEquals("A tip", plain.text)

        val now = parsePost("staging now Right now\nsecond line")!!
        assertTrue(now.now)
        assertEquals("Right now\nsecond line", now.text)

        val poll = parsePost("staging Which one?\n? Red\n? Blue")!!
        assertEquals("Which one?", poll.text)
        assertEquals(listOf("Red", "Blue"), poll.pollOptions)

        assertFalse(parsePost("staging nowhere to go")!!.now)
        assertNull(parsePost("   "))
    }

    @Test
    fun aLineWithOnlyThreeDashesSplitsTheTextFromTheReply() {
        val split = parsePost("ua now Main text\nsecond line\n---\nDownload: {link}\nmore")!!
        assertTrue(split.now)
        assertEquals("Main text\nsecond line", split.text)
        assertEquals("Download: {link}\nmore", split.replyText)

        assertNull(parsePost("ua Main text")!!.replyText)
        assertEquals("", parsePost("ua Main text\n---")!!.replyText)
        // Only an exact line splits; the first one wins and later ones belong to the reply.
        assertNull(parsePost("ua a --- b\n----")!!.replyText)
        val twice = parsePost("ua A\n---\nB\n---\nC")!!
        assertEquals("A", twice.text)
        assertEquals("B\n---\nC", twice.replyText)
        // Poll option lines count only before the split.
        val poll = parsePost("ua Which?\n? Red\n? Blue\n---\n? not an option")!!
        assertEquals(listOf("Red", "Blue"), poll.pollOptions)
        assertEquals("? not an option", poll.replyText)
    }

    @Test
    fun aReplyIsStoredOnTheNewPostAndShownInItsPreview() = runTest {
        bot.say("/post staging Check your photos.\n---\nDownload: {link}")

        val post = bot.post("stt0001")
        assertEquals("Check your photos.", post.text)
        assertEquals("Download: {link}", post.replyText)
        assertFalse(post.link, "the flag describes the text only")
        assertEquals(Pillar.Tip, post.pillar)
        assertEquals(PostStatus.Pending, post.status)
        val preview = bot.telegram.callsOf<FakeTelegramApi.Call.Sent>().single().text.orEmpty()
        assertTrue("Check your photos.\n\n↳ reply:\nDownload: https://go.test/go/stt0001" in preview, preview)
    }

    @Test
    fun aLinkReplyIsAllowedOnAnAccountsVeryFirstPost() = runTest {
        assertEquals(0, bot.account().publishedCount)

        bot.say("/post staging Check your photos.\n---\nDownload: {link}")

        assertEquals(PostStatus.Pending, bot.status("stt0001"))
    }

    @Test
    fun aPollAndAnImagePostMayHaveAReply() = runTest {
        bot.telegram.files["photos/p1.jpg"] = byteArrayOf(1)

        bot.say("/post staging Which one?\n? Red\n? Blue\n---\nVote above")
        bot.say("/post staging Fresh\n---\nMore: {link}", photoFileId = "p1")

        assertEquals("Vote above", bot.post("stp0001").replyText)
        assertEquals(PostKind.Poll, bot.post("stp0001").kind)
        assertEquals("More: {link}", bot.post("stt0002").replyText)
        assertEquals(PostKind.Image, bot.post("stt0002").kind)
    }

    @Test
    fun aBadReplyRejectsThePostWithTheRepliesOwnMessage() = runTest {
        bot.say("/post staging Fine text\n---\nGet it at https://example.com")
        bot.say("/post staging Fine text\n---")

        assertTrue(bot.store.allPosts.isEmpty())
        assertEquals(
            listOf(
                BotText.postRejected("write {link} in the reply instead of a raw link"),
                BotText.postRejected("the reply is empty"),
            ),
            bot.sentTexts(),
        )
    }

    @Test
    fun postNowPublishesThePostAndItsReplyInTheSameRequest() = runTest {
        bot.say("/post staging now Going out\n---\nDownload: {link}")

        val post = bot.post("stt0001")
        assertEquals(PostStatus.Published, post.status)
        assertNotNull(post.replyMediaId)
        val containers = bot.threads.callsTo("createContainer").map { it.args[2] as com.sirelon.sellsnap.threads.ContainerRequest }
        assertEquals(listOf(null, post.mediaId), containers.map { it.replyToId })
        assertTrue("https://go.test/go/stt0001" in containers[1].text)
        assertEquals(BotText.publishedNowWithReply("stt0001"), bot.sentTexts().single())
    }

    @Test
    fun postNowSaysWhenTheReplyFailedOrIsPending() = runTest {
        bot.threads.failNextReply(ThreadsFailure.Permanent(10, "Threads rejected the request (HTTP 400, code 10)"))
        bot.say("/post staging now One\n---\nreply")
        bot.threads.failNextReply(ThreadsFailure.RateLimited("limit"))
        bot.say("/post staging now Two\n---\nreply")

        assertEquals(PostStatus.Published, bot.status("stt0001"))
        assertEquals(PostStatus.Published, bot.status("stt0002"))
        assertEquals(
            listOf(BotText.publishedNowReplyFailed("stt0001"), BotText.publishedNowReplyPending("stt0002")),
            bot.sentTexts(),
        )
    }

    // --- /post

    @Test
    fun aTextPostBecomesAPendingTipInTheNextFreeSlotWithAPreview() = runTest {
        bot.say("/post staging Check your photos for glare.")

        val post = bot.post("stt0001")
        assertEquals(PostStatus.Pending, post.status)
        assertEquals(Pillar.Tip, post.pillar)
        assertEquals(PostKind.Text, post.kind)
        assertEquals(PostSource.Owner, post.source)
        assertEquals(Instant.parse("2026-10-12T05:30:00Z"), post.scheduledAt)
        assertEquals(1, bot.account().postSeq)
        val preview = bot.telegram.callsOf<FakeTelegramApi.Call.Sent>().single()
        assertTrue("Check your photos for glare." in preview.text.orEmpty())
        assertTrue("stt0001" in preview.text.orEmpty())
        assertEquals(listOf("Approve", "Edit", "Skip"), bot.keyboardOf(preview))
        assertEquals(preview.messageId, post.telegramMessageId)
    }

    @Test
    fun theNextPostTakesTheNextSlotAndCode() = runTest {
        bot.say("/post staging First")
        bot.say("/post staging Second")
        bot.say("/post staging Third")

        assertEquals(
            listOf(
                Instant.parse("2026-10-12T05:30:00Z"),
                Instant.parse("2026-10-12T16:30:00Z"),
                Instant.parse("2026-10-13T05:30:00Z"),
            ),
            listOf("stt0001", "stt0002", "stt0003").map { bot.post(it).scheduledAt },
        )
    }

    @Test
    fun linesStartingWithAQuestionMarkMakeAPoll() = runTest {
        bot.say("/post staging Which angle sells best?\n? Front\n? Side\n? Top")

        val post = bot.post("stp0001")
        assertEquals(PostKind.Poll, post.kind)
        assertEquals(Pillar.Poll, post.pillar)
        assertEquals("Which angle sells best?", post.text)
        assertEquals(listOf("Front", "Side", "Top"), post.pollOptions)
        assertEquals(Instant.parse("2026-10-12T16:30:00Z"), post.scheduledAt, "polls take slot 2")
        val preview = bot.telegram.callsOf<FakeTelegramApi.Call.Sent>().single().text.orEmpty()
        assertTrue("Poll options:" in preview && "2. Side" in preview)
    }

    @Test
    fun aPollWithOneOptionIsRejected() = runTest {
        bot.say("/post staging Pick one\n? Only")

        assertTrue(bot.store.allPosts.isEmpty())
        assertTrue("a poll needs 2 to 4 options" in bot.sentTexts().single())
        assertEquals(0, bot.account().postSeq)
    }

    @Test
    fun aPhotoWithThePostCommandAsItsCaptionMakesAnImagePost() = runTest {
        bot.telegram.files["photos/p1.jpg"] = byteArrayOf(9, 8, 7)

        bot.say("/post staging Fresh from the shelf", photoFileId = "p1")

        val post = bot.post("stt0001")
        assertEquals(PostKind.Image, post.kind)
        assertEquals(listOf("social/manual/stt0001.jpg"), post.imagePaths)
        assertContentEquals(byteArrayOf(9, 8, 7), bot.images.saved.getValue("social/manual/stt0001.jpg"))
        assertEquals("Fresh from the shelf", post.text)
        val preview = bot.telegram.calls.filterIsInstance<FakeTelegramApi.Call.Sent>().single()
        assertEquals("sendPhoto", preview.method)
    }

    @Test
    fun aPngSentAsAFileMakesAnImagePost() = runTest {
        bot.telegram.files["photos/d1.jpg"] = byteArrayOf(1, 2, 3)

        bot.say("/post staging Sent as a file", document = Document("d1", "image/png"))

        val post = bot.post("stt0001")
        assertEquals(PostKind.Image, post.kind)
        assertEquals(listOf("social/manual/stt0001.png"), post.imagePaths)
        assertContentEquals(byteArrayOf(1, 2, 3), bot.images.saved.getValue("social/manual/stt0001.png"))
    }

    @Test
    fun aFileThatIsNotAnImageIsRefused() = runTest {
        bot.say("/post staging Here is a PDF", document = Document("d2", "application/pdf"))

        assertEquals(listOf(BotText.UNSUPPORTED_FILE), bot.sentTexts())
        assertTrue(bot.store.allPosts.isEmpty())
    }

    @Test
    fun aPhotoWithoutACaptionGetsAHint() = runTest {
        bot.sendBareImage(photoFileId = "p1")
        bot.sendBareImage(document = Document("d1", "image/jpeg"))

        assertEquals(listOf(BotText.PHOTO_NEEDS_CAPTION, BotText.PHOTO_NEEDS_CAPTION), bot.sentTexts())
        assertTrue(bot.store.allPosts.isEmpty())
    }

    @Test
    fun aPollCannotCarryAPhoto() = runTest {
        bot.say("/post staging Pick\n? A\n? B", photoFileId = "p1")

        assertEquals(listOf(BotText.POLL_WITH_PHOTO), bot.sentTexts())
        assertTrue(bot.store.allPosts.isEmpty())
    }

    @Test
    fun textWithTheLinkPlaceholderIsAnUpdateAndNeedsFivePublishedPosts() = runTest {
        bot.say("/post staging Version 2 is out {link}")
        assertTrue(BotText.violation(com.sirelon.sellsnap.content.ContentRules.Violation.LinkTooEarly) in bot.sentTexts().single())
        assertTrue(bot.store.allPosts.isEmpty())

        bot.store.put(bot.account().copy(publishedCount = 5))
        bot.say("/post staging Version 2 is out {link}")

        val post = bot.post("stu0001")
        assertEquals(Pillar.Update, post.pillar)
        assertTrue(post.link)
    }

    @Test
    fun anOverLongPostIsRejectedAndNothingIsCreated() = runTest {
        bot.say("/post staging " + "x".repeat(600))

        assertTrue(bot.store.allPosts.isEmpty())
        assertEquals(0, bot.account().postSeq)
        assertTrue("too long" in bot.sentTexts().single())
    }

    @Test
    fun anUnknownAccountOrCommandGetsAShortUsageLine() = runTest {
        bot.say("/post nobody hello")
        bot.say("/frobnicate")
        bot.say("/approval")
        bot.say("/post")

        assertEquals(
            listOf(BotText.unknownAccount("nobody"), BotText.USAGE, BotText.USAGE, BotText.POST_USAGE),
            bot.sentTexts(),
        )
    }

    @Test
    fun postNowIsApprovedAndPublishedInsideTheRequest() = runTest {
        bot.say("/post staging now Going out right now")

        val post = bot.post("stt0001")
        assertEquals(PostStatus.Published, post.status)
        assertEquals(NOW, post.scheduledAt)
        assertEquals(1, bot.threads.published.size)
        assertEquals(1, bot.threads.callsTo("createContainer").size)
        assertEquals(BotText.publishedNow("stt0001"), bot.sentTexts().single())
        assertEquals(listOf("Delete"), bot.keyboardOf(bot.telegram.callsOf<FakeTelegramApi.Call.Sent>().single()))
    }

    @Test
    fun aNowPostCountsTowardTheDailyCapButIsNeverRefusedForIt() = runTest {
        bot.store.put(bot.account().copy(publishedCount = 20))
        bot.say("/post staging now One")
        bot.say("/post staging now Two")
        bot.say("/post staging now Three")
        assertEquals(3, bot.threads.published.size)

        bot.say("/post staging Four")

        // Three posts sit on the 12th, so the next slot is on the 13th.
        assertEquals(Instant.parse("2026-10-13T05:30:00Z"), bot.post("stt0004").scheduledAt)
    }

    @Test
    fun postNowOnAPausedAccountIsRefusedBeforeAnythingIsCreated() = runTest {
        bot.store.put(bot.account().copy(paused = true, pausedReason = "test"))

        bot.say("/post staging now Hello")

        assertTrue(bot.store.allPosts.isEmpty())
        assertEquals(BotText.pausedBeforeNow(bot.account().handle, "staging"), bot.sentTexts().single())
    }

    @Test
    fun postNowWithoutATokenIsRefused() = runTest {
        bot.store.put(bot.account().copy(token = null))

        bot.say("/post staging now Hello")

        assertTrue(bot.store.allPosts.isEmpty())
        assertEquals(BotText.NO_TOKEN, bot.sentTexts().single())
    }

    @Test
    fun postNowFailingOnTheLimitReadMarksThePostFailed() = runTest {
        bot.threads.failNext("publishingLimit", ThreadsFailure.Permanent(190, "Threads rejected the request (HTTP 400, code 190)"))

        bot.say("/post staging now Hello")

        assertEquals(PostStatus.Failed, bot.status("stt0001"))
        assertTrue(bot.account().paused)
        assertTrue(bot.sentTexts().single().startsWith("stt0001 failed and the account is paused"))
    }

    @Test
    fun postNowReportsAPermanentFailure() = runTest {
        bot.threads.failNext("createContainer", ThreadsFailure.Permanent(190, "Threads rejected the request (HTTP 400, code 190)"))

        bot.say("/post staging now Hello")

        assertEquals(PostStatus.Failed, bot.status("stt0001"))
        assertTrue(bot.sentTexts().single().startsWith("stt0001 failed and the account is paused"))
    }

    // --- /token

    @Test
    fun theTokenMessageIsDeletedFirstAndNoReplyHoldsTheToken() = runTest {
        val message = bot.say("/token staging $TOKEN")

        val first = bot.telegram.calls.first() as FakeTelegramApi.Call.Deleted
        assertEquals(message.messageId, first.messageId)
        assertEquals("deleteMessage", bot.telegram.calls.first().method)
        assertEquals("refreshed-token", bot.account().token)
        assertFalse(TOKEN in bot.everythingSaid())
        assertTrue(bot.sentTexts().single().startsWith("Token saved for staging. It expires "))
    }

    @Test
    fun tokenRepliesNameMissingOptionalScopes() = runTest {
        bot.say("/token staging $TOKEN")

        assertTrue("Missing optional scopes: " in bot.sentTexts().single())
        assertTrue("threads_manage_insights" in bot.sentTexts().single())
    }

    @Test
    fun noTokenFailurePathEchoesTheToken() = runTest {
        // Rejected by Threads, a thrown error in the middle of intake, wrong shape and unknown account.
        bot.threads.failNext("debugToken", ThreadsFailure.Permanent(190, "Threads rejected the request (HTTP 400, code 190)"))
        bot.say("/token staging $TOKEN")
        bot.threads.failNext("debugToken", IllegalStateException("boom $TOKEN"))
        bot.say("/token staging $TOKEN")
        bot.say("/token staging")
        bot.say("/token nobody $TOKEN")

        assertEquals(4, bot.telegram.callsOf<FakeTelegramApi.Call.Deleted>().size)
        assertFalse(TOKEN in bot.everythingSaid())
        assertEquals("stored-token", bot.account().token)
        assertEquals(BotText.TOKEN_CHECK_FAILED, bot.sentTexts()[1])
    }

    @Test
    fun aTokenMessageThatCannotBeDeletedIsReported() = runTest {
        bot.telegram.failNext("deleteMessage", TelegramFailure("Telegram rejected deleteMessage (HTTP 400)"))

        bot.say("/token staging $TOKEN")

        assertEquals(BotText.TOKEN_MESSAGE_NOT_DELETED, bot.sentTexts().first())
        assertFalse(TOKEN in bot.everythingSaid())
    }

    // --- /approval, /resume, /refresh

    @Test
    fun resumeClearsThePauseWritesAnEventAndRollsOverduePosts() = runTest {
        bot.store.put(bot.account().copy(paused = true, pausedReason = "Threads rejected the request"))
        bot.store.put(testPost("stt0001", status = PostStatus.Approved, scheduledAt = NOW.minusSeconds(7200)))
        bot.store.put(testPost("stt0002", status = PostStatus.Pending, scheduledAt = NOW.minusSeconds(3600)).copy(telegramMessageId = 900))
        bot.store.put(testPost("stt0003", status = PostStatus.Approved, scheduledAt = NOW.plusSeconds(86_400)))
        bot.store.put(testPost("stt0004", status = PostStatus.Failed, scheduledAt = NOW.minusSeconds(100)))

        bot.say("/resume staging")

        val account = bot.account()
        assertFalse(account.paused)
        assertNull(account.pausedReason)
        assertEquals(1, bot.store.events.values.count { it.type == EventType.Resume })
        assertEquals(Instant.parse("2026-10-12T05:30:00Z"), bot.post("stt0001").scheduledAt)
        assertEquals(Instant.parse("2026-10-12T16:30:00Z"), bot.post("stt0002").scheduledAt)
        assertEquals(NOW.plusSeconds(86_400), bot.post("stt0003").scheduledAt)
        assertEquals(NOW.minusSeconds(100), bot.post("stt0004").scheduledAt)
        assertEquals(PostStatus.Pending, bot.status("stt0002"))
        assertEquals(BotText.resumed("sellsnap.staging", 2), bot.sentTexts().last())
        // The pending post's preview shows its new slot.
        assertEquals("editMessageText", bot.telegram.callsOf<FakeTelegramApi.Call.Edited>().single().method)
    }

    @Test
    fun refreshRunsTheForcedRefreshAndReportsTheExpiry() = runTest {
        bot.say("/refresh staging")

        assertEquals(1, bot.threads.callsTo("refreshToken").size)
        assertTrue(bot.sentTexts().single().startsWith("Token for staging refreshed. It expires "))
        assertFalse("refreshed-token" in bot.everythingSaid())
    }

    @Test
    fun aFailedRefreshIsReportedWithoutSecrets() = runTest {
        bot.threads.failNext("refreshToken", ThreadsFailure.Permanent(190, "Threads rejected the request (HTTP 400, code 190)"))

        bot.say("/refresh staging")

        assertEquals(BotText.refreshFailed("Threads rejected the request (HTTP 400, code 190)"), bot.sentTexts().single())
        assertNotNull(bot.account().token)
    }

    @Test
    fun postWithoutNowAsksToWaitWhileABatchIsDraftingTheAccount() = runTest {
        bot.store.put(
            SocialBatch(
                "2026-W42",
                runId = "run-1",
                leaseUntil = NOW.plusSeconds(120),
                startedAt = NOW,
                accounts = mapOf("staging" to BatchAccount(slotsWritten = 3)),
            ),
        )

        bot.say("/post staging A tip while the batch writes")

        assertEquals(listOf("A batch is drafting for staging, try again in a few minutes."), bot.sentTexts())
        assertTrue(bot.store.allPosts.isEmpty())
        assertEquals(0, bot.account().postSeq)
    }

    @Test
    fun postNowIsNotBlockedByABatchAndAFinishedBatchDoesNotBlockPost() = runTest {
        bot.store.put(
            SocialBatch("2026-W42", runId = "run-1", leaseUntil = NOW.plusSeconds(120), startedAt = NOW, accounts = mapOf("staging" to BatchAccount())),
        )
        bot.say("/post staging now Going out")
        assertEquals(PostStatus.Published, bot.status("stt0001"))

        // The run finished the account and released the lease.
        bot.store.put(
            SocialBatch("2026-W42", leaseUntil = null, startedAt = NOW, accounts = mapOf("staging" to BatchAccount(done = true, slotsWritten = 12))),
        )
        bot.say("/post staging A later tip")
        assertEquals(PostStatus.Pending, bot.status("stt0002"))
    }

    private companion object {
        const val TOKEN = "THQWsecret1234567890tokenvalue"
    }
}
