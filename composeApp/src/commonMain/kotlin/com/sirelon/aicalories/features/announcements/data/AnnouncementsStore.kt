package com.sirelon.sellsnap.features.announcements.data

import com.sirelon.sellsnap.datastore.KeyValueStore
import com.sirelon.sellsnap.datastore.createKeyValueStore

/**
 * Which `once` announcements this device has already been shown, one key per id. Editing a
 * published document does not re-show it - publish a new id instead.
 */
class AnnouncementsStore internal constructor(
    private val storage: KeyValueStore,
) {
    constructor() : this(createKeyValueStore("announcements"))

    suspend fun isSeen(id: String): Boolean = storage.getString(seenKey(id)) != null

    suspend fun markSeen(id: String) {
        storage.putString(seenKey(id), SEEN)
    }

    private fun seenKey(id: String) = "seen_$id"

    private companion object {
        const val SEEN = "1"
    }
}
