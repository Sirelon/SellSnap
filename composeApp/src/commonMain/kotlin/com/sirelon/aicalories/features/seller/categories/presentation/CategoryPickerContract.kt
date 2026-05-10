package com.sirelon.sellsnap.features.seller.categories.presentation

import com.sirelon.sellsnap.features.seller.categories.domain.OlxCategory

data class CategoryDisplayItem(
    val category: OlxCategory,
    val parentChain: String = "",
)

interface CategoryPickerContract {

    data class CategoryPickerState(
        val allCategories: List<OlxCategory> = emptyList(),
        val isLoading: Boolean = true,
        val path: List<OlxCategory> = emptyList(),
        val searchQuery: String = "",
        val displayItems: List<CategoryDisplayItem> = emptyList(),
    )

    sealed interface CategoryPickerEvent {
        data class NavigateTo(val category: OlxCategory) : CategoryPickerEvent
        data class NavigateToIndex(val index: Int) : CategoryPickerEvent
        data object Reset : CategoryPickerEvent
        data class Search(val query: String) : CategoryPickerEvent
    }
}
