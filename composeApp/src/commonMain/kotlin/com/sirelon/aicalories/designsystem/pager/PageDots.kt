package com.sirelon.sellsnap.designsystem.pager

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.sirelon.sellsnap.designsystem.AppDimens

@Composable
fun PageDots(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { pageIndex ->
            val dotWidth by animateDpAsState(
                targetValue = if (pageIndex == currentPage) AppDimens.Size.xl4 else AppDimens.Size.s,
                label = "pageDotWidth",
            )

            Box(
                modifier = Modifier
                    .size(width = dotWidth, height = AppDimens.Size.s)
                    .clip(CircleShape)
                    .background(
                        color = if (pageIndex == currentPage) {
                            Color.White
                        } else {
                            Color.White.copy(alpha = 0.55f)
                        }
                    )
            )
        }
    }
}
