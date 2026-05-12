package com.sirelon.sellsnap.features.seller.categories.presentation

import androidx.compose.runtime.Immutable
import com.sirelon.sellsnap.features.seller.categories.domain.OlxCategory

@Immutable
data class CategoryDisplayItem(
    val category: OlxCategory,
    val parentChain: String = "",
)

interface CategoryPickerContract {

    @Immutable
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
