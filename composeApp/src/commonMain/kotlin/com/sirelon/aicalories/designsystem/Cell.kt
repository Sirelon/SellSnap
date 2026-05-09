package com.sirelon.sellsnap.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun Cell(
    headline: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    transparent: Boolean = false,
    onClick: (() -> Unit)? = null,
    overline: @Composable (() -> Unit)? = null,
    supporting: @Composable (() -> Unit)? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val clickableModifier = onClick?.let { Modifier.clickable(onClick = onClick) } ?: Modifier
    val defaultColors = ListItemDefaults.colors()
    ListItem(
        headlineContent = headline,
        modifier = modifier
            .fillMaxWidth()
            .then(clickableModifier),
        overlineContent = overline,
        supportingContent = supporting,
        leadingContent = leading,
        trailingContent = trailing,
        colors = if (transparent) ListItemDefaults.colors(containerColor = Color.Transparent) else defaultColors,
        tonalElevation = ListItemDefaults.Elevation,
        shadowElevation = ListItemDefaults.Elevation,
    )
}
