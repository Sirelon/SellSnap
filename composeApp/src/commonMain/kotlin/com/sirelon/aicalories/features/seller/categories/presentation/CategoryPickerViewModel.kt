package com.sirelon.sellsnap.features.seller.categories.presentation

import androidx.lifecycle.viewModelScope
import com.sirelon.sellsnap.features.common.presentation.BaseViewModel
import com.sirelon.sellsnap.features.seller.categories.data.CategoriesRepository
import com.sirelon.sellsnap.features.seller.categories.domain.OlxCategory
import com.sirelon.sellsnap.features.seller.categories.presentation.CategoryPickerContract.CategoryPickerEvent
import com.sirelon.sellsnap.features.seller.categories.presentation.CategoryPickerContract.CategoryPickerEvent.NavigateTo
import com.sirelon.sellsnap.features.seller.categories.presentation.CategoryPickerContract.CategoryPickerEvent.NavigateToIndex
import com.sirelon.sellsnap.features.seller.categories.presentation.CategoryPickerContract.CategoryPickerEvent.Reset
import com.sirelon.sellsnap.features.seller.categories.presentation.CategoryPickerContract.CategoryPickerEvent.Search
import com.sirelon.sellsnap.features.seller.categories.presentation.CategoryPickerContract.CategoryPickerState
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class CategoryPickerViewModel(
    private val categoriesRepository: CategoriesRepository,
) : BaseViewModel<CategoryPickerState, CategoryPickerEvent, Nothing>() {

    override fun initialState() = CategoryPickerState()

    init {
        categoriesRepository.loadCategories()
            .onEach { categories ->
                setState { s ->
                    val next = s.copy(allCategories = categories, isLoading = false)
                    next.copy(displayItems = computeDisplayItems(next))
                }
            }
            .launchIn(viewModelScope)
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
