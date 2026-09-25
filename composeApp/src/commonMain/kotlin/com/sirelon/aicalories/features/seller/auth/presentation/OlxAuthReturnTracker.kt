package com.sirelon.sellsnap.features.seller.auth.presentation

/**
 * Tells a closed OLX login from a finished one on a platform that reports nothing when the login
 * page is closed.
 *
 * Android opens the login in a Custom Tab (`CustomTabsIntent.launchUrl`), which returns as soon as
 * the tab opens and never calls back when it closes. What Android does guarantee: a finished login
 * comes back through the `selolxai://olx-auth` redirect, delivered to `MainActivity.onNewIntent`,
 * and `onNewIntent` always runs before `onResume` ("An activity can never receive a new intent in
 * the resumed state... you can count on onResume() being called after this method" -
 * developer.android.com, `Activity#onNewIntent`). So when the app resumes after a launch and no new
 * callback has been published since, the seller closed the login. AppAuth-Android's
 * `AuthorizationManagementActivity` detects a cancelled authorization the same way.
 *
 * [publishedCallbacks] must only grow; [OlxAuthCallbackBridge.publishedCount][com.sirelon.sellsnap.features.seller.auth.data.OlxAuthCallbackBridge.publishedCount]
 * is the production source.
 */
class OlxAuthReturnTracker(private val publishedCallbacks: () -> Int) {

    private var callbacksAtLaunch: Int? = null

    fun onLaunched() {
        callbacksAtLaunch = publishedCallbacks()
    }

    /** True once per launch, when the app comes back to the front without the login's callback. */
    fun onResumed(): Boolean {
        val atLaunch = callbacksAtLaunch ?: return false
        callbacksAtLaunch = null
        return publishedCallbacks() == atLaunch
    }
}
