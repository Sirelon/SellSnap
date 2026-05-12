package com.sirelon.sellsnap.designsystem.buttons

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.painter.Painter
import com.sirelon.sellsnap.designsystem.AppDimens
import com.sirelon.sellsnap.designsystem.performStepFeedback

@Composable
fun AppIconButton(
    icon: Painter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hapticFeedback = LocalHapticFeedback.current

    FilledTonalIconButton(
        onClick = {
            hapticFeedback.performStepFeedback()
            onClick()
        },
        modifier = modifier.size(AppDimens.Size.xl8 + AppDimens.Size.xl7),
        shape = RoundedCornerShape(AppDimens.BorderRadius.xl4),
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            modifier = Modifier.size(AppDimens.Size.xl6),
        )
    }
}
