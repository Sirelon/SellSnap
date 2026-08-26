package com.sirelon.sellsnap.features.whatsnew.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sirelon.sellsnap.config.AppConfig
import com.sirelon.sellsnap.designsystem.AppCard
import com.sirelon.sellsnap.designsystem.AppDimens
import com.sirelon.sellsnap.designsystem.AppScaffold
import com.sirelon.sellsnap.designsystem.AppTheme
import com.sirelon.sellsnap.designsystem.Cell
import com.sirelon.sellsnap.designsystem.IconWithBackground
import com.sirelon.sellsnap.designsystem.Pill
import com.sirelon.sellsnap.designsystem.screens.EmptyScreen
import com.sirelon.sellsnap.designsystem.screens.LoadingOverlay
import com.sirelon.sellsnap.features.whatsnew.model.Release
import com.sirelon.sellsnap.features.whatsnew.presentation.WhatsNewViewModel
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.back
import com.sirelon.sellsnap.generated.resources.ic_circle_check_big
import com.sirelon.sellsnap.generated.resources.whats_new_empty_description
import com.sirelon.sellsnap.generated.resources.whats_new_empty_title
import com.sirelon.sellsnap.generated.resources.whats_new_installed_badge
import com.sirelon.sellsnap.generated.resources.whats_new_screen_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun AllReleasesScreenRoute(
    viewModel: WhatsNewViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    AppScaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.whats_new_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LoadingOverlay(isLoading = state.isLoading) {
            if (!state.isLoading && state.releases.isEmpty()) {
                EmptyScreen(
                    title = stringResource(Res.string.whats_new_empty_title),
                    description = stringResource(Res.string.whats_new_empty_description),
                    actionLabel = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .consumeWindowInsets(padding),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .consumeWindowInsets(padding),
                    contentPadding = padding,
                    verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl4),
                ) {
                    items(state.releases) { release ->
                        ReleaseCard(
                            release = release,
                            isInstalled = release.version == AppConfig.appVersionName,
                            modifier = Modifier.padding(horizontal = AppDimens.Spacing.xl3),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReleaseCard(
    release: Release,
    isInstalled: Boolean,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(vertical = AppDimens.Spacing.xl2),
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = AppDimens.Spacing.xl5,
                    vertical = AppDimens.Spacing.xl3,
                ),
                verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xs),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.Spacing.m),
                ) {
                    Text(
                        text = "v${release.version}",
                        style = AppTheme.typography.title,
                        color = AppTheme.colors.onSurface,
                    )
                    if (isInstalled) {
                        Pill(
                            text = stringResource(Res.string.whats_new_installed_badge),
                            iconResource = Res.drawable.ic_circle_check_big,
                            color = AppTheme.colors.success,
                        )
                    }
                }
                Text(
                    text = release.date,
                    style = AppTheme.typography.caption,
                    color = AppTheme.colors.onSurfaceMuted,
                )
            }

            release.changes.forEach { change ->
                Cell(
                    headline = {
                        Text(
                            text = change.title,
                            style = AppTheme.typography.body,
                            color = AppTheme.colors.onSurface,
                        )
                    },
                    supporting = {
                        Column(verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xs)) {
                            Text(
                                text = change.summary,
                                style = AppTheme.typography.caption,
                                color = AppTheme.colors.onSurfaceMuted,
                            )
                            change.detail?.let { detail ->
                                Text(
                                    text = detail,
                                    style = AppTheme.typography.caption,
                                    color = AppTheme.colors.onSurfaceMuted,
                                )
                            }
                        }
                    },
                    leading = {
                        IconWithBackground(
                            backgroundColor = AppTheme.colors.primary,
                            modifier = Modifier.size(AppDimens.Size.xl10),
                        ) {
                            Icon(
                                painter = painterResource(releaseChangeIcon(change.icon)),
                                contentDescription = null,
                                tint = AppTheme.colors.primary,
                                modifier = Modifier.size(AppDimens.Size.xl6),
                            )
                        }
                    },
                    transparent = true,
                )
            }
        }
    }
}
