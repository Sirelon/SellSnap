package com.sirelon.sellsnap.features.seller.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sirelon.sellsnap.designsystem.AppCard
import com.sirelon.sellsnap.designsystem.AppDimens
import com.sirelon.sellsnap.designsystem.AppScaffold
import com.sirelon.sellsnap.designsystem.AppTheme
import com.sirelon.sellsnap.designsystem.AppThemeMode
import com.sirelon.sellsnap.designsystem.Cell
import com.sirelon.sellsnap.features.seller.settings.presentation.SettingsContract.SettingsEvent
import com.sirelon.sellsnap.features.seller.settings.presentation.SettingsContract.SettingsState
import com.sirelon.sellsnap.features.seller.settings.presentation.SettingsViewModel
import com.sirelon.sellsnap.legal.LegalLinks
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.profile_analytics_consent_subtitle
import com.sirelon.sellsnap.generated.resources.profile_analytics_consent_title
import com.sirelon.sellsnap.generated.resources.profile_contact_data_request
import com.sirelon.sellsnap.generated.resources.profile_delete_account_data
import com.sirelon.sellsnap.generated.resources.profile_privacy_data_title
import com.sirelon.sellsnap.generated.resources.profile_theme_dark
import com.sirelon.sellsnap.generated.resources.profile_theme_light
import com.sirelon.sellsnap.generated.resources.profile_theme_subtitle
import com.sirelon.sellsnap.generated.resources.profile_theme_system
import com.sirelon.sellsnap.generated.resources.profile_theme_title
import com.sirelon.sellsnap.generated.resources.privacy_policy
import com.sirelon.sellsnap.generated.resources.settings_screen_title
import com.sirelon.sellsnap.generated.resources.settings_version_history
import com.sirelon.sellsnap.generated.resources.terms_of_service
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsScreenRoute(
    onDeleteAccountDataRequested: () -> Unit,
    onOpenWhatsNew: () -> Unit,
) {
    val viewModel: SettingsViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val uriHandler = LocalUriHandler.current

    SettingsScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onEvent = viewModel::onEvent,
        onOpenPrivacy = { uriHandler.openUri(LegalLinks.PRIVACY_URL) },
        onOpenTerms = { uriHandler.openUri(LegalLinks.TERMS_URL) },
        onContactDataRequest = { uriHandler.openUri(LegalLinks.DATA_REQUEST_MAILTO) },
        onDeleteAccountData = onDeleteAccountDataRequested,
        onOpenWhatsNew = onOpenWhatsNew,
    )
}

@Composable
private fun SettingsScreen(
    state: SettingsState,
    snackbarHostState: SnackbarHostState,
    onEvent: (SettingsEvent) -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenTerms: () -> Unit,
    onContactDataRequest: () -> Unit,
    onDeleteAccountData: () -> Unit,
    onOpenWhatsNew: () -> Unit,
) {
    AppScaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.settings_screen_title)) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .verticalScroll(rememberScrollState())
                .padding(AppDimens.Spacing.xl3),
            verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl4),
        ) {
            ThemeCard(
                themeMode = state.themeMode,
                onThemeModeSelected = { themeMode ->
                    onEvent(SettingsEvent.ThemeModeSelected(themeMode))
                },
            )

            AppCard(modifier = Modifier.fillMaxWidth()) {
                Cell(
                    headline = {
                        Text(
                            text = stringResource(Res.string.settings_version_history),
                            style = AppTheme.typography.body,
                            color = AppTheme.colors.onSurface,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    transparent = true,
                    onClick = onOpenWhatsNew,
                )
            }

            PrivacyAndDataCard(
                analyticsConsentGranted = state.analyticsConsentGranted,
                onToggleAnalytics = { enabled ->
                    onEvent(SettingsEvent.SetAnalyticsConsent(enabled))
                },
                onOpenPrivacy = onOpenPrivacy,
                onOpenTerms = onOpenTerms,
                onContactDataRequest = onContactDataRequest,
                onDeleteAccountData = onDeleteAccountData,
            )
        }
    }
}

@Composable
private fun ThemeCard(
    themeMode: AppThemeMode,
    onThemeModeSelected: (AppThemeMode) -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
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
                Text(
                    text = stringResource(Res.string.profile_theme_title),
                    style = AppTheme.typography.title,
                    color = AppTheme.colors.onSurface,
                )
                Text(
                    text = stringResource(Res.string.profile_theme_subtitle),
                    style = AppTheme.typography.body,
                    color = AppTheme.colors.onSurfaceMuted,
                )
            }

            AppThemeMode.entries.forEach { option ->
                Cell(
                    headline = {
                        Text(
                            text = stringResource(option.labelResource),
                            style = AppTheme.typography.body,
                            color = AppTheme.colors.onSurface,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    transparent = true,
                    onClick = { onThemeModeSelected(option) },
                    trailing = {
                        RadioButton(
                            selected = themeMode == option,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = AppTheme.colors.primary,
                                unselectedColor = AppTheme.colors.onSurfaceMuted,
                            ),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun PrivacyAndDataCard(
    analyticsConsentGranted: Boolean,
    onToggleAnalytics: (Boolean) -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenTerms: () -> Unit,
    onContactDataRequest: () -> Unit,
    onDeleteAccountData: () -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
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
                Text(
                    text = stringResource(Res.string.profile_privacy_data_title),
                    style = AppTheme.typography.title,
                    color = AppTheme.colors.onSurface,
                )
            }

            Cell(
                headline = {
                    Text(
                        text = stringResource(Res.string.profile_analytics_consent_title),
                        style = AppTheme.typography.body,
                        color = AppTheme.colors.onSurface,
                    )
                },
                supporting = {
                    Text(
                        text = stringResource(Res.string.profile_analytics_consent_subtitle),
                        style = AppTheme.typography.caption,
                        color = AppTheme.colors.onSurfaceMuted,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                transparent = true,
                trailing = {
                    Switch(
                        checked = analyticsConsentGranted,
                        onCheckedChange = onToggleAnalytics,
                    )
                },
            )

            Cell(
                headline = {
                    Text(
                        text = stringResource(Res.string.privacy_policy),
                        style = AppTheme.typography.body,
                        color = AppTheme.colors.onSurface,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                transparent = true,
                onClick = onOpenPrivacy,
            )

            Cell(
                headline = {
                    Text(
                        text = stringResource(Res.string.terms_of_service),
                        style = AppTheme.typography.body,
                        color = AppTheme.colors.onSurface,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                transparent = true,
                onClick = onOpenTerms,
            )

            Cell(
                headline = {
                    Text(
                        text = stringResource(Res.string.profile_contact_data_request),
                        style = AppTheme.typography.body,
                        color = AppTheme.colors.onSurface,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                transparent = true,
                onClick = onContactDataRequest,
            )

            Cell(
                headline = {
                    Text(
                        text = stringResource(Res.string.profile_delete_account_data),
                        style = AppTheme.typography.body,
                        color = AppTheme.colors.error,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                transparent = true,
                onClick = onDeleteAccountData,
            )
        }
    }
}

private val AppThemeMode.labelResource
    get() = when (this) {
        AppThemeMode.System -> Res.string.profile_theme_system
        AppThemeMode.Light -> Res.string.profile_theme_light
        AppThemeMode.Dark -> Res.string.profile_theme_dark
    }
