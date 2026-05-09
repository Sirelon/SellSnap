package com.sirelon.sellsnap.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

// No-Line Rule: visual separation is achieved through spacing and tonal layering, not lines.

@Composable
fun AppDivider(modifier: Modifier = Modifier) {
    Spacer(modifier = modifier.height(AppDimens.Spacing.xl3))
}

@Composable
fun AppDivider(middleContent: @Composable () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        middleContent()
    }
}
