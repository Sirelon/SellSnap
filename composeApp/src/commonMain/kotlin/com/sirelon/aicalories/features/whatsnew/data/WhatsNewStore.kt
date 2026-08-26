package com.sirelon.sellsnap.features.whatsnew.data

import com.sirelon.sellsnap.datastore.createKeyValueStore

class WhatsNewStore {
    private val store = createKeyValueStore("whats_new")

    suspend fun lastSeenVersion(): String? = store.getString(KEY_LAST_SEEN_VERSION)

    suspend fun markVersionSeen(version: String) {
        store.putString(KEY_LAST_SEEN_VERSION, version)
    }

    private companion object {
        const val KEY_LAST_SEEN_VERSION = "last_seen_version"
    }
}
