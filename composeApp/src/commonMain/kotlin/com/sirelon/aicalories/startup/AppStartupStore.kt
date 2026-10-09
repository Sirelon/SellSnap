package com.sirelon.sellsnap.startup

import com.sirelon.sellsnap.datastore.KeyValueStore
import com.sirelon.sellsnap.datastore.createKeyValueStore

class AppStartupStore internal constructor(
    private val store: KeyValueStore,
) {
    constructor() : this(createKeyValueStore("app_startup"))

    /** True once the seller has tapped through the onboarding's final button. */
    suspend fun hasSeenOnboarding(): Boolean =
        store.getString(KEY_ONBOARDING_SEEN) != null

    suspend fun markOnboardingSeen() {
        store.putString(KEY_ONBOARDING_SEEN, "true")
    }

    /**
     * Records that the app was opened and answers whether it had been opened before. Installs
     * that finished onboarding count as opened before even without the opened marker.
     */
    suspend fun recordLaunch(): Boolean {
        val markedOpened = store.getString(KEY_APP_OPENED) != null
        if (!markedOpened) {
            store.putString(KEY_APP_OPENED, "true")
        }
        return markedOpened || hasSeenOnboarding()
    }

    private companion object {
        const val KEY_ONBOARDING_SEEN = "has_seen_onboarding"
        const val KEY_APP_OPENED = "has_opened_app"
    }
}
