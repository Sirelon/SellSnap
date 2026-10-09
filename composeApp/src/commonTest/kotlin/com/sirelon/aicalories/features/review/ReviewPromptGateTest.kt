package com.sirelon.sellsnap.features.review

import kotlin.test.Test
import kotlin.test.assertEquals

private const val Now = 1_800_000_000L

private fun decide(
    trigger: ReviewPromptTrigger = ReviewPromptTrigger.Publish,
    publishCount: Int = 2,
    isReturningSession: Boolean = true,
    hadPublishErrorThisSession: Boolean = false,
    whatsNewShownThisSession: Boolean = false,
    announcementShownThisSession: Boolean = false,
    notificationPromptShownThisSession: Boolean = false,
    lastPromptEpochSeconds: Long? = null,
) = reviewPromptDecision(
    trigger = trigger,
    count = publishCount,
    isReturningSession = isReturningSession,
    hadPublishErrorThisSession = hadPublishErrorThisSession,
    whatsNewShownThisSession = whatsNewShownThisSession,
    announcementShownThisSession = announcementShownThisSession,
    notificationPromptShownThisSession = notificationPromptShownThisSession,
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
    fun `the notifications prompt is the one interruption this session gets`() {
        assertSkip(
            ReviewPromptSkipReason.NotificationPrompt,
            decide(notificationPromptShownThisSession = true),
        )
    }

    @Test
    fun `an announcement is the one interruption this session gets`() {
        assertSkip(
            ReviewPromptSkipReason.Announcement,
            decide(announcementShownThisSession = true),
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
                hadPublishErrorThisSession = true,
                lastPromptEpochSeconds = Now - 1,
            ),
        )
        assertSkip(
            ReviewPromptSkipReason.RecentError,
            decide(
                hadPublishErrorThisSession = true,
                whatsNewShownThisSession = true,
                lastPromptEpochSeconds = Now - 1,
            ),
        )
    }

    @Test
    fun `four copied listings are not enough`() {
        assertSkip(
            ReviewPromptSkipReason.TooFewCopiedListings,
            decide(trigger = ReviewPromptTrigger.CopiedListing, publishCount = 4),
        )
    }

    @Test
    fun `the fifth copied listing is asked for`() {
        assertEquals(
            ReviewPromptDecision.Request,
            decide(trigger = ReviewPromptTrigger.CopiedListing, publishCount = ReviewPromptMinCopiedListings),
        )
    }

    @Test
    fun `a guest has to come back before being asked, however many listings were copied`() {
        assertSkip(
            ReviewPromptSkipReason.InstallSession,
            decide(trigger = ReviewPromptTrigger.CopiedListing, publishCount = 50, isReturningSession = false),
        )
    }

    @Test
    fun `a second ask in the same version is skipped by the shared cooldown`() {
        assertSkip(
            ReviewPromptSkipReason.Cooldown,
            decide(
                trigger = ReviewPromptTrigger.CopiedListing,
                publishCount = 6,
                lastPromptEpochSeconds = Now - 1,
            ),
        )
    }

    @Test
    fun `a publish after a copied-listing ask is skipped by the shared cooldown`() {
        // Both triggers read the one timestamp the copied_listing request wrote.
        assertSkip(
            ReviewPromptSkipReason.Cooldown,
            decide(trigger = ReviewPromptTrigger.Publish, lastPromptEpochSeconds = Now - 1),
        )
    }

    @Test
    fun `three cooldowns cannot fit inside one year, which is Apple's cap`() {
        val yearInSeconds = 365L * 24 * 60 * 60
        assertEquals(true, ReviewPromptCooldownSeconds * 3 > yearInSeconds)
    }
}
