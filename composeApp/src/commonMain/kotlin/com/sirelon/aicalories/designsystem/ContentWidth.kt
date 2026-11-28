package com.sirelon.sellsnap.designsystem

import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.window.core.layout.WindowSizeClass

/**
 * Keeps a phone-shaped column from stretching across a tablet or desktop window: the element
 * fills the width up to [AppDimens.Size.xl26] and is centred beyond that. Chain it after a
 * scroll modifier, so the whole window still scrolls, and before the column's own padding.
 */
fun Modifier.centeredContentWidth(): Modifier = this
    .fillMaxWidth()
    .wrapContentWidth(Alignment.CenterHorizontally)
    .widthIn(max = AppDimens.Size.xl26)
    .fillMaxWidth()

/** [centeredContentWidth] for a pair of side-by-side panes. */
fun Modifier.centeredTwoPaneWidth(): Modifier = this
    .fillMaxWidth()
    .wrapContentWidth(Alignment.CenterHorizontally)
    .widthIn(max = AppDimens.Size.xl26 * 2)
    .fillMaxWidth()

/**
 * The lazy-list form of [centeredContentWidth]: the horizontal content padding that centres the
 * items in a list [containerWidth] wide, so the list itself, and its scroll area, still reach the
 * window edges. [padding] is the list's own side padding, kept inside the centred column.
 */
fun centeredContentPadding(containerWidth: Dp, padding: Dp): Dp =
    maxOf(Dp(0f), (containerWidth - AppDimens.Size.xl26) / 2) + padding

/**
 * True from the expanded width class (840dp) up: landscape tablets, unfolded foldables in
 * landscape, desktop, and the largest phones held landscape. Screens with two distinct parts lay
 * them out side by side there, each part at most [AppDimens.Size.xl26] wide (see
 * [centeredTwoPaneWidth]).
 */
@Composable
fun isExpandedWidthWindow(): Boolean = currentWindowAdaptiveInfoV2().windowSizeClass
    .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)

/**
 * Content padding for a full-width lazy list inside a [BoxWithConstraints]: [base] (usually the
 * scaffold's padding) plus the sides from [centeredContentPadding].
 */
@Composable
fun BoxWithConstraintsScope.centeredListPadding(base: PaddingValues, padding: Dp): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    val side = centeredContentPadding(containerWidth = maxWidth, padding = padding)
    return PaddingValues(
        start = base.calculateStartPadding(layoutDirection) + side,
        top = base.calculateTopPadding(),
        end = base.calculateEndPadding(layoutDirection) + side,
        bottom = base.calculateBottomPadding(),
    )
}
