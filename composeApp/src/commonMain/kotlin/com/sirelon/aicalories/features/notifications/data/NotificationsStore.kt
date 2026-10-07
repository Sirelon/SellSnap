package com.sirelon.sellsnap.features.notifications.data

import com.sirelon.sellsnap.datastore.KeyValueStore
import com.sirelon.sellsnap.datastore.createKeyValueStore

/**
 * Whether the notification prompt was already offered, whether the OS permission was ever
 * requested, and which language topic this device is subscribed to - the one to leave when the
 * device language changes.
 */
class NotificationsStore internal constructor(
    private val storage: KeyValueStore,
) {
    constructor() : this(createKeyValueStore("notifications"))

    suspend fun isPromptShown(): Boolean = storage.getString(KEY_PROMPT_SHOWN) != null

    suspend fun markPromptShown() {
        storage.putString(KEY_PROMPT_SHOWN, SHOWN)
    }

    suspend fun wasPermissionRequested(): Boolean = storage.getString(KEY_PERMISSION_REQUESTED) != null

    suspend fun markPermissionRequested() {
        storage.putString(KEY_PERMISSION_REQUESTED, SHOWN)
    }

    suspend fun languageTopic(): String? = storage.getString(KEY_LANGUAGE_TOPIC)

    suspend fun setLanguageTopic(topic: String) {
        storage.putString(KEY_LANGUAGE_TOPIC, topic)
    }

    private companion object {
        const val KEY_PROMPT_SHOWN = "prompt_shown"
        const val KEY_PERMISSION_REQUESTED = "permission_requested"
        const val KEY_LANGUAGE_TOPIC = "language_topic"
        const val SHOWN = "1"
    }
}
