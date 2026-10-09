package com.sirelon.sellsnap.telegram

import com.sirelon.sellsnap.fakes.FakeTelegramApi
import com.sirelon.sellsnap.fakes.testPost
import com.sirelon.sellsnap.model.AccountMode
import com.sirelon.sellsnap.model.EventType
import com.sirelon.sellsnap.model.Pillar
import com.sirelon.sellsnap.model.PostSource
import com.sirelon.sellsnap.model.PostStatus
import com.sirelon.sellsnap.telegram.BotFixture.Companion.NOW
import com.sirelon.sellsnap.threads.ThreadsFailure
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApprovalActionsTest {
    private val bot = BotFixture()

    // --- Who may press

    @Test
    fun updatesFromAnyoneButTheOwnerAreIgnoredSilently() = runTest {
        bot.putPending()

        bot.tap("a:stt0001", from = 7)
        bot.say("/post staging hello", from = 7)

        assertEquals(PostStatus.Pending, bot.status("stt0001"))
        assertTrue(bot.telegram.calls.isEmpty())
        assertTrue(bot.store.updates.isEmpty())
    }

    // --- Approve and graduation

    @Test
    fun approveMovesPendingToApprovedAndCountsTowardTheStreak() = runTest {
        bot.putPending()

        bot.tap("a:stt0001")

        assertEquals(PostStatus.Approved, bot.status("stt0001"))
        assertEquals(1, bot.account().approvalStreak)
        assertEquals(listOf<String?>(BotText.APPROVED), bot.answers())
        assertNull(bot.telegram.callsOf<FakeTelegramApi.Call.Edited>().single().markup)
    }

    @Test
    fun aDoubleTapAnswersAlreadyAndCountsOnce() = runTest {
        bot.putPending()

        bot.tap("a:stt0001")
        bot.tap("a:stt0001")

        assertEquals(listOf<String?>(BotText.APPROVED, "Already approved."), bot.answers())
        assertEquals(1, bot.account().approvalStreak)
    }

    @Test
    fun nineteenApprovalsStayInApprovalMode() = runTest {
        repeat(19) { index ->
            bot.putPending(id = "stt%04d".format(index + 1))
            bot.tap("a:stt%04d".format(index + 1))
        }

        assertEquals(19, bot.account().approvalStreak)
        assertEquals(AccountMode.Approval, bot.account().mode)
        assertTrue(bot.store.events.values.none { it.type == EventType.ModeChange })
    }

    @Test
    fun theTwentiethApprovalSwitchesToAutoAndTellsTheOwner() = runTest {
        repeat(20) { index ->
            bot.putPending(id = "stt%04d".format(index + 1))
            bot.tap("a:stt%04d".format(index + 1))
        }

        assertEquals(AccountMode.Auto, bot.account().mode)
        assertEquals(1, bot.store.events.values.count { it.type == EventType.ModeChange })
        assertEquals(BotText.graduated("staging", "sellsnap.staging", 20), bot.sentTexts().last())
    }

    @Test
    fun ownerPostsNeitherCountNorReset() = runTest {
        bot.store.put(bot.account().copy(approvalStreak = 5))
        bot.store.put(testPost(id = "stt0001", status = PostStatus.Pending).copy(source = PostSource.Owner))

        bot.tap("a:stt0001")

        assertEquals(PostStatus.Approved, bot.status("stt0001"))
        assertEquals(5, bot.account().approvalStreak)
    }

    @Test
    fun skipLeavesTheStreakAlone() = runTest {
        bot.store.put(bot.account().copy(approvalStreak = 7))
        bot.putPending()

        bot.tap("s:stt0001")

        assertEquals(PostStatus.Skipped, bot.status("stt0001"))
        assertEquals(7, bot.account().approvalStreak)
        assertEquals(listOf<String?>(BotText.SKIPPED), bot.answers())
        bot.tap("s:stt0001")
        assertEquals("Already skipped.", bot.answers().last())
    }

    @Test
    fun everyTapIsAnsweredEvenForMissingPostsAndUnknownButtons() = runTest {
        bot.tap("a:nope")
        bot.tap("x:stt0001")
        bot.tap("garbage")

        assertEquals(listOf<String?>(BotText.POST_NOT_FOUND, BotText.UNKNOWN_BUTTON, BotText.UNKNOWN_BUTTON), bot.answers())
    }

    // --- Edit

    @Test
    fun editSendsAForceReplyPromptNamingTheCodeAndStoresItsId() = runTest {
        bot.putPending()

        bot.tap("e:stt0001")

        val prompt = bot.telegram.callsOf<FakeTelegramApi.Call.Sent>().single()
        assertEquals(BotText.editPrompt("stt0001"), prompt.text)
        assertTrue("stt0001" in prompt.text.orEmpty())
        assertEquals(ForceReply(true), prompt.markup)
        assertEquals(prompt.messageId, bot.post("stt0001").editPromptMessageId)
        assertEquals(PostStatus.Pending, bot.status("stt0001"))
        assertEquals(listOf<String?>(BotText.EDIT_PROMPT_SENT), bot.answers())
    }

    @Test
    fun aReplyToThePromptReplacesTheTextApprovesAndResetsTheStreak() = runTest {
        bot.store.put(bot.account().copy(approvalStreak = 9, publishedCount = 6))
        bot.store.put(testPost(status = PostStatus.Pending, scheduledAt = NOW.plusSeconds(3600)).copy(pillar = Pillar.Update, telegramMessageId = 900))
        bot.tap("e:stt0001")
        val promptId = bot.post("stt0001").editPromptMessageId!!

        bot.say("A better tip. Get the app: {link}", replyTo = promptId)

        val post = bot.post("stt0001")
        assertEquals("A better tip. Get the app: {link}", post.text)
        assertEquals(PostStatus.Approved, post.status)
        assertTrue(post.editedByOwner)
        assertTrue(post.link)
        assertNull(post.editPromptMessageId)
        assertEquals(0, bot.account().approvalStreak)
        assertEquals(BotText.editSaved("stt0001"), bot.sentTexts().last())
        val edited = bot.telegram.callsOf<FakeTelegramApi.Call.Edited>().last()
        assertEquals("editMessageText", edited.method)
        assertTrue("A better tip." in edited.text.orEmpty())
        assertTrue("https://go.test/go/stt0001" in edited.text.orEmpty())
    }

    @Test
    fun editingAnImagePostRewritesTheCaption() = runTest {
        bot.store.put(
            testPost(status = PostStatus.Pending).copy(
                kind = com.sirelon.sellsnap.model.PostKind.Image,
                imagePaths = listOf("social/manual/stt0001.jpg"),
                telegramMessageId = 900,
            ),
        )
        bot.tap("e:stt0001")

        bot.say("New caption", replyTo = bot.post("stt0001").editPromptMessageId)

        assertEquals("editMessageCaption", bot.telegram.callsOf<FakeTelegramApi.Call.Edited>().last().method)
    }

    @Test
    fun anyOtherPlainTextGetsAHintAndChangesNothing() = runTest {
        bot.putPending()
        bot.tap("e:stt0001")
        val before = bot.post("stt0001")

        bot.say("just some words")
        bot.say("a reply to something else", replyTo = 4242)

        assertEquals(before, bot.post("stt0001"))
        assertEquals(listOf(BotText.PLAIN_TEXT_HINT, BotText.PLAIN_TEXT_HINT), bot.sentTexts().takeLast(2))
    }

    @Test
    fun anOverLongEditIsRejectedAndThePostStaysPending() = runTest {
        bot.putPending()
        bot.tap("e:stt0001")
        val promptId = bot.post("stt0001").editPromptMessageId

        bot.say("x".repeat(600), replyTo = promptId)

        val post = bot.post("stt0001")
        assertEquals(PostStatus.Pending, post.status)
        assertEquals("Tip text", post.text)
        assertFalse(post.editedByOwner)
        assertEquals(promptId, post.editPromptMessageId)
        assertTrue(BotText.violation(com.sirelon.sellsnap.content.ContentRules.Violation.TooLong) in bot.sentTexts().last())
    }

    @Test
    fun aRussianOnlyLetterIsRejectedWithItsReason() = runTest {
        bot.putPending()
        bot.tap("e:stt0001")

        bot.say("bad ${Char(0x44B)} letter", replyTo = bot.post("stt0001").editPromptMessageId)

        assertEquals(PostStatus.Pending, bot.status("stt0001"))
        assertTrue("used only in Russian" in bot.sentTexts().last())
    }

    @Test
    fun aLinkInATipIsRejected() = runTest {
        bot.putPending()
        bot.tap("e:stt0001")

        bot.say("Get it {link}", replyTo = bot.post("stt0001").editPromptMessageId)

        assertEquals(PostStatus.Pending, bot.status("stt0001"))
        assertTrue("a link is not allowed" in bot.sentTexts().last())
    }

    @Test
    fun editOfAPostThatIsNoLongerPendingChangesNothing() = runTest {
        bot.putPending()
        bot.tap("s:stt0001")

        bot.tap("e:stt0001")

        assertEquals("Already skipped.", bot.answers().last())
        assertTrue(bot.telegram.callsOf<FakeTelegramApi.Call.Sent>().isEmpty())
    }

    // --- Delete

    private fun publishedPost(id: String = "stt0001") =
        testPost(id = id, status = PostStatus.Published, scheduledAt = NOW.minusSeconds(60)).copy(mediaId = "media-9", telegramMessageId = 900)

    @Test
    fun deleteAfterPublishCallsThreadsAndReturnsToApproval() = runTest {
        bot.store.put(bot.account().copy(mode = AccountMode.Auto, approvalStreak = 20))
        bot.store.put(publishedPost())

        bot.tap("d:stt0001")

        assertEquals(listOf("media-9"), bot.threads.deleted)
        assertEquals(PostStatus.Deleted, bot.status("stt0001"))
        assertEquals(AccountMode.Approval, bot.account().mode)
        assertEquals(0, bot.account().approvalStreak)
        assertEquals(1, bot.store.events.values.count { it.type == EventType.Delete && it.postId == "stt0001" })
        assertEquals(listOf<String?>(BotText.DELETED), bot.answers())
    }

    @Test
    fun deleteBeforeTheSlotSkipsThePostWithoutCallingThreads() = runTest {
        bot.store.put(bot.account().copy(mode = AccountMode.Auto, approvalStreak = 20))
        bot.store.put(testPost(status = PostStatus.Approved, scheduledAt = NOW.plusSeconds(3600)).copy(autoApproved = true, telegramMessageId = 900))

        bot.tap("d:stt0001")

        assertTrue(bot.threads.calls.none { it.method == "delete" })
        val post = bot.post("stt0001")
        assertEquals(PostStatus.Skipped, post.status)
        assertEquals("deleted_before_slot", post.skipReason)
        assertEquals(AccountMode.Approval, bot.account().mode)
        assertEquals(0, bot.account().approvalStreak)
        assertEquals(1, bot.store.events.values.count { it.type == EventType.Delete })
    }

    @Test
    fun aFailedThreadsDeleteChangesNothingAndSaysSo() = runTest {
        bot.store.put(publishedPost())
        bot.threads.failNext("delete", ThreadsFailure.Permanent(100, "Threads rejected the request (HTTP 400, code 100)"))

        bot.tap("d:stt0001")

        assertEquals(PostStatus.Published, bot.status("stt0001"))
        assertTrue(bot.answers().single().orEmpty().startsWith("Threads did not delete it"))
    }

    @Test
    fun aSecondDeleteTapDoesNotCallThreadsAgain() = runTest {
        bot.store.put(publishedPost())

        bot.tap("d:stt0001")
        bot.tap("d:stt0001")

        assertEquals(1, bot.threads.deleted.size)
        assertEquals("A deleted post cannot be deleted.", bot.answers().last())
    }

    @Test
    fun aPublishedPostWithoutAMediaIdCannotBeDeletedFromHere() = runTest {
        bot.store.put(publishedPost().copy(mediaId = null))

        bot.tap("d:stt0001")

        assertEquals(PostStatus.Published, bot.status("stt0001"))
        assertEquals(listOf<String?>(BotText.NO_MEDIA_ID), bot.answers())
    }

    // --- Revert after Delete

    private fun autoApproved(id: String, minutesAhead: Long, auto: Boolean = true) =
        testPost(id = id, status = PostStatus.Approved, scheduledAt = NOW.plusSeconds(minutesAhead * 60))
            .copy(autoApproved = auto, telegramMessageId = 800 + minutesAhead)

    @Test
    fun aDeleteSendsEveryFutureAutoApprovedPostBackToPendingWithFreshPreviews() = runTest {
        bot.store.put(bot.account().copy(mode = AccountMode.Auto, approvalStreak = 20))
        bot.store.put(publishedPost("stt0001"))
        bot.store.put(autoApproved("stt0002", 60))
        bot.store.put(autoApproved("stt0003", 120))
        bot.store.put(autoApproved("stt0004", 180, auto = false))
        bot.store.put(autoApproved("stt0005", -30))

        bot.tap("d:stt0001")

        for (id in listOf("stt0002", "stt0003")) {
            val post = bot.post(id)
            assertEquals(PostStatus.Pending, post.status, id)
            assertFalse(post.autoApproved)
            assertTrue(post.telegramMessageId!! >= 1000, "a new preview message is recorded")
        }
        assertEquals(PostStatus.Approved, bot.status("stt0004"))
        assertEquals(PostStatus.Approved, bot.status("stt0005"))

        val previews = bot.telegram.callsOf<FakeTelegramApi.Call.Sent>().filter { "stt000" in it.text.orEmpty() }
        assertEquals(2, previews.size)
        previews.forEach { assertEquals(listOf("Approve", "Edit", "Skip"), bot.keyboardOf(it)) }
        assertTrue(bot.telegram.callsOf<FakeTelegramApi.Call.Edited>().any { it.messageId == 860L && it.markup == null })
        assertEquals(BotText.deletedNotice("sellsnap.staging", 2), bot.sentTexts().last())
    }

    @Test
    fun theApprovalCommandDoesTheSameRevert() = runTest {
        bot.store.put(bot.account().copy(mode = AccountMode.Auto, approvalStreak = 20))
        bot.store.put(autoApproved("stt0002", 60))

        bot.say("/approval staging")

        assertEquals(AccountMode.Approval, bot.account().mode)
        assertEquals(0, bot.account().approvalStreak)
        assertEquals(PostStatus.Pending, bot.status("stt0002"))
        assertEquals(1, bot.store.events.values.count { it.type == EventType.ModeChange })
        assertEquals(BotText.approvalDone("sellsnap.staging", 1), bot.sentTexts().last())
    }

    // --- Previews on the keyboard

    @Test
    fun previewKeyboardsFollowStatusAndMode() {
        val account = bot.account()
        val auto = account.copy(mode = AccountMode.Auto)
        val previews = bot.previews
        fun labels(status: PostStatus, a: com.sirelon.sellsnap.model.SocialAccount = account) =
            previews.keyboard(testPost(status = status), a)?.inlineKeyboard?.flatten()?.map { it.text }

        assertEquals(listOf("Approve", "Edit", "Skip"), labels(PostStatus.Pending))
        assertNull(labels(PostStatus.Approved))
        assertEquals(listOf("Delete before slot"), labels(PostStatus.Approved, auto))
        assertEquals(listOf("Delete"), labels(PostStatus.Published))
        assertNull(labels(PostStatus.Skipped))
    }
}
