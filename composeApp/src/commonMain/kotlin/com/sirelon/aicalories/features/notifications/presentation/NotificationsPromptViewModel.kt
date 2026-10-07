package com.sirelon.sellsnap.features.notifications.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sirelon.sellsnap.analytics.Analytics
import com.sirelon.sellsnap.analytics.AnalyticsEvents
import com.sirelon.sellsnap.features.notifications.data.PushNotificationsRepository
import com.sirelon.sellsnap.features.review.ReviewPromptCoordinator
import kotlinx.coroutines.launch

/**
 * Decides whether the one-time "turn on notifications" sheet opens at launch, and records how it
 * was answered. The sheet is the third launch dialog, after an announcement and What's New.
 */
class NotificationsPromptViewModel(
    private val repository: PushNotificationsRepository,
    private val reviewPromptCoordinator: ReviewPromptCoordinator,
    private val analytics: Analytics,
) : ViewModel() {

    private var answered = false

    /**
     * True when the sheet should open now: this is a second or later session and the repository
     * still offers the prompt. Showing counts as shown - the flag is persisted here, so the sheet
     * is never offered again whatever the user does with it.
     */
    suspend fun shouldShowPrompt(): Boolean {
        if (!reviewPromptCoordinator.isReturningSession || !repository.shouldOfferPrompt()) return false
        repository.markPromptShown()
        // One uninvited interruption per session: see ReviewPromptGate.
        reviewPromptCoordinator.notificationPromptShownThisSession = true
        answered = false
        return true
    }

    /**
     * Logs the answer and refreshes the `notifications_enabled` property. Only the first call per
     * showing counts, because a swipe-away also reports after an answer has been given.
     *
     * [granted] is only meaningful when [enabled] is true.
     */
    fun onAnswered(enabled: Boolean, granted: Boolean) {
        if (answered) return
        answered = true
        val params = buildMap<String, Any> {
            put("choice", if (enabled) "enable" else "not_now")
            if (enabled) put("granted", granted)
        }
        analytics.logEvent(AnalyticsEvents.NOTIFICATION_PROMPT_ANSWERED, params)
        viewModelScope.launch { repository.reportPermission() }
    }
}
