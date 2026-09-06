package com.sirelon.sellsnap.features.review

import com.sirelon.sellsnap.features.seller.ad.publish_success.AdvertStatus
import kotlin.test.Test
import kotlin.test.assertEquals

private const val Now = 1_800_000_000L

private fun decide(
    publishCount: Int = 2,
    status: AdvertStatus = AdvertStatus.New,
    isReturningSession: Boolean = true,
    hadPublishErrorThisSession: Boolean = false,
    whatsNewShownThisSession: Boolean = false,
    lastPromptEpochSeconds: Long? = null,
) = reviewPromptDecision(
    publishCount = publishCount,
    status = status,
    isReturningSession = isReturningSession,
    hadPublishErrorThisSession = hadPublishErrorThisSession,
    whatsNewShownThisSession = whatsNewShownThisSession,
    lastPromptEpochSeconds = lastPromptEpochSeconds,
    nowEpochSeconds = Now,
)

private fun assertSkip(reason: ReviewPromptSkipReason, decision: ReviewPromptDecision) =
    assertEquals(ReviewPromptDecision.Skip(reason), decision)

class ReviewPromptGateTest {

    @Test
    fun `returning seller with two publishes is asked`() {
        assertEquals(ReviewPromptDecision.Request, decide(publishCount = 2))
    }

    @Test
    fun `first publish is never enough`() {
        assertSkip(ReviewPromptSkipReason.TooFewPublishes, decide(publishCount = 1))
        assertSkip(ReviewPromptSkipReason.TooFewPublishes, decide(publishCount = 0))
    }

    @Test
    fun `two publishes in the install session are not enough`() {
        assertSkip(
            ReviewPromptSkipReason.InstallSession,
            decide(publishCount = 2, isReturningSession = false),
        )
    }

    @Test
    fun `three publishes in the install session are enough`() {
        assertEquals(
            ReviewPromptDecision.Request,
            decide(publishCount = 3, isReturningSession = false),
        )
    }

    @Test
    fun `only new and active adverts are worth celebrating`() {
        assertEquals(ReviewPromptDecision.Request, decide(status = AdvertStatus.New))
        assertEquals(ReviewPromptDecision.Request, decide(status = AdvertStatus.Active))
        listOf(
            AdvertStatus.Limited,
            AdvertStatus.Unpaid,
            AdvertStatus.Unconfirmed,
            AdvertStatus.Moderated,
            AdvertStatus.Blocked,
            AdvertStatus.Disabled,
            AdvertStatus.RemovedByModerator,
            AdvertStatus.RemovedByUser,
            AdvertStatus.Outdated,
            AdvertStatus.Unknown,
        ).forEach { status ->
            assertSkip(ReviewPromptSkipReason.BadStatus, decide(status = status))
        }
    }

    @Test
    fun `an earlier failure in the same session suppresses the ask`() {
        assertSkip(
            ReviewPromptSkipReason.RecentError,
            decide(hadPublishErrorThisSession = true),
        )
    }

    @Test
    fun `the whats new sheet is the one interruption this session gets`() {
        assertSkip(
            ReviewPromptSkipReason.WhatsNew,
            decide(whatsNewShownThisSession = true),
        )
    }

    @Test
    fun `the cooldown runs to the second`() {
        val oneSecondShort = Now - ReviewPromptCooldownSeconds + 1
        assertSkip(
            ReviewPromptSkipReason.Cooldown,
            decide(lastPromptEpochSeconds = oneSecondShort),
        )
        assertEquals(
            ReviewPromptDecision.Request,
            decide(lastPromptEpochSeconds = Now - ReviewPromptCooldownSeconds),
        )
    }

    @Test
    fun `the reported reason follows a fixed order when several apply`() {
        // The skip histogram is read as a diagnosis, so which reason wins has to be stable: a
        // seller who has published once AND is inside the cooldown is a funnel problem, not a
        // cooldown one.
        assertSkip(
            ReviewPromptSkipReason.TooFewPublishes,
            decide(
                publishCount = 1,
                status = AdvertStatus.Blocked,
                hadPublishErrorThisSession = true,
                lastPromptEpochSeconds = Now - 1,
            ),
        )
        assertSkip(
            ReviewPromptSkipReason.BadStatus,
            decide(
                status = AdvertStatus.Blocked,
                hadPublishErrorThisSession = true,
                whatsNewShownThisSession = true,
            ),
        )
    }

    @Test
    fun `three cooldowns cannot fit inside one year, which is Apple's cap`() {
        val yearInSeconds = 365L * 24 * 60 * 60
        assertEquals(true, ReviewPromptCooldownSeconds * 3 > yearInSeconds)
    }
}
