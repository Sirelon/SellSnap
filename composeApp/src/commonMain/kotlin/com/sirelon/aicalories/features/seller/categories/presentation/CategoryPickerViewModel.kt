package com.sirelon.sellsnap.features.seller.categories.presentation

import androidx.lifecycle.viewModelScope
import com.sirelon.sellsnap.analytics.Analytics
import com.sirelon.sellsnap.analytics.reportShownError
import com.sirelon.sellsnap.features.common.presentation.BaseViewModel
import com.sirelon.sellsnap.features.seller.auth.domain.shownErrorCause
import com.sirelon.sellsnap.features.seller.categories.data.CategoriesRepository
import com.sirelon.sellsnap.features.seller.categories.domain.OlxCategory
import com.sirelon.sellsnap.features.seller.categories.presentation.CategoryPickerContract.CategoryPickerEvent
import com.sirelon.sellsnap.features.seller.categories.presentation.CategoryPickerContract.CategoryPickerEvent.NavigateTo
import com.sirelon.sellsnap.features.seller.categories.presentation.CategoryPickerContract.CategoryPickerEvent.NavigateToIndex
import com.sirelon.sellsnap.features.seller.categories.presentation.CategoryPickerContract.CategoryPickerEvent.Reset
import com.sirelon.sellsnap.features.seller.categories.presentation.CategoryPickerContract.CategoryPickerEvent.Retry
import com.sirelon.sellsnap.features.seller.categories.presentation.CategoryPickerContract.CategoryPickerEvent.Search
import com.sirelon.sellsnap.features.seller.categories.presentation.CategoryPickerContract.CategoryPickerState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class CategoryPickerViewModel(
    private val categoriesRepository: CategoriesRepository,
    private val analytics: Analytics,
) : BaseViewModel<CategoryPickerState, CategoryPickerEvent, Nothing>() {

    override fun initialState() = CategoryPickerState()

    private var loadJob: Job? = null

    init {
        load()
    }

    private fun load() {
        loadJob?.cancel()
        setState { it.copy(isLoading = true, loadFailed = false) }
        loadJob = viewModelScope.launch {
            try {
                categoriesRepository.loadCategories().collect { categories ->
                    setState { s ->
                        val next = s.copy(allCategories = categories, isLoading = false)
                        next.copy(displayItems = computeDisplayItems(next))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                analytics.reportShownError(
                    screen = "category_picker",
                    reason = "load_failed:${e.shownErrorCause}",
                    throwable = e,
                )
                setState { it.copy(isLoading = false, loadFailed = true) }
            }
        }
    }

    override fun onEvent(event: CategoryPickerEvent) = when (event) {
        is NavigateTo -> setState { s ->
            val next = s.copy(path = s.path + event.category, searchQuery = "")
            next.copy(displayItems = computeDisplayItems(next))
        }
        is NavigateToIndex -> setState { s ->
            val next = s.copy(path = s.path.take(event.index), searchQuery = "")
            next.copy(displayItems = computeDisplayItems(next))
        }
        Reset -> setState { s ->
            val next = s.copy(path = emptyList(), searchQuery = "")
            next.copy(displayItems = computeDisplayItems(next))
        }
        Retry -> load()
        is Search -> setState { s ->
            val next = s.copy(searchQuery = event.query)
            next.copy(displayItems = computeDisplayItems(next))
        }
    }

    private fun computeDisplayItems(state: CategoryPickerState): List<CategoryDisplayItem> {
        val all = state.allCategories
        return if (state.searchQuery.isNotBlank()) {
            val q = state.searchQuery.trim().lowercase()
            all.filter { it.label.lowercase().contains(q) }
                .take(30)
                .map { CategoryDisplayItem(category = it, parentChain = buildParentChain(all, it)) }
        } else {
            val currentParentId = state.path.lastOrNull()?.id
            all.filter { it.parentId == currentParentId }
                .map { CategoryDisplayItem(category = it) }
        }
    }

    private fun buildParentChain(all: List<OlxCategory>, category: OlxCategory): String {
        val chain = mutableListOf<String>()
        var current = category
        while (true) {
            val parent = all.find { it.id == current.parentId } ?: break
            chain.add(parent.label)
            current = parent
        }
        return chain.reversed().joinToString(" › ")
    }
}
