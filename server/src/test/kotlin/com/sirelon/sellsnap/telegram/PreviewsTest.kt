package com.sirelon.sellsnap.telegram

import com.sirelon.sellsnap.fakes.FakeImageStore
import com.sirelon.sellsnap.fakes.testAccount
import com.sirelon.sellsnap.fakes.testPost
import com.sirelon.sellsnap.model.AccountMode
import com.sirelon.sellsnap.model.PostKind
import com.sirelon.sellsnap.model.PostSource
import com.sirelon.sellsnap.model.PostStatus
import com.sirelon.sellsnap.model.SocialPost
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PreviewsTest {
    private val previews = Previews(FakeImageStore(), "https://go.test/go/{code}")
    private val account = testAccount()
    private val slot = Instant.parse("2026-10-12T05:30:00Z")

    @Test
    fun anApprovedPostOffersCancelInApprovalModeAndDeleteBeforeSlotInAutoMode() {
        val approved = testPost(status = PostStatus.Approved, scheduledAt = slot)

        val inApproval = previews.keyboard(approved, account)?.inlineKeyboard?.flatten()
        val inAuto = previews.keyboard(approved, account.copy(mode = AccountMode.Auto))?.inlineKeyboard?.flatten()

        assertEquals(listOf(BotText.CANCEL to "c:stt0001"), inApproval?.map { it.text to it.callbackData })
        assertEquals(listOf(BotText.DELETE_BEFORE_SLOT to "d:stt0001"), inAuto?.map { it.text to it.callbackData })
    }

    @Test
    fun aTextPostIsATextMessageWithAHeaderInTheAccountsLocalTime() {
        val preview = previews.build(testPost(status = PostStatus.Pending, scheduledAt = slot), account)

        assertNull(preview.photoUrl)
        assertEquals("@sellsnap.staging | stt0001 | Mon 12 Oct 08:30\n\nTip text", preview.text)
    }

    @Test
    fun theHeaderFlagsNeedsAttentionAndAMissingSlot() {
        val preview = previews.build(testPost(status = PostStatus.Pending).copy(needsAttention = true), account)

        assertTrue(preview.text.startsWith("@sellsnap.staging | stt0001 | no slot yet | needs attention"))
    }

    @Test
    fun anImagePostIsAPhotoWithTheHeaderAndTextAsItsCaption() {
        val post = testPost(status = PostStatus.Pending, scheduledAt = slot)
            .copy(kind = PostKind.Image, imagePaths = listOf("social/manual/stt0001.jpg"))

        val preview = previews.build(post, account)

        assertEquals("https://images.test/social/manual/stt0001.jpg", preview.photoUrl)
        assertTrue(preview.text.startsWith("@sellsnap.staging | stt0001"))
        assertTrue(preview.text.endsWith("Tip text"))
    }

    @Test
    fun aCaptionIsCutToTelegramsLimitAndAMessageToItsOwn() {
        val long = "word ".repeat(2000)
        val photo = testPost(status = PostStatus.Pending).copy(kind = PostKind.Image, imagePaths = listOf("a.jpg"), text = long)
        val text = testPost(status = PostStatus.Pending).copy(text = long)

        assertEquals(1024, previews.build(photo, account).text.length)
        assertEquals(4096, previews.build(text, account).text.length)
        assertTrue(previews.build(photo, account).text.endsWith("…"))
    }

    @Test
    fun aPollShowsTheTextAndItsOptions() {
        val post = testPost(status = PostStatus.Pending).copy(kind = PostKind.Poll, text = "Which one?", pollOptions = listOf("Red", "Blue"))

        val text = previews.build(post, account).text

        assertTrue(text.endsWith("Which one?\n\nPoll options:\n1. Red\n2. Blue"))
    }

    @Test
    fun aLinkedPostShowsTheRenderedLinkAndIndependenceLine() {
        val post = testPost(status = PostStatus.Pending).copy(text = "Get it: {link}", link = true)

        val text = previews.build(post, account).text

        assertTrue("Get it: https://go.test/go/stt0001" in text)
        assertTrue(com.sirelon.sellsnap.content.ContentRules.INDEPENDENCE_LINE.getValue("uk") in text)
    }

    @Test
    fun aReplyShowsUnderTheTextAfterAReplyLineRenderedWithItsLink() {
        val post = testPost(status = PostStatus.Pending).copy(replyText = "Download: {link}")

        val text = previews.build(post, account).text

        val independence = com.sirelon.sellsnap.content.ContentRules.INDEPENDENCE_LINE.getValue("uk")
        assertTrue(
            text.endsWith("Tip text\n\n↳ reply:\nDownload: https://go.test/go/stt0001\n\n$independence"),
            text,
        )
    }

    @Test
    fun aReplyComesAfterAPollsOptions() {
        val post = testPost(status = PostStatus.Pending)
            .copy(kind = PostKind.Poll, text = "Which one?", pollOptions = listOf("Red", "Blue"), replyText = "More soon")

        assertTrue(previews.build(post, account).text.endsWith("Poll options:\n1. Red\n2. Blue\n\n↳ reply:\nMore soon"))
    }

    @Test
    fun aPostWithoutAReplyHasNoReplyLine() {
        assertFalse("↳ reply:" in previews.build(testPost(status = PostStatus.Pending), account).text)
    }

    @Test
    fun theLinkUrlIsBuiltFromTheTemplateTheCodeAndTheLanguage() {
        assertEquals("https://go.test/go/stt0001", previews.linkUrl("stt0001", "uk"))
        val website = Previews(FakeImageStore(), "https://sirelon.github.io/SellSnap/?lang={lang}&p={code}")
        assertEquals("https://sirelon.github.io/SellSnap/?lang=uk&p=ukt0042", website.linkUrl("ukt0042", "uk"))
        assertEquals("https://sirelon.github.io/SellSnap/?lang=pl&p=plt0042", website.linkUrl("plt0042", "pl"))
    }

    @Test
    fun aLinkedPostAndItsReplyPreviewTheWebsiteLink() {
        val website = Previews(FakeImageStore(), "https://sirelon.github.io/SellSnap/?lang={lang}&p={code}")
        val pl = testAccount(id = "pl", codePrefix = "pl", language = "pl")
        val post = testPost(id = "plt0001", accountId = "pl", status = PostStatus.Pending)
            .copy(text = "News {link}", replyText = "Get it: {link}")

        val text = website.build(post, pl).text

        assertEquals(2, "https://sirelon.github.io/SellSnap/?lang=pl&p=plt0001".toRegex(RegexOption.LITERAL).findAll(text).count(), text)
    }

    private val withRegenerate = Previews(FakeImageStore(), "https://go.test/go/{code}", regenerateAvailable = true)

    private fun labels(previews: Previews, post: SocialPost) = previews.keyboard(post, account)?.inlineKeyboard?.flatten()?.map { it.text }.orEmpty()

    @Test
    fun regenerateStaysHiddenUnlessTheFlagIsOn() {
        val post = testPost(status = PostStatus.Pending).copy(source = PostSource.Drafter)

        assertFalse("Regenerate" in labels(previews, post))
        assertTrue("Regenerate" in labels(withRegenerate, post))
        assertEquals(listOf("r:stt0001"), withRegenerate.keyboard(post, account)!!.inlineKeyboard.last().map { it.callbackData })
    }

    @Test
    fun regenerateShowsOnlyUnderAPendingPostThatTheDrafterWroteAndThatHasVersionsLeft() {
        val draft = testPost(status = PostStatus.Pending).copy(source = PostSource.Drafter)

        assertTrue("Regenerate" in labels(withRegenerate, draft))
        assertTrue("Regenerate" in labels(withRegenerate, draft.copy(regenerations = 2)))
        assertFalse("Regenerate" in labels(withRegenerate, draft.copy(regenerations = 3)))
        assertFalse("Regenerate" in labels(withRegenerate, draft.copy(source = PostSource.Seed)))
        assertFalse("Regenerate" in labels(withRegenerate, draft.copy(source = PostSource.Owner)))
        assertFalse("Regenerate" in labels(withRegenerate, draft.copy(status = PostStatus.Approved)))
        assertFalse("Regenerate" in labels(withRegenerate, draft.copy(status = PostStatus.Skipped)))
    }

    // --- Back-translation and check notes

    private val polish = testAccount(id = "pl", codePrefix = "pl", language = "pl")
    private val english = "Photograph the item by a window."

    @Test
    fun aPolishPostWithABackTranslationAppendsItAfterABlankLine() {
        val post = testPost(status = PostStatus.Pending, scheduledAt = slot).copy(text = "Sfotografuj rzecz przy oknie.", backTranslation = english)

        val text = previews.build(post, polish).text

        assertTrue(text.endsWith("Sfotografuj rzecz przy oknie.\n\nEN: $english"), text)
    }

    @Test
    fun aBackTranslationOnANonPolishAccountOrABlankOneIsNotShown() {
        val post = testPost(status = PostStatus.Pending, scheduledAt = slot).copy(backTranslation = english)

        assertFalse("EN:" in previews.build(post, account).text)
        assertFalse("EN:" in previews.build(post.copy(backTranslation = "  "), polish).text)
        assertFalse("EN:" in previews.build(post.copy(backTranslation = null), polish).text)
    }

    @Test
    fun checkNotesAreAppendedOnlyToAPostThatNeedsAttention() {
        val post = testPost(status = PostStatus.Pending, scheduledAt = slot).copy(checkNotes = "Unsupported product claim: it prints labels")

        val flagged = previews.build(post.copy(needsAttention = true), account).text
        val unflagged = previews.build(post, account).text

        assertTrue(flagged.endsWith("Tip text\n\nCheck: Unsupported product claim: it prints labels"), flagged)
        assertTrue(flagged.startsWith("@sellsnap.staging | stt0001 | Mon 12 Oct 08:30 | needs attention\n\n"))
        assertFalse("Check:" in unflagged)
    }

    @Test
    fun aPolishPostThatNeedsAttentionShowsTheBackTranslationBeforeTheCheckNotes() {
        val post = testPost(status = PostStatus.Pending, scheduledAt = slot)
            .copy(needsAttention = true, backTranslation = english, checkNotes = "Awkward phrase")

        val text = previews.build(post, polish).text

        assertTrue(text.endsWith("Tip text\n\nEN: $english\n\nCheck: Awkward phrase"), text)
    }

    @Test
    fun whenTheAppendedPartsDoNotFitTheCheckNotesAreCutFirstAndThePostTextStaysWhole() {
        val body = "word ".repeat(790).trim()
        val post = testPost(status = PostStatus.Pending, scheduledAt = slot)
            .copy(text = body, needsAttention = true, backTranslation = english, checkNotes = "note ".repeat(200).trim())

        val text = previews.build(post, polish).text

        assertTrue(text.length <= Previews.MESSAGE_LIMIT, "${text.length}")
        assertTrue("\n\n$body\n\nEN: $english\n\nCheck: note" in text)
        assertTrue(text.endsWith("…"))
    }

    @Test
    fun theBackTranslationIsCutBeforeThePostTextAndDropsTheCheckNotesEntirely() {
        val body = "word ".repeat(790).trim()
        val post = testPost(status = PostStatus.Pending, scheduledAt = slot)
            .copy(text = body, needsAttention = true, backTranslation = "line ".repeat(200).trim(), checkNotes = "Awkward phrase")

        val text = previews.build(post, polish).text

        assertTrue(text.length <= Previews.MESSAGE_LIMIT, "${text.length}")
        assertTrue("\n\n$body\n\nEN: line" in text)
        assertTrue(text.endsWith("…"))
        assertFalse("Check:" in text)
    }

    @Test
    fun aPostTextThatFillsTheMessageLeavesNoRoomForTheAppendedParts() {
        val body = "word ".repeat(2000)
        val post = testPost(status = PostStatus.Pending, scheduledAt = slot)
            .copy(text = body, needsAttention = true, backTranslation = english, checkNotes = "Awkward phrase")

        val text = previews.build(post, polish).text

        assertEquals(Previews.MESSAGE_LIMIT, text.length)
        assertFalse("EN:" in text)
        assertFalse("Check:" in text)
    }

    @Test
    fun aCaptionKeepsTheSameOrderWithinItsOwnLimit() {
        val body = "word ".repeat(150).trim()
        val photo = testPost(status = PostStatus.Pending, scheduledAt = slot).copy(
            kind = PostKind.Image,
            imagePaths = listOf("a.jpg"),
            text = body,
            needsAttention = true,
            backTranslation = "line ".repeat(100).trim(),
            checkNotes = "Awkward phrase",
        )

        val caption = previews.build(photo, polish).text

        assertTrue(caption.length <= Previews.CAPTION_LIMIT, "${caption.length}")
        assertTrue("\n\n$body\n\nEN: line" in caption)
        assertFalse("Check:" in caption)
        assertTrue(caption.endsWith("…"))
    }

    @Test
    fun callbackDataIsShortAndCarriesThePostId() {
        val post = testPost(id = "stt0001", status = PostStatus.Pending)

        val data = previews.keyboard(post, account)!!.inlineKeyboard.flatten().map { it.callbackData }

        assertEquals(listOf("a:stt0001", "e:stt0001", "s:stt0001"), data)
        assertTrue(data.all { it.toByteArray().size <= 64 })
    }
}
