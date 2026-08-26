package com.sirelon.sellsnap.features.seller.categories.data

import com.sirelon.sellsnap.features.seller.auth.data.OlxApiClient
import com.sirelon.sellsnap.features.seller.auth.data.OlxCountryStore
import com.sirelon.sellsnap.features.seller.categories.domain.CategoriesMapper
import com.sirelon.sellsnap.features.seller.categories.domain.OlxAttribute
import com.sirelon.sellsnap.features.seller.categories.domain.OlxCategory
import com.sirelon.sellsnap.features.seller.categories.domain.excludedRootCategoryIds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn

class CategoriesRepository(
    private val olxApiClient: OlxApiClient,
    private val mapper: CategoriesMapper,
    private val scope: CoroutineScope,
    private val countryStore: OlxCountryStore,
) {

    // Errors are wrapped as Result so the sharing coroutine is never cancelled by a network failure.
    // Callers unwrap with getOrThrow(), propagating the exception to their own collector/caller.
    private val categoriesCache: SharedFlow<Result<List<OlxCategory>>> = flow {
        emit(runCatching { loadSupportedCategories() })
    }.shareIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        replay = 1,
    )

    fun loadCategories(): Flow<List<OlxCategory>> = categoriesCache.map { it.getOrThrow() }

    fun getRootCategories(): Flow<List<OlxCategory>> =
        loadCategories().map { all -> all.filter { it.parentId == null } }

    fun getSubcategories(parentId: Int): Flow<List<OlxCategory>> =
        loadCategories().map { all -> all.filter { it.parentId == parentId } }

    suspend fun getCategoryById(id: Int): OlxCategory? =
        loadCategories().first().find { it.id == id }

    fun getAttributes(categoryId: Int): Flow<List<OlxAttribute>> = flow {
        val response = olxApiClient.loadAttributes(categoryId)
        emit(mapper.mapAttributes(response))
    }

    fun categorySuggestion(title: String): Flow<OlxCategory> = flow {
        println("[AdGen] CategoriesRepository: categorySuggestion(\"$title\") started")
        val response = olxApiClient.loadCategorySuggestionId(title)
        if (response == null) {
            println("[AdGen] CategoriesRepository: OLX returned no suggested category for \"$title\" — flow will complete with no value")
            emit(null)
        } else {
            val category = getCategoryById(response)
            if (category == null) {
                println("[AdGen] CategoriesRepository: suggested category id=$response not found locally — flow will complete with no value")
            }
            emit(category)
        }
    }
        .filterNotNull()

    private suspend fun loadSupportedCategories(): List<OlxCategory> {
        println("[AdGen] CategoriesRepository: loadSupportedCategories started")
        val result = olxApiClient.loadCategories()
        val data = result.mapNotNull(mapper::mapCategory)

        return normalize(data).also {
            println("[AdGen] CategoriesRepository: loadSupportedCategories resolved ${it.size} categories")
        }
    }

    private fun normalize(data: List<OlxCategory>): List<OlxCategory> {
        val notSupportedRootIds = excludedRootCategoryIds(countryStore.current)
        val grouppedData = data.groupBy { it.parentId }.toMutableMap()

        val rootCategories = grouppedData[null].orEmpty()
        val toRemove = rootCategories.filter { notSupportedRootIds.contains(it.id) }

        grouppedData[null] = rootCategories - toRemove

        grouppedData.removeLeaves(toRemove)

        return grouppedData
            .values
            .flatten()
            .toList()
    }

    private fun MutableMap<Int?, List<OlxCategory>>.removeLeaves(toRemove: List<OlxCategory>) {
        if (toRemove.isEmpty()) return

        val removeSub = toRemove
            .flatMap { this.remove(it.id).orEmpty() }

        removeLeaves(removeSub)
    }
}
