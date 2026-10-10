package com.sirelon.sellsnap.telegram

import com.sirelon.sellsnap.fakes.FakeLlmClient
import com.sirelon.sellsnap.fakes.FakeTelegramApi
import com.sirelon.sellsnap.fakes.testAccount
import com.sirelon.sellsnap.fakes.testPost
import com.sirelon.sellsnap.llm.LlmFailure
import com.sirelon.sellsnap.model.AccountMode
import com.sirelon.sellsnap.model.EventType
import com.sirelon.sellsnap.model.Pillar
import com.sirelon.sellsnap.model.PostSource
import com.sirelon.sellsnap.model.PostStatus
import com.sirelon.sellsnap.model.UsageCounters
import com.sirelon.sellsnap.telegram.BotFixture.Companion.NOW
import com.sirelon.sellsnap.threads.ThreadsFailure
import kotlinx.coroutines.runBlocking
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
        val buttons = bot.telegram.callsOf<FakeTelegramApi.Call.Edited>().single().markup as InlineKeyboardMarkup
        assertEquals(listOf(BotText.CANCEL), buttons.inlineKeyboard.flatten().map { it.text })
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
    fun theEditPromptSaysThatTheReplyStaysWhenThePostHasOne() = runTest {
        bot.store.put(bot.putPending().copy(replyText = "Download: {link}"))

        bot.tap("e:stt0001")

        assertEquals(BotText.editPrompt("stt0001", hasReply = true), bot.sentTexts().single())
        assertTrue(bot.sentTexts().single().endsWith("Its reply stays as it is."))
    }

    @Test
    fun editingTheTextLeavesTheReplyAsItWas() = runTest {
        bot.store.put(bot.putPending().copy(pillar = Pillar.Update, replyText = "Download: {link}"))
        bot.tap("e:stt0001")

        bot.say("New text", replyTo = bot.post("stt0001").editPromptMessageId)

        val post = bot.post("stt0001")
        assertEquals("New text", post.text)
        assertEquals("Download: {link}", post.replyText)
        assertEquals(PostStatus.Approved, post.status)
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
        assertTrue("used only in russian" in bot.sentTexts().last())
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
    fun deleteAfterPublishRemovesTheReplyFirstThenThePost() = runTest {
        bot.store.put(publishedPost().copy(replyText = "Download: {link}", replyMediaId = "reply-9"))

        bot.tap("d:stt0001")

        assertEquals(listOf("reply-9", "media-9"), bot.threads.deleted)
        assertEquals(PostStatus.Deleted, bot.status("stt0001"))
        assertEquals(listOf<String?>(BotText.DELETED), bot.answers())
    }

    @Test
    fun aReplyThatIsAlreadyGoneDoesNotBlockDeletingThePost() = runTest {
        bot.store.put(publishedPost().copy(replyText = "Download: {link}", replyMediaId = "reply-9"))
        bot.threads.failNext("delete", ThreadsFailure.Permanent(100, "Threads rejected the request (HTTP 400, code 100)"))

        bot.tap("d:stt0001")

        assertEquals(listOf("media-9"), bot.threads.deleted)
        assertEquals(listOf("reply-9", "media-9"), bot.threads.callsTo("delete").map { it.args[0] })
        assertEquals(PostStatus.Deleted, bot.status("stt0001"))
        assertEquals(listOf<String?>(BotText.DELETED), bot.answers())
    }

    @Test
    fun aTemporaryFailureDeletingTheReplyLeavesThePostPublished() = runTest {
        bot.store.put(publishedPost().copy(replyText = "Download: {link}", replyMediaId = "reply-9"))
        bot.threads.failNext("delete", ThreadsFailure.Transient("Threads server error (HTTP 500)"))

        bot.tap("d:stt0001")

        assertEquals(PostStatus.Published, bot.status("stt0001"))
        assertEquals(listOf("reply-9"), bot.threads.callsTo("delete").map { it.args[0] })
        assertTrue(bot.answers().single().orEmpty().startsWith("Threads did not delete it"))
    }

    @Test
    fun aPostWhoseReplyWasNeverPublishedDeletesOnlyThePost() = runTest {
        bot.store.put(publishedPost().copy(replyText = "Download: {link}"))

        bot.tap("d:stt0001")

        assertEquals(listOf("media-9"), bot.threads.deleted)
    }

    @Test
    fun cancelInApprovalModeIsALateSkip() = runTest {
        bot.store.put(bot.account().copy(approvalStreak = 7))
        bot.store.put(testPost(status = PostStatus.Approved, scheduledAt = NOW.plusSeconds(3600)).copy(telegramMessageId = 900))

        bot.tap("c:stt0001")

        val post = bot.post("stt0001")
        assertEquals(PostStatus.Skipped, post.status)
        assertEquals("cancelled_before_slot", post.skipReason)
        assertEquals(7, bot.account().approvalStreak)
        assertEquals(AccountMode.Approval, bot.account().mode)
        assertTrue(bot.store.events.isEmpty())
        assertTrue(bot.threads.calls.none { it.method == "delete" })
        assertEquals(listOf<String?>(BotText.CANCELLED), bot.answers())
        bot.tap("c:stt0001")
        assertEquals("Already skipped.", bot.answers().last())
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

    // --- Edit on an AI draft

    @Test
    fun editingAnAiDraftClearsItsBackTranslationAndCheckNotes() = runTest {
        bot.putDraft {
            it.copy(backTranslation = "Old English version", checkNotes = "Guardrail: old finding", needsAttention = true)
        }
        bot.tap("e:stt0001")

        bot.say("A new tip from the owner", replyTo = bot.post("stt0001").editPromptMessageId)

        val post = bot.post("stt0001")
        assertEquals("A new tip from the owner", post.text)
        assertNull(post.backTranslation)
        assertNull(post.checkNotes)
        assertTrue(post.editedByOwner)
        assertEquals(PostStatus.Approved, post.status)
        assertEquals(0, bot.account().approvalStreak)
        val shown = bot.telegram.callsOf<FakeTelegramApi.Call.Edited>().last().text.orEmpty()
        assertFalse("Old English version" in shown)
        assertFalse("Check:" in shown)
    }

    // --- Regenerate

    private val newUk = "Сфотографуйте річ біля вікна: денне світло показує справжній колір 📸"

    private fun edits() = bot.telegram.callsOf<FakeTelegramApi.Call.Edited>()

    private fun draftCalls() = bot.llm.requests.filter { it.model == BotFixture.DRAFT_MODEL }

    @Test
    fun regenerateWritesANewVersionAndRefreshesThePreviewInPlace() = runTest {
        bot.putDraft { it.copy(needsAttention = true, checkNotes = "Guardrail: old finding", editedByOwner = true) }
        bot.llm.enqueue(bot.draftReply(newUk))
        bot.llm.enqueue(bot.checkReply("uk"))

        bot.tap("r:stt0001")

        assertEquals(listOf<String?>(BotText.REGENERATING), bot.answers())
        val post = bot.post("stt0001")
        assertEquals(newUk, post.text)
        assertEquals(1, post.regenerations)
        assertEquals(PostStatus.Pending, post.status)
        assertFalse(post.needsAttention)
        assertNull(post.checkNotes)
        assertNull(post.backTranslation)
        assertFalse(post.editedByOwner)
        assertNull(post.regeneratingSince)
        assertEquals(listOf(BotFixture.DRAFT_MODEL, BotFixture.CHECK_MODEL), bot.llm.requests.map { it.model })

        val refresh = edits().single()
        assertEquals("editMessageText", refresh.method)
        assertEquals(900L, refresh.messageId)
        assertTrue(newUk in refresh.text.orEmpty())
        assertFalse("Old draft text" in refresh.text.orEmpty())
        assertEquals(
            listOf("Approve", "Edit", "Skip", "Regenerate"),
            refresh.markup!!.inlineKeyboard.flatten().map { it.text },
        )
        assertTrue(bot.telegram.callsOf<FakeTelegramApi.Call.Sent>().isEmpty())
    }

    @Test
    fun theDrafterPromptCarriesThePostsPillarAndTheAccountsLatestTexts() = runTest {
        bot.store.put(testPost(id = "stt0002", scheduledAt = NOW.minusSeconds(86_400)).copy(text = "Yesterday's tip"))
        bot.putDraft()
        bot.llm.enqueue(bot.draftReply(newUk))
        bot.llm.enqueue(bot.checkReply("uk"))

        bot.tap("r:stt0001")

        val prompt = draftCalls().single().user
        assertTrue("Ukrainian" in prompt)
        assertTrue("Yesterday's tip" in prompt)
        assertTrue("Old draft text" in prompt)
        assertFalse("RELEASE" in prompt)
    }

    @Test
    fun aRegeneratedUpdateIsWrittenFromTheReleaseThatItsSourceRefNames() = runTest {
        bot.store.put(bot.account().copy(publishedCount = 12))
        bot.putDraft { it.copy(pillar = Pillar.Update, sourceRef = "release:3.3", replyText = "Old: {link}") }
        bot.llm.enqueue(bot.draftReply("Нове: чернетки зберігають незавершені оголошення ✍️", replyText = "Спробуйте: {link}"))
        bot.llm.enqueue(bot.checkReply("uk"))

        bot.tap("r:stt0001")

        assertTrue("RELEASE 3.3" in draftCalls().single().user)
        val post = bot.post("stt0001")
        assertEquals("Спробуйте: {link}", post.replyText)
        assertEquals(1, post.regenerations)
    }

    @Test
    fun anUpdateWhoseReleaseIsGoneIsLeftAloneWithAShortReply() = runTest {
        bot.putDraft { it.copy(pillar = Pillar.Update, sourceRef = "release:9.9") }
        val before = bot.post("stt0001")

        bot.tap("r:stt0001")

        assertEquals(before, bot.post("stt0001"))
        assertTrue(bot.llm.requests.isEmpty())
        assertEquals(listOf(BotText.REGENERATE_NO_RELEASE), bot.sentTexts())
    }

    @Test
    fun aRegeneratedPolishDraftGetsItsBackTranslationInTheRefreshedPreview() = runTest {
        bot.store.put(testAccount(id = "pl", codePrefix = "pl", language = "pl"))
        bot.putDraft(id = "plt0001") { it.copy(accountId = "pl", code = "plt0001") }
        bot.llm.enqueue(bot.draftReply("Sfotografuj rzecz przy oknie: dzienne światło pokazuje prawdziwy kolor 📸"))
        bot.llm.enqueue(bot.checkReply("pl", backTranslation = "Photograph the item by a window.", naturalnessOk = true))

        bot.tap("r:plt0001")

        assertEquals("Photograph the item by a window.", bot.post("plt0001").backTranslation)
        assertTrue(edits().single().text.orEmpty().endsWith("\n\nEN: Photograph the item by a window."))
    }

    @Test
    fun aFailedCheckKeepsTheNewVersionAndShowsItsNotes() = runTest {
        bot.putDraft()
        bot.llm.enqueue(bot.draftReply(newUk))
        bot.llm.enqueue(bot.checkReply("uk", findings = listOf("1. Too pushy.")))
        bot.llm.enqueue(bot.draftReply(newUk))
        bot.llm.enqueue(bot.checkReply("uk", findings = listOf("1. Still too pushy.")))

        bot.tap("r:stt0001")

        val post = bot.post("stt0001")
        assertTrue(post.needsAttention)
        assertEquals("Guardrail: 1. Still too pushy.", post.checkNotes)
        assertTrue(edits().single().text.orEmpty().endsWith("\n\nCheck: Guardrail: 1. Still too pushy."))
    }

    @Test
    fun aSecondTapWhileTheFirstIsStillWritingRunsNothing() = runTest {
        lateinit var racing: BotFixture
        racing = BotFixture(
            FakeLlmClient { request ->
                if (request.model == BotFixture.DRAFT_MODEL) {
                    // The owner taps again while the model is still answering.
                    runBlocking { racing.tap("r:stt0001") }
                    racing.draftReply(newUk)
                } else {
                    racing.checkReply("uk")
                }
            },
        )
        racing.putDraft()

        racing.tap("r:stt0001")

        assertEquals(listOf<String?>(BotText.REGENERATING, BotText.REGENERATE_RUNNING), racing.answers())
        assertEquals(1, racing.llm.requests.count { it.model == BotFixture.DRAFT_MODEL })
        assertEquals(1, racing.post("stt0001").regenerations)
        assertEquals(newUk, racing.post("stt0001").text)
        assertNull(racing.post("stt0001").regeneratingSince)
    }

    @Test
    fun aMarkerFromARunningTapRefusesAndAStaleMarkerDoesNot() = runTest {
        bot.putDraft { it.copy(regeneratingSince = NOW.minusSeconds(60)) }
        val before = bot.post("stt0001")

        bot.tap("r:stt0001")

        assertEquals(listOf<String?>(BotText.REGENERATE_RUNNING), bot.answers())
        assertEquals(before, bot.post("stt0001"))
        assertTrue(bot.llm.requests.isEmpty())

        bot.store.put(before.copy(regeneratingSince = NOW.minusSeconds(600)))
        bot.llm.enqueue(bot.draftReply(newUk))
        bot.llm.enqueue(bot.checkReply("uk"))

        bot.tap("r:stt0001")

        assertEquals(1, bot.post("stt0001").regenerations)
        assertNull(bot.post("stt0001").regeneratingSince)
    }

    @Test
    fun theThirdRegenerationHidesTheButtonAndTheFourthTapIsRefused() = runTest {
        bot.putDraft { it.copy(regenerations = 2) }
        bot.llm.enqueue(bot.draftReply(newUk))
        bot.llm.enqueue(bot.checkReply("uk"))

        bot.tap("r:stt0001")

        assertEquals(3, bot.post("stt0001").regenerations)
        assertEquals(listOf("Approve", "Edit", "Skip"), edits().single().markup!!.inlineKeyboard.flatten().map { it.text })
        val requestsSoFar = bot.llm.requests.size

        bot.tap("r:stt0001")

        assertEquals(BotText.NO_MORE_VERSIONS, bot.answers().last())
        assertEquals(3, bot.post("stt0001").regenerations)
        assertEquals(requestsSoFar, bot.llm.requests.size)
        assertEquals(1, edits().size)
    }

    @Test
    fun aDraftThatBreaksTheRulesTwiceLeavesThePostUnchanged() = runTest {
        bot.putDraft()
        val before = bot.post("stt0001")
        repeat(2) { bot.llm.enqueue(bot.draftReply("Read more at https://example.com/tip")) }

        bot.tap("r:stt0001")

        assertEquals(before, bot.post("stt0001"))
        assertEquals(listOf<String?>(BotText.REGENERATING), bot.answers())
        assertEquals(listOf(BotText.REGENERATE_FAILED), bot.sentTexts())
        assertTrue(edits().isEmpty())
    }

    @Test
    fun anUnreachableModelGivesTheBusyReplyAndLeavesThePostUnchanged() = runTest {
        bot.putDraft()
        val before = bot.post("stt0001")
        bot.llm.enqueueFailure(LlmFailure.Transient("The model is busy"))

        bot.tap("r:stt0001")

        assertEquals(before, bot.post("stt0001"))
        assertEquals(listOf(BotText.REGENERATE_BUSY), bot.sentTexts())
        assertEquals(900L, bot.telegram.callsOf<FakeTelegramApi.Call.Sent>().single().replyToMessageId)
        assertTrue(edits().isEmpty())

        // The marker is gone, so the owner can tap again.
        bot.llm.enqueue(bot.draftReply(newUk))
        bot.llm.enqueue(bot.checkReply("uk"))
        bot.tap("r:stt0001")
        assertEquals(1, bot.post("stt0001").regenerations)
    }

    @Test
    fun aPostThatIsNotFromTheDrafterHasNoRegenerateButtonAndAForgedTapIsRefused() = runTest {
        bot.putPending()
        val before = bot.post("stt0001")

        assertEquals(listOf("Approve", "Edit", "Skip"), bot.previews.keyboard(before, bot.account())!!.inlineKeyboard.flatten().map { it.text })
        bot.tap("r:stt0001")

        assertEquals(listOf<String?>(BotText.CANNOT_REGENERATE), bot.answers())
        assertEquals(before, bot.post("stt0001"))
        assertTrue(bot.llm.requests.isEmpty())
    }

    @Test
    fun aTapOnAPostThatIsNoLongerPendingOrDoesNotExistIsRefused() = runTest {
        bot.putDraft { it.copy(status = PostStatus.Approved) }

        bot.tap("r:stt0001")
        bot.tap("r:missing")

        assertEquals(listOf<String?>("Already approved.", BotText.POST_NOT_FOUND), bot.answers())
        assertTrue(bot.llm.requests.isEmpty())
    }

    @Test
    fun aPostThatChangesWhileTheModelWritesKeepsItsNewStateAndDropsTheNewVersion() = runTest {
        lateinit var racing: BotFixture
        racing = BotFixture(
            FakeLlmClient { request ->
                if (request.model == BotFixture.DRAFT_MODEL) runBlocking { racing.tap("s:stt0001") }
                if (request.model == BotFixture.DRAFT_MODEL) racing.draftReply(newUk) else racing.checkReply("uk")
            },
        )
        racing.putDraft()

        racing.tap("r:stt0001")

        assertEquals(PostStatus.Skipped, racing.post("stt0001").status)
        assertEquals("Old draft text", racing.post("stt0001").text)
        assertEquals(0, racing.post("stt0001").regenerations)
        assertEquals(BotText.REGENERATE_POST_CHANGED, racing.sentTexts().last())
    }

    @Test
    fun theDailyUsageLimitGivesItsOwnReplyAndLeavesThePostUnchanged() = runTest {
        bot.putDraft()
        val before = bot.post("stt0001")
        bot.store.addUsage("20261012", BotFixture.DRAFT_MODEL, UsageCounters(calls = 180, promptTokens = 0, outputTokens = 0, thoughtsTokens = 0))

        bot.tap("r:stt0001")

        assertEquals(before, bot.post("stt0001"))
        assertTrue(bot.llm.requests.isEmpty())
        assertEquals(listOf(BotText.USAGE_LIMIT_REACHED), bot.sentTexts())
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
        assertEquals(listOf("Cancel"), labels(PostStatus.Approved))
        assertEquals(listOf("Delete before slot"), labels(PostStatus.Approved, auto))
        assertEquals(listOf("Delete"), labels(PostStatus.Published))
        assertNull(labels(PostStatus.Skipped))
    }
}
