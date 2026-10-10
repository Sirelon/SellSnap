package com.sirelon.sellsnap.telegram

import com.sirelon.sellsnap.fakes.FakeImageStore
import com.sirelon.sellsnap.fakes.testAccount
import com.sirelon.sellsnap.fakes.testPost
import com.sirelon.sellsnap.model.AccountMode
import com.sirelon.sellsnap.model.PostKind
import com.sirelon.sellsnap.model.PostStatus
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PreviewsTest {
    private val previews = Previews(FakeImageStore(), "https://go.test/")
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
    fun theLinkUrlIsBuiltFromTheBaseAndTheCode() {
        assertEquals("https://go.test/go/stt0001", previews.linkUrl("stt0001"))
        assertEquals("https://go.test/go/stt0001", com.sirelon.sellsnap.content.postLinkUrl("https://go.test/", "stt0001"))
    }

    @Test
    fun regenerateStaysHiddenUnlessTheFlagIsOn() {
        val post = testPost(status = PostStatus.Pending)

        val hidden = previews.keyboard(post, account)!!.inlineKeyboard.flatten().map { it.text }
        val shown = Previews(FakeImageStore(), "https://go.test", regenerateAvailable = true)
            .keyboard(post, account)!!.inlineKeyboard.flatten().map { it.text }

        assertFalse("Regenerate" in hidden)
        assertTrue("Regenerate" in shown)
    }

    @Test
    fun callbackDataIsShortAndCarriesThePostId() {
        val post = testPost(id = "stt0001", status = PostStatus.Pending)

        val data = previews.keyboard(post, account)!!.inlineKeyboard.flatten().map { it.callbackData }

        assertEquals(listOf("a:stt0001", "e:stt0001", "s:stt0001"), data)
        assertTrue(data.all { it.toByteArray().size <= 64 })
    }
}
