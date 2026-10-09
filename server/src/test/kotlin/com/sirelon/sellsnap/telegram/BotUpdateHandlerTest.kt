package com.sirelon.sellsnap.telegram

import com.sirelon.sellsnap.telegram.BotFixture.Companion.NOW
import kotlinx.coroutines.test.runTest
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Webhook behaviour: update dedup, the owner whitelist and the error reply. */
class BotUpdateHandlerTest {
    private val bot = BotFixture()

    @Test
    fun anUpdateIdIsProcessedOnceAndExpiresInSevenDays() = runTest {
        bot.say("/approval staging", updateId = 77)
        bot.say("/approval staging", updateId = 77)

        assertEquals(1, bot.sentTexts().size)
        assertEquals(NOW.plus(Duration.ofDays(7)), bot.store.updates.getValue(77))
    }

    @Test
    fun aRedeliveredButtonTapDoesNothingTheSecondTime() = runTest {
        bot.putPending()

        bot.tap("a:stt0001", updateId = 5)
        bot.tap("a:stt0001", updateId = 5)

        assertEquals(1, bot.answers().size)
        assertEquals(1, bot.account().approvalStreak)
    }

    @Test
    fun aNonOwnerUpdateDoesNotUseUpAnUpdateId() = runTest {
        bot.say("/approval staging", from = 9, updateId = 31)

        assertTrue(bot.store.updates.isEmpty())
        assertTrue(bot.telegram.calls.isEmpty())
    }

    @Test
    fun aFailureBecomesARedactedOneLineReplyAndHandleReturnsNormally() = runTest {
        val leak = "https://api.telegram.org/bot123456789:AAFakeTokenForTestsOnly_abcdefghij12345/sendMessage?access_token=SECRET123\nline two"
        bot.telegram.failNext("sendMessage", TelegramFailure(leak))

        bot.say("/frobnicate")

        val reply = bot.sentTexts().single()
        assertTrue(reply.startsWith("Something went wrong: "), reply)
        assertFalse("AAFakeToken" in reply)
        assertFalse("SECRET123" in reply)
        assertFalse('\n' in reply)
    }

    @Test
    fun aFailingButtonAnswersTheTapAndReplies() = runTest {
        bot.putPending()
        bot.telegram.failNext("answerCallbackQuery", TelegramFailure("Telegram rejected answerCallbackQuery (HTTP 400)"))

        bot.tap("a:stt0001")

        // The approval itself went through; the owner is told about the failed answer.
        assertEquals(com.sirelon.sellsnap.model.PostStatus.Approved, bot.status("stt0001"))
        assertTrue(bot.sentTexts().single().startsWith("Something went wrong: "))
        assertEquals(1, bot.answers().size)
    }

    @Test
    fun aPhotoWithoutACommandIsIgnored() = runTest {
        bot.say("hello", photoFileId = "p1")

        assertTrue(bot.telegram.calls.isEmpty())
    }
}
