package com.sirelon.sellsnap.features.seller.drafts

import kotlinx.coroutines.flow.Flow

/**
 * On-device drafts (SIR-133). Country-agnostic: callers filter by the current OLX country.
 *
 * Implementations must swallow their own storage failures: a draft that could not be written is
 * a lost convenience, never a broken generate or publish flow.
 */
interface DraftsRepository {
    /** Every draft, most recently updated first. Emits again after each change. */
    fun drafts(): Flow<List<Draft>>

    suspend fun get(id: String): Draft?

    /** Inserts, or replaces the draft with the same [Draft.id]. */
    suspend fun upsert(draft: Draft)

    suspend fun delete(id: String)

    /** Part of "Delete my SellSnap data". */
    suspend fun deleteAll()
}
