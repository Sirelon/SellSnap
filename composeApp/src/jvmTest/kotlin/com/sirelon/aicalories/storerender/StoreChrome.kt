package com.sirelon.sellsnap.storerender

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.sirelon.sellsnap.SellerRootTab
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The app's tab navigation around a top-level screen, as `App.kt` draws it: a bottom bar on phones,
 * a rail on wider windows. Use with [StoreScene.fullBleedBottom] so the bar reaches the home strip.
 */
@Composable
internal fun AppChrome(tab: SellerRootTab, unreadMessages: Int = 0, content: @Composable () -> Unit) {
    val layoutType = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())
    val stripColor = if (layoutType == NavigationSuiteType.NavigationBar) {
        MaterialTheme.colorScheme.surfaceContainer
    } else {
        MaterialTheme.colorScheme.background
    }
    Column(Modifier.fillMaxSize()) {
        NavigationSuiteScaffold(
            modifier = Modifier.weight(1f),
            layoutType = layoutType,
            navigationSuiteItems = {
                SellerRootTab.entries.forEach { item ->
                    item(
                        selected = item == tab,
                        onClick = {},
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (item == SellerRootTab.Messages && unreadMessages > 0) {
                                        Badge { Text(unreadMessages.toString()) }
                                    }
                                },
                            ) {
                                val icon = item.icon
                                if (icon != null) {
                                    Icon(painter = painterResource(icon), contentDescription = null)
                                } else {
                                    Icon(imageVector = Icons.Default.Settings, contentDescription = null)
                                }
                            }
                        },
                        label = { Text(stringResource(item.label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        alwaysShowLabel = false,
                    )
                }
            },
        ) { content() }
        Spacer(Modifier.fillMaxWidth().height(LocalStoreBottomInset.current).background(stripColor))
    }
}
