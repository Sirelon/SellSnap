package com.sirelon.sellsnap.features.review

import com.sirelon.sellsnap.datastore.KeyValueStore
import com.sirelon.sellsnap.datastore.createKeyValueStore

/**
 * The two numbers the store-review gate needs across launches: how many adverts this seller has
 * published, and when the native review prompt was last asked for.
 *
 * Neither platform reports whether the prompt was actually shown or whether a rating was left, so
 * "asked for" is the most that can ever be recorded here - see [ReviewPromptCoordinator].
 */
class ReviewPromptStore internal constructor(
    private val storage: KeyValueStore,
) {
    constructor() : this(createKeyValueStore("store_review"))

    suspend fun publishCount(): Int = storage.getString(KEY_PUBLISH_COUNT)?.toIntOrNull() ?: 0

    suspend fun incrementPublishCount() {
        storage.putString(KEY_PUBLISH_COUNT, (publishCount() + 1).toString())
    }

    suspend fun lastPromptEpochSeconds(): Long? =
        storage.getString(KEY_LAST_PROMPT_AT)?.toLongOrNull()

    suspend fun markPromptRequested(nowEpochSeconds: Long) {
        storage.putString(KEY_LAST_PROMPT_AT, nowEpochSeconds.toString())
    }

    private companion object {
        const val KEY_PUBLISH_COUNT = "successful_publish_count"
        const val KEY_LAST_PROMPT_AT = "last_prompt_epoch_seconds"
    }
}
