package com.sirelon.sellsnap.features.seller.categories.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sirelon.sellsnap.designsystem.AppDimens
import com.sirelon.sellsnap.designsystem.AppTheme
import com.sirelon.sellsnap.designsystem.Cell
import com.sirelon.sellsnap.designsystem.SearchInput
import com.sirelon.sellsnap.designsystem.buttons.AppButton
import com.sirelon.sellsnap.designsystem.buttons.AppButtonDefaults
import com.sirelon.sellsnap.designsystem.screens.LoadingOverlay
import com.sirelon.sellsnap.features.seller.categories.domain.OlxCategory
import com.sirelon.sellsnap.features.seller.categories.presentation.CategoryPickerContract.CategoryPickerEvent.NavigateTo
import com.sirelon.sellsnap.features.seller.categories.presentation.CategoryPickerContract.CategoryPickerEvent.NavigateToIndex
import com.sirelon.sellsnap.features.seller.categories.presentation.CategoryPickerContract.CategoryPickerEvent.Reset
import com.sirelon.sellsnap.features.seller.categories.presentation.CategoryPickerContract.CategoryPickerEvent.Search
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.category_picker_all
import com.sirelon.sellsnap.generated.resources.category_picker_reset
import com.sirelon.sellsnap.generated.resources.category_picker_search_placeholder
import com.sirelon.sellsnap.generated.resources.category_picker_select
import com.sirelon.sellsnap.generated.resources.category_picker_select_label
import com.sirelon.sellsnap.generated.resources.category_picker_select_with_name
import com.sirelon.sellsnap.generated.resources.category_picker_selected_label
import com.sirelon.sellsnap.generated.resources.ic_chevron_right
import com.sirelon.sellsnap.generated.resources.ic_circle_check_big
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun CategoryPickerSheet(
    onCategorySelected: (OlxCategory) -> Unit,
) {
    val viewModel: CategoryPickerViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val c = AppTheme.colors
    val t = AppTheme.typography

    Column(modifier = Modifier.fillMaxHeight(0.92f)) {
        // ── Search bar ────────────────────────────────────────────
        SearchInput(
            value = state.searchQuery,
            onValueChange = { viewModel.onEvent(Search(it)) },
            placeholder = stringResource(Res.string.category_picker_search_placeholder),
            modifier = Modifier.padding(
                horizontal = AppDimens.Spacing.xl5,
                vertical = AppDimens.Spacing.xl,
            ),
        )

        // ── Breadcrumbs ───────────────────────────────────────────
        if (state.searchQuery.isEmpty() && state.path.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = AppDimens.Spacing.xl5)
                    .padding(bottom = AppDimens.Spacing.xl),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppDimens.Spacing.s),
            ) {
                TextButton(
                    onClick = { viewModel.onEvent(NavigateToIndex(0)) },
                    contentPadding = PaddingValues(AppDimens.Spacing.xs4),
                ) {
                    Text(
                        stringResource(Res.string.category_picker_all),
                        style = t.body.copy(fontWeight = FontWeight.SemiBold),
                        color = c.primary,
                    )
                }
                state.path.forEachIndexed { index, category ->
                    Text(
                        "›",
                        color = c.onSurface.copy(alpha = 0.34f),
                        fontSize = AppDimens.TextSize.xl,
                    )
                    val isLast = index == state.path.lastIndex
                    TextButton(
                        onClick = { viewModel.onEvent(NavigateToIndex(index + 1)) },
                        contentPadding = PaddingValues(AppDimens.Spacing.xs4),
                    ) {
                        Text(
                            category.label,
                            style = t.body.copy(
                                fontWeight = if (isLast) FontWeight.Bold else FontWeight.SemiBold,
                            ),
                            color = if (isLast) c.onSurface else c.primary,
                        )
                    }
                }
            }
        }

        // ── Category list ─────────────────────────────────────────
        LazyColumn(modifier = Modifier.weight(1f)) {
            if (state.isLoading) {
                item {
                    LoadingOverlay(isLoading = true) {}
                }
            } else {
                val isSearchMode = state.searchQuery.isNotBlank()
                itemsIndexed(state.displayItems, key = { _, item -> item.category.id }) { index, item ->
                    val category = item.category
                    if (isSearchMode) {
                        Cell(
                            headline = {
                                Text(
                                    category.label,
                                    style = t.body.copy(fontWeight = FontWeight.Medium),
                                    color = c.onSurface,
                                )
                            },
                            supporting = if (item.parentChain.isNotEmpty()) {
                                { Text(item.parentChain, style = t.caption, color = c.onSurfaceMuted) }
                            } else null,
                            onClick = { if (category.isLeaf) onCategorySelected(category) },
                        )
                    } else {
                        val isActive = state.path.lastOrNull()?.id == category.id
                        val icon = categoryIconPainter(category.id)
                        Cell(
                            modifier = Modifier.background(
                                if (isActive) c.primary.copy(alpha = 0.06f) else Color.Transparent,
                            ),
                            headline = {
                                Text(
                                    category.label,
                                    style = t.body.copy(
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                    ),
                                    color = if (isActive) c.primary else c.onSurface,
                                )
                            },
                            leading = if (icon != null) {
                                {
                                    Box(
                                        modifier = Modifier
                                            .size(AppDimens.Size.xl9)
                                            .background(
                                                c.surfaceLow,
                                                RoundedCornerShape(AppDimens.BorderRadius.l),
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            painter = icon,
                                            contentDescription = null,
                                            modifier = Modifier.size(AppDimens.Size.xl5),
                                            tint = c.onSurface,
                                        )
                                    }
                                }
                            } else null,
                            trailing = {
                                when {
                                    category.isLeaf && isActive -> Text(
                                        stringResource(Res.string.category_picker_selected_label),
                                        style = t.caption.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = c.primary,
                                        ),
                                    )
                                    category.isLeaf -> Text(
                                        stringResource(Res.string.category_picker_select_label),
                                        style = t.caption.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = c.success,
                                        ),
                                    )
                                    isActive -> Icon(
                                        painter = painterResource(Res.drawable.ic_circle_check_big),
                                        contentDescription = null,
                                        tint = c.primary,
                                        modifier = Modifier.size(AppDimens.Size.xl3),
                                    )
                                    else -> Icon(
                                        painter = painterResource(Res.drawable.ic_chevron_right),
                                        contentDescription = null,
                                        tint = c.onSurface.copy(alpha = 0.34f),
                                        modifier = Modifier.size(AppDimens.Size.xl2),
                                    )
                                }
                            },
                            onClick = {
                                if (category.isLeaf) {
                                    onCategorySelected(category)
                                } else {
                                    viewModel.onEvent(NavigateTo(category))
                                }
                            },
                        )
                    }
                    if (index < state.displayItems.lastIndex) {
                        HorizontalDivider(color = c.outlineVariant.copy(alpha = 0.13f))
                    }
                }
            }
        }

        // ── Footer ────────────────────────────────────────────────
        HorizontalDivider(color = c.outlineVariant.copy(alpha = 0.2f))
        Row(
            modifier = Modifier
                .padding(horizontal = AppDimens.Spacing.xl5, vertical = AppDimens.Spacing.xl)
                .navigationBarsPadding()
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.Spacing.l),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppButton(
                text = stringResource(Res.string.category_picker_reset),
                onClick = { viewModel.onEvent(Reset) },
                style = AppButtonDefaults.secondary(),
                modifier = Modifier.wrapContentWidth(),
            )
            val selectedCategory = state.path.lastOrNull()
            val selectedLabel = selectedCategory?.label
            AppButton(
                text = if (selectedLabel != null) {
                    stringResource(Res.string.category_picker_select_with_name, selectedLabel)
                } else {
                    stringResource(Res.string.category_picker_select)
                },
                onClick = {
                    selectedCategory
                        ?.takeIf { it.isLeaf }
                        ?.let { onCategorySelected(it) }
                },
                enabled = selectedCategory?.isLeaf == true,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
