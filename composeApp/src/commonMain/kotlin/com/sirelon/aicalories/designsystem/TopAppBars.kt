package com.sirelon.sellsnap.designsystem

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.RowScope
import org.jetbrains.compose.resources.stringResource
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.back

@Composable
fun AppLargeAppBar(
    title: String,
    subtitle: String?,
    onBack: (() -> Unit)?,
    scrollBehavior: TopAppBarScrollBehavior,
    actions: @Composable RowScope.() -> Unit = {},
) {
    LargeFlexibleTopAppBar(
        subtitle = if (subtitle != null) {
            {
                Text(text = subtitle)
            }
        } else null,
        title = {
            Text(text = title)
        },
        navigationIcon = {
            onBack?.let {
                IconButton(onClick = it) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = stringResource(Res.string.back),
                    )
                }
            }
        },
        actions = actions,
        scrollBehavior = scrollBehavior,
    )
}
