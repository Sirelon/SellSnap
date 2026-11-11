package com.sirelon.sellsnap.features.seller.categories.data

import com.sirelon.sellsnap.features.seller.auth.data.OlxApiClient
import com.sirelon.sellsnap.features.seller.auth.data.OlxCountryStore
import com.sirelon.sellsnap.features.seller.auth.domain.OlxCountry
import com.sirelon.sellsnap.features.seller.categories.domain.CategoriesMapper
import com.sirelon.sellsnap.features.seller.categories.domain.OlxAttribute
import com.sirelon.sellsnap.features.seller.categories.domain.OlxCategory
import com.sirelon.sellsnap.features.seller.categories.domain.excludedRootCategoryIds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn

/**
 * Thrown when OLX suggests a category for a listing that this app doesn't support publishing to
 * (e.g. the suggestion falls under an excluded root like Animals or Real Estate, see
 * [excludedRootCategoryIds]) - callers surface this as a normal generation/suggestion failure
 * instead of the flow silently completing with no result.
 */
class UnsupportedOlxCategoryException(title: String) :
    Exception("OLX suggested a category for \"$title\" that this app does not support.")

class CategoriesRepository(
    private val olxApiClient: OlxApiClient,
    private val mapper: CategoriesMapper,
    private val scope: CoroutineScope,
    private val countryStore: OlxCountryStore,
) {

    // Errors are wrapped as Result so the sharing coroutine is never cancelled by a network failure.
    // Callers unwrap with getOrThrow(), propagating the exception to their own collector/caller.
    private fun newCategoriesCache(country: OlxCountry): SharedFlow<Result<List<OlxCategory>>> = flow {
        emit(runCatching { loadSupportedCategories(country) })
    }.shareIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        replay = 1,
    )

    // The tree is per market: OlxConfig.apiBaseUrl follows the current country, and so do the
    // excluded roots. A guest can change country mid-flow, and the saved country loads
    // asynchronously at startup, so the cache is kept only for the country it was loaded for.
    private var categoriesCache: Pair<OlxCountry, SharedFlow<Result<List<OlxCategory>>>>? = null

    private fun cacheFor(country: OlxCountry): SharedFlow<Result<List<OlxCategory>>> =
        categoriesCache?.takeIf { it.first == country }?.second
            ?: newCategoriesCache(country).also { categoriesCache = country to it }

    // A failure is handed out once and then dropped, so the next call (a retry) loads again
    // instead of replaying the cached failure.
    fun loadCategories(): Flow<List<OlxCategory>> = flow {
        val cache = cacheFor(countryStore.current)
        val result = cache.first()
        if (result.isFailure && categoriesCache?.second === cache) categoriesCache = null
        emit(result.getOrThrow())
    }

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
        val response = olxApiClient.loadCategorySuggestionId(title)
            ?: throw UnsupportedOlxCategoryException(title)
        val category = getCategoryById(response) ?: throw UnsupportedOlxCategoryException(title)
        emit(category)
    }

    private suspend fun loadSupportedCategories(country: OlxCountry): List<OlxCategory> {
        val result = olxApiClient.loadCategories()
        val data = result.mapNotNull(mapper::mapCategory)

        return normalize(data, country)
    }

    private fun normalize(data: List<OlxCategory>, country: OlxCountry): List<OlxCategory> {
        val notSupportedRootIds = excludedRootCategoryIds(country)
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
