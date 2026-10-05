package com.sirelon.sellsnap.features.seller.drafts.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.sirelon.sellsnap.db.SellSnapDatabase
import com.sirelon.sellsnap.features.seller.ad.AdvertisementWithAttributes
import com.sirelon.sellsnap.features.seller.drafts.Draft
import com.sirelon.sellsnap.features.seller.drafts.DraftsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.sirelon.sellsnap.db.Draft as DraftRow

/**
 * [DraftsRepository] over the `draft` table of [SellSnapDatabase]. The listing is stored as the
 * [json] encoding of [AdvertisementWithAttributes]; a row whose JSON no longer decodes is left in
 * the table and left out of [drafts].
 */
internal class SqlDelightDraftsRepository(
    database: SellSnapDatabase,
    private val json: Json,
) : DraftsRepository {

    private val queries = database.draftQueries

    override fun drafts(): Flow<List<Draft>> =
        queries.selectAll()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.mapNotNull { it.toDraft(json) } }
            .catch { emit(emptyList()) }

    override suspend fun get(id: String): Draft? =
        inBackground { queries.selectById(id).executeAsOneOrNull()?.toDraft(json) }

    override suspend fun upsert(draft: Draft) {
        inBackground {
            queries.upsert(
                id = draft.id,
                country_code = draft.countryCode,
                created_at_epoch_seconds = draft.createdAtEpochSeconds,
                updated_at_epoch_seconds = draft.updatedAtEpochSeconds,
                listing_json = json.encodeToString(draft.listing),
            )
        }
    }

    override suspend fun delete(id: String) {
        inBackground { queries.deleteById(id) }
    }

    override suspend fun deleteAll() {
        inBackground { queries.deleteAll() }
    }

    /**
     * Runs [block] on [Dispatchers.Default]: `Dispatchers.IO` is not visible from the source set
     * shared by Android, JVM and iOS. A storage failure yields null; a cancellation propagates.
     */
    private suspend fun <T> inBackground(block: () -> T): T? =
        runCatching { withContext(Dispatchers.Default) { block() } }
            .onFailure { if (it is CancellationException) throw it }
            .getOrNull()
}

private fun DraftRow.toDraft(json: Json): Draft? {
    val listing = runCatching { json.decodeFromString<AdvertisementWithAttributes>(listing_json) }
        .getOrNull() ?: return null
    return Draft(
        id = id,
        countryCode = country_code,
        createdAtEpochSeconds = created_at_epoch_seconds,
        updatedAtEpochSeconds = updated_at_epoch_seconds,
        listing = listing,
    )
}
