package com.sirelon.sellsnap.designsystem.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.sirelon.sellsnap.designsystem.AppDimens
import com.sirelon.sellsnap.designsystem.AppTheme

// TODO: make design better
@Composable
fun EmptyScreen(
    title: String,
    description: String,
    actionLabel: String?,
    modifier: Modifier = Modifier,
    onActionClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .background(AppTheme.colors.background)
            .padding(AppDimens.Spacing.xl8),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            style = AppTheme.typography.title,
            color = AppTheme.colors.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(AppDimens.Spacing.xl3))
        Text(
            text = description,
            style = AppTheme.typography.body,
            color = AppTheme.colors.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
        actionLabel?.let { actionLabel ->
            Spacer(modifier = Modifier.height(AppDimens.Spacing.xl4))
            Button(
                onClick = { onActionClick?.invoke() },
                enabled = onActionClick != null,
            ) {
                Text(actionLabel)
            }
        }
    }
}