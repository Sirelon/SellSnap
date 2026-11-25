package com.sirelon.sellsnap.features.seller.categories.data

import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountState
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountStore
import com.sirelon.sellsnap.features.seller.auth.data.OlxApiClient
import com.sirelon.sellsnap.features.seller.auth.data.OlxCountryStore
import com.sirelon.sellsnap.features.seller.auth.domain.OlxApiError
import com.sirelon.sellsnap.features.seller.auth.domain.OlxApiException
import com.sirelon.sellsnap.features.seller.auth.domain.OlxCountry
import com.sirelon.sellsnap.features.seller.categories.domain.CategoriesMapper
import com.sirelon.sellsnap.features.seller.categories.domain.OlxAttribute
import com.sirelon.sellsnap.features.seller.categories.domain.OlxCategory
import com.sirelon.sellsnap.features.seller.categories.domain.excludedRootCategoryIds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Thrown when OLX suggests a category for a listing that this app doesn't support publishing to
 * (e.g. the suggestion falls under an excluded root like Animals or Real Estate, see
 * [excludedRootCategoryIds]) - callers surface this as a normal generation/suggestion failure
 * instead of the flow silently completing with no result.
 */
class UnsupportedOlxCategoryException(title: String) :
    Exception("OLX suggested a category for \"$title\" that this app does not support.")

/**
 * OLX's catalog for the current market. It reads with the active seller's token while that
 * account works, and with the app's own token ([appApiClient]) when the market has no usable
 * account, so a seller whose OLX connection expired still gets a category and its attributes
 * (SIR-201). The seller's token stays first: it is what every healthy seller has always used.
 */
class CategoriesRepository internal constructor(
    private val olxApiClient: OlxApiClient,
    private val appApiClient: OlxApiClient,
    private val accountStore: OlxAccountStore,
    private val mapper: CategoriesMapper,
    private val countryStore: OlxCountryStore,
) {

    // The tree is per market: OlxConfig.apiBaseUrl follows the current country, and so do the
    // excluded roots. A guest can change country mid-flow, and the saved country loads
    // asynchronously at startup, so a tree is kept only for the country it was loaded for.
    // Only a tree that loaded is kept: a failure goes to its caller and the next call loads again,
    // and a fetch that fails offline never replaces a tree the seller is already picking from.
    private var cachedTree: Pair<OlxCountry, List<OlxCategory>>? = null
    private val loadMutex = Mutex()

    fun loadCategories(): Flow<List<OlxCategory>> = flow { emit(categoriesFor(countryStore.current)) }

    // The mutex makes concurrent callers share one fetch instead of each starting their own.
    private suspend fun categoriesFor(country: OlxCountry): List<OlxCategory> = loadMutex.withLock {
        cachedTree?.takeIf { it.first == country }?.second
            ?: loadSupportedCategories(country).also { cachedTree = country to it }
    }

    fun getRootCategories(): Flow<List<OlxCategory>> =
        loadCategories().map { all -> all.filter { it.parentId == null } }

    fun getSubcategories(parentId: Int): Flow<List<OlxCategory>> =
        loadCategories().map { all -> all.filter { it.parentId == parentId } }

    suspend fun getCategoryById(id: Int): OlxCategory? =
        loadCategories().first().find { it.id == id }

    fun getAttributes(categoryId: Int): Flow<List<OlxAttribute>> = flow {
        val response = catalog { loadAttributes(categoryId) }
        emit(mapper.mapAttributes(response))
    }

    fun categorySuggestion(title: String): Flow<OlxCategory> = flow {
        val response = catalog { loadCategorySuggestionId(title) }
            ?: throw UnsupportedOlxCategoryException(title)
        val category = getCategoryById(response) ?: throw UnsupportedOlxCategoryException(title)
        emit(category)
    }

    private suspend fun loadSupportedCategories(country: OlxCountry): List<OlxCategory> {
        val result = catalog { loadCategories() }
        val data = result.mapNotNull(mapper::mapCategory)

        return normalize(data, country)
    }

    /**
     * A seller token that dies on this very call - its refresh refused with `invalid_grant` or
     * `invalid_token`, which also marks the account NeedsReconnect - gets the call retried with
     * the app's token, so the seller never sees that failure here.
     */
    private suspend fun <T> catalog(call: suspend OlxApiClient.() -> T): T {
        if (!hasUsableAccount()) return appApiClient.call()
        return try {
            olxApiClient.call()
        } catch (error: OlxApiException) {
            if (error.error is OlxApiError.InvalidGrant || error.error is OlxApiError.InvalidToken) {
                appApiClient.call()
            } else {
                throw error
            }
        }
    }

    private fun hasUsableAccount(): Boolean {
        val record = accountStore.recordFlow.value
        val activeIndex = record.activeByCountry[countryStore.current.code] ?: return false
        return record.accounts.any { it.localIndex == activeIndex && it.state == OlxAccountState.Usable }
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
