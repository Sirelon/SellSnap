package com.sirelon.sellsnap.features.seller.drafts

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Process-lifetime drafts: the web targets, which have no SQLite driver, and tests. */
class InMemoryDraftsRepository : DraftsRepository {

    private val byId = MutableStateFlow<Map<String, Draft>>(emptyMap())

    override fun drafts(): Flow<List<Draft>> =
        byId.map { drafts -> drafts.values.sortedByDescending { it.updatedAtEpochSeconds } }

    override suspend fun get(id: String): Draft? = byId.value[id]

    override suspend fun upsert(draft: Draft) {
        byId.update { it + (draft.id to draft) }
    }

    override suspend fun delete(id: String) {
        byId.update { it - id }
    }

    override suspend fun deleteAll() {
        byId.value = emptyMap()
    }
}
