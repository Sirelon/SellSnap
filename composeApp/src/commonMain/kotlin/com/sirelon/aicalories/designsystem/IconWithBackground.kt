package com.sirelon.sellsnap.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

@Composable
fun IconWithBackground(
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    iconPadding: Dp = AppDimens.Spacing.l,
    content: @Composable () -> Unit,
) {

    Card(modifier = modifier, colors = CardDefaults.cardColors(contentColor = backgroundColor)) {
        Box(
            modifier = Modifier.padding(iconPadding),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}
