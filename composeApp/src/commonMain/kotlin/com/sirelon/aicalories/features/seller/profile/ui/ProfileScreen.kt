package com.sirelon.sellsnap.features.seller.profile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mohamedrejeb.calf.permissions.CoarseLocation
import com.mohamedrejeb.calf.permissions.Permission
import com.sirelon.sellsnap.designsystem.AppAsyncImage
import com.sirelon.sellsnap.designsystem.AppAvatar
import com.sirelon.sellsnap.designsystem.AppCard
import com.sirelon.sellsnap.designsystem.AppDimens
import com.sirelon.sellsnap.designsystem.AppScaffold
import com.sirelon.sellsnap.designsystem.AppTheme
import com.sirelon.sellsnap.designsystem.AppThemeMode
import com.sirelon.sellsnap.designsystem.Cell
import com.sirelon.sellsnap.designsystem.ErrorPill
import com.sirelon.sellsnap.designsystem.ObserveAsEvents
import com.sirelon.sellsnap.designsystem.Pill
import com.sirelon.sellsnap.designsystem.buttons.AppButton
import com.sirelon.sellsnap.designsystem.buttons.AppButtonDefaults
import com.sirelon.sellsnap.designsystem.buttons.AppButtonStyle
import com.sirelon.sellsnap.designsystem.screens.LoadingOverlay
import com.sirelon.sellsnap.features.media.PermissionDialogContent
import com.sirelon.sellsnap.features.media.PermissionDialogs
import com.sirelon.sellsnap.features.media.rememberPermissionController
import com.sirelon.sellsnap.features.seller.auth.data.OlxAuthCallbackBridge
import com.sirelon.sellsnap.features.seller.auth.domain.OlxUser
import com.sirelon.sellsnap.features.seller.location.OlxLocation
import com.sirelon.sellsnap.features.seller.profile.presentation.ProfileContract
import com.sirelon.sellsnap.features.seller.profile.presentation.ProfileContract.ProfileEvent
import com.sirelon.sellsnap.features.seller.profile.presentation.ProfileContract.SellerAccountUiModel
import com.sirelon.sellsnap.features.seller.profile.presentation.ProfileViewModel
import com.sirelon.sellsnap.legal.LegalLinks
import kotlinx.coroutines.delay
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.back
import com.sirelon.sellsnap.generated.resources.change_button
import com.sirelon.sellsnap.generated.resources.continue_with_olx
import com.sirelon.sellsnap.generated.resources.ic_arrow_left
import com.sirelon.sellsnap.generated.resources.ic_check
import com.sirelon.sellsnap.generated.resources.ic_refresh_cw
import com.sirelon.sellsnap.generated.resources.ic_user
import com.sirelon.sellsnap.generated.resources.location_detecting
import com.sirelon.sellsnap.generated.resources.location_not_available
import com.sirelon.sellsnap.generated.resources.location_rationale_message
import com.sirelon.sellsnap.generated.resources.location_rationale_title
import com.sirelon.sellsnap.generated.resources.location_settings_message_android
import com.sirelon.sellsnap.generated.resources.location_settings_message_ios
import com.sirelon.sellsnap.generated.resources.location_settings_title
import com.sirelon.sellsnap.generated.resources.not_now
import com.sirelon.sellsnap.generated.resources.open_settings
import com.sirelon.sellsnap.generated.resources.profile_field_business
import com.sirelon.sellsnap.generated.resources.profile_field_created_at
import com.sirelon.sellsnap.generated.resources.profile_field_email
import com.sirelon.sellsnap.generated.resources.profile_field_id
import com.sirelon.sellsnap.generated.resources.profile_field_last_login_at
import com.sirelon.sellsnap.generated.resources.profile_field_name
import com.sirelon.sellsnap.generated.resources.profile_field_phone
import com.sirelon.sellsnap.generated.resources.profile_field_status
import com.sirelon.sellsnap.generated.resources.profile_guest_description
import com.sirelon.sellsnap.generated.resources.profile_guest_title
import com.sirelon.sellsnap.generated.resources.profile_delete_account_data
import com.sirelon.sellsnap.generated.resources.profile_delete_account_data_cancel
import com.sirelon.sellsnap.generated.resources.profile_delete_account_data_confirm
import com.sirelon.sellsnap.generated.resources.profile_delete_account_data_message
import com.sirelon.sellsnap.generated.resources.profile_delete_account_data_title
import com.sirelon.sellsnap.generated.resources.profile_analytics_consent_subtitle
import com.sirelon.sellsnap.generated.resources.profile_analytics_consent_title
import com.sirelon.sellsnap.generated.resources.profile_contact_data_request
import com.sirelon.sellsnap.generated.resources.profile_privacy_data_title
import com.sirelon.sellsnap.generated.resources.privacy_policy
import com.sirelon.sellsnap.generated.resources.terms_of_service
import com.sirelon.sellsnap.generated.resources.profile_location_subtitle
import com.sirelon.sellsnap.generated.resources.profile_location_title
import com.sirelon.sellsnap.generated.resources.profile_logout
import com.sirelon.sellsnap.generated.resources.profile_not_provided
import com.sirelon.sellsnap.generated.resources.profile_olx_account
import com.sirelon.sellsnap.generated.resources.profile_screen_title
import com.sirelon.sellsnap.generated.resources.profile_theme_dark
import com.sirelon.sellsnap.generated.resources.profile_theme_light
import com.sirelon.sellsnap.generated.resources.profile_theme_subtitle
import com.sirelon.sellsnap.generated.resources.profile_theme_system
import com.sirelon.sellsnap.generated.resources.profile_theme_title
import com.sirelon.sellsnap.generated.resources.profile_value_no
import com.sirelon.sellsnap.generated.resources.profile_value_yes
import com.sirelon.sellsnap.generated.resources.retry
import com.sirelon.sellsnap.generated.resources.profile_account_active_badge
import com.sirelon.sellsnap.generated.resources.profile_account_add_button
import com.sirelon.sellsnap.generated.resources.account_business_badge
import com.sirelon.sellsnap.generated.resources.profile_account_cap_reached_message
import com.sirelon.sellsnap.generated.resources.account_needs_reconnect_badge
import com.sirelon.sellsnap.generated.resources.profile_accounts_section_title
import com.sirelon.sellsnap.generated.resources.profile_account_set_active
import com.sirelon.sellsnap.generated.resources.profile_active_account_details_title
import com.sirelon.sellsnap.generated.resources.profile_add_account_confirm_cancel
import com.sirelon.sellsnap.generated.resources.profile_add_account_confirm_continue
import com.sirelon.sellsnap.generated.resources.profile_add_account_confirm_note
import com.sirelon.sellsnap.generated.resources.profile_add_account_confirm_subtitle
import com.sirelon.sellsnap.generated.resources.profile_add_account_confirm_title
import com.sirelon.sellsnap.generated.resources.profile_auth_failed_dismiss
import com.sirelon.sellsnap.generated.resources.profile_auth_failed_message
import com.sirelon.sellsnap.generated.resources.profile_auth_failed_recovery_action
import com.sirelon.sellsnap.generated.resources.profile_auth_failed_recovery_message
import com.sirelon.sellsnap.generated.resources.profile_auth_failed_recovery_title
import com.sirelon.sellsnap.generated.resources.profile_auth_failed_retry
import com.sirelon.sellsnap.generated.resources.profile_auth_failed_retry_countdown
import com.sirelon.sellsnap.generated.resources.profile_auth_failed_title
import com.sirelon.sellsnap.generated.resources.profile_disconnect_confirm_action
import com.sirelon.sellsnap.generated.resources.profile_disconnect_confirm_cancel
import com.sirelon.sellsnap.generated.resources.profile_disconnect_confirm_message
import com.sirelon.sellsnap.generated.resources.profile_disconnect_confirm_olx_link
import com.sirelon.sellsnap.generated.resources.profile_disconnect_confirm_title
import com.sirelon.sellsnap.generated.resources.profile_reconnect_account_action
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ProfileScreenRoute(
    onBack: (() -> Unit)?,
    onOpenOlxAuth: (String) -> Unit,
    onLogout: () -> Unit,
    onDeleteAccountDataRequested: () -> Unit,
    // SIR-83: a second launcher forcing a fresh OLX login, used for add-account and reconnect
    // only (D5) - first connect above keeps using [onOpenOlxAuth]'s non-forced session.
    onOpenOlxAuthForceReauth: (String) -> Unit,
    onAddAccountRequested: () -> Unit,
    onDisconnectRequested: (Int) -> Unit,
    onAddAccountFailed: () -> Unit,
    reason: String? = null,
) {
    val viewModel: ProfileViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val locationPermissionController = rememberPermissionController(permission = Permission.CoarseLocation)
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(viewModel) {
        OlxAuthCallbackBridge.callbacks.collect { callbackUrl ->
            viewModel.onCallbackReceived(callbackUrl)
        }
    }

    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            is ProfileContract.ProfileEffect.LaunchOlxAuthFlow -> {
                if (effect.forceReauth) onOpenOlxAuthForceReauth(effect.url) else onOpenOlxAuth(effect.url)
            }

            is ProfileContract.ProfileEffect.ShowMessage -> {
                snackbarHostState.showSnackbar(effect.message)
            }

            ProfileContract.ProfileEffect.NavigateToLanding -> onLogout()

            ProfileContract.ProfileEffect.AuthorizationFailed -> onAddAccountFailed()
        }
    }

    LoadingOverlay(
        isLoading = state.isLoading || state.isAuthenticating,
    ) {
        ProfileScreen(
            state = state,
            snackbarHostState = snackbarHostState,
            onBack = onBack,
            onEvent = viewModel::onEvent,
            onChangeLocation = {
                locationPermissionController.requestPermission {
                    viewModel.onEvent(ProfileEvent.ChangeLocationClicked)
                }
            },
            onDeleteAccountDataRequested = onDeleteAccountDataRequested,
            onAddAccountRequested = onAddAccountRequested,
            onDisconnectRequested = onDisconnectRequested,
            onOpenPrivacy = { uriHandler.openUri(LegalLinks.PRIVACY_URL) },
            onOpenTerms = { uriHandler.openUri(LegalLinks.TERMS_URL) },
            onContactDataRequest = { uriHandler.openUri(LegalLinks.DATA_REQUEST_MAILTO) },
            reason = reason,
        )
    }

    PermissionDialogs(
        controller = locationPermissionController,
        rationaleContent = PermissionDialogContent(
            title = Res.string.location_rationale_title,
            message = Res.string.location_rationale_message,
            confirmText = Res.string.retry,
            dismissText = Res.string.not_now,
        ),
        settingsContentProvider = { isIos ->
            PermissionDialogContent(
                title = Res.string.location_settings_title,
                message = if (isIos) {
                    Res.string.location_settings_message_ios
                } else {
                    Res.string.location_settings_message_android
                },
                confirmText = Res.string.open_settings,
                dismissText = Res.string.not_now,
            )
        },
    )
}

@Composable
private fun ProfileScreen(
    state: ProfileContract.ProfileState,
    snackbarHostState: SnackbarHostState,
    onBack: (() -> Unit)?,
    onEvent: (ProfileEvent) -> Unit,
    onChangeLocation: () -> Unit,
    onDeleteAccountDataRequested: () -> Unit,
    onAddAccountRequested: () -> Unit,
    onDisconnectRequested: (Int) -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenTerms: () -> Unit,
    onContactDataRequest: () -> Unit,
    reason: String? = null,
) {
    AppScaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.profile_screen_title)) },
                navigationIcon = {
                    onBack?.let {
                        IconButton(onClick = it) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_arrow_left),
                                contentDescription = stringResource(Res.string.back),
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { onEvent(ProfileEvent.RefreshClicked) }) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_refresh_cw),
                            contentDescription = null,
                        )
                    }
                },
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
            if (!reason.isNullOrBlank()) {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = reason,
                        style = AppTheme.typography.body,
                        color = AppTheme.colors.error,
                        modifier = Modifier.padding(AppDimens.Spacing.xl5),
                    )
                }
            }

            if (state.user == null && state.accounts.isEmpty()) {
                GuestCard(onLogin = { onEvent(ProfileEvent.LoginClicked) })
            } else {
                AccountsSection(
                    accounts = state.accounts,
                    activeUser = state.user,
                    canAddAccount = state.canAddAccount,
                    expandedReconnectLocalIndex = state.expandedReconnectLocalIndex,
                    onDisconnectSingleAccount = { onEvent(ProfileEvent.LogoutClicked) },
                    onSetActiveAccount = { onEvent(ProfileEvent.SetActiveAccountClicked(it)) },
                    onToggleReconnectRow = { onEvent(ProfileEvent.NeedsReconnectRowClicked(it)) },
                    onReconnectAccount = { onEvent(ProfileEvent.ReconnectClicked(it)) },
                    onDisconnectAccount = onDisconnectRequested,
                    onAddAccountClick = onAddAccountRequested,
                )
            }

            LocationCard(
                location = state.location,
                isLoading = state.isLocationLoading,
                onChangeLocation = onChangeLocation,
            )

            ThemeCard(
                themeMode = state.themeMode,
                onThemeModeSelected = { themeMode ->
                    onEvent(ProfileEvent.ThemeModeSelected(themeMode))
                },
            )

            PrivacyAndDataCard(
                analyticsConsentGranted = state.analyticsConsentGranted,
                onToggleAnalytics = { enabled ->
                    onEvent(ProfileEvent.SetAnalyticsConsent(enabled))
                },
                onOpenPrivacy = onOpenPrivacy,
                onOpenTerms = onOpenTerms,
                onContactDataRequest = onContactDataRequest,
                onDeleteAccountData = onDeleteAccountDataRequested,
            )

            state.errorMessage?.let { message ->
                Text(
                    text = message,
                    style = AppTheme.typography.body,
                    color = AppTheme.colors.error,
                )
            }

            Spacer(modifier = Modifier.height(AppDimens.Spacing.xl2))
        }
    }
}

@Composable
private fun GuestCard(onLogin: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(AppDimens.Spacing.xl5),
            verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl4),
            horizontalAlignment = Alignment.Start,
        ) {
            AppAvatar(avatarUrl = null, fallbackInitial = null)
            Text(
                text = stringResource(Res.string.profile_guest_title),
                style = AppTheme.typography.title,
                color = AppTheme.colors.onSurface,
            )
            Text(
                text = stringResource(Res.string.profile_guest_description),
                style = AppTheme.typography.body,
                color = AppTheme.colors.onSurfaceMuted,
            )
            AppButton(
                text = stringResource(Res.string.continue_with_olx),
                onClick = onLogin,
                modifier = Modifier.fillMaxWidth(),
                style = AppButtonDefaults.primary(),
            )
        }
    }
}

@Composable
private fun AccountCard(
    user: OlxUser,
    onLogout: () -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(vertical = AppDimens.Spacing.xl2),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppDimens.Spacing.xl5),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl4),
            ) {
                AppAvatar(
                    avatarUrl = user.avatar,
                    fallbackInitial = user.name.firstOrNull()?.uppercaseChar()?.toString(),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user.name.takeIf { it.isNotBlank() }
                            ?: stringResource(Res.string.profile_olx_account),
                        style = AppTheme.typography.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = user.email.takeIf { it.isNotBlank() }
                            ?: stringResource(Res.string.profile_not_provided),
                        style = AppTheme.typography.body,
                        color = AppTheme.colors.onSurfaceMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            AccountFieldDump(user)

            AppButton(
                text = stringResource(Res.string.profile_logout),
                onClick = onLogout,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppDimens.Spacing.xl5),
                style = AppButtonStyle(
                    backgroundColor = AppTheme.colors.error,
                    contentColor = AppTheme.colors.onError,
                ),
            )
        }
    }
}

@Composable
private fun AccountFieldDump(user: OlxUser) {
    ProfileField(label = stringResource(Res.string.profile_field_id), value = user.id.toString())
    ProfileField(label = stringResource(Res.string.profile_field_name), value = user.name)
    ProfileField(label = stringResource(Res.string.profile_field_email), value = user.email)
    ProfileField(label = stringResource(Res.string.profile_field_status), value = user.status)
    ProfileField(label = stringResource(Res.string.profile_field_phone), value = user.phone)
    ProfileField(label = stringResource(Res.string.profile_field_created_at), value = user.createdAt)
    ProfileField(label = stringResource(Res.string.profile_field_last_login_at), value = user.lastLoginAt)
    ProfileField(
        label = stringResource(Res.string.profile_field_business),
        value = if (user.isBusiness) {
            stringResource(Res.string.profile_value_yes)
        } else {
            stringResource(Res.string.profile_value_no)
        },
    )
}

/**
 * SIR-83 U1: renders exactly today's single-account [AccountCard] when there's zero or one
 * connected account (the feature must be invisible below two accounts - PRD §3, Q20), and the
 * accounts list (active marked + first, per-row Set-as-active/Disconnect/Reconnect actions) once
 * a second account exists. The active account's full field dump keeps rendering underneath the
 * list either way, from [activeUser] (the repository's reactive, network-fetched [OlxUser]) -
 * the lightweight [SellerAccountUiModel] rows never carry those fields.
 */
@Composable
private fun AccountsSection(
    accounts: List<SellerAccountUiModel>,
    activeUser: OlxUser?,
    canAddAccount: Boolean,
    expandedReconnectLocalIndex: Int?,
    onDisconnectSingleAccount: () -> Unit,
    onSetActiveAccount: (Int) -> Unit,
    onToggleReconnectRow: (Int) -> Unit,
    onReconnectAccount: (Int) -> Unit,
    onDisconnectAccount: (Int) -> Unit,
    onAddAccountClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl3)) {
        if (accounts.size <= 1) {
            if (activeUser != null) {
                AccountCard(user = activeUser, onLogout = onDisconnectSingleAccount)
            }
        } else {
            Text(
                text = stringResource(Res.string.profile_accounts_section_title),
                style = AppTheme.typography.title,
                color = AppTheme.colors.onSurface,
            )
            AccountsListCard(
                accounts = accounts,
                expandedReconnectLocalIndex = expandedReconnectLocalIndex,
                onSetActiveAccount = onSetActiveAccount,
                onToggleReconnectRow = onToggleReconnectRow,
                onReconnectAccount = onReconnectAccount,
                onDisconnectAccount = onDisconnectAccount,
            )
        }

        AddAccountAffordance(canAddAccount = canAddAccount, onClick = onAddAccountClick)

        if (accounts.size > 1 && activeUser != null) {
            Text(
                text = stringResource(Res.string.profile_active_account_details_title),
                style = AppTheme.typography.title,
                color = AppTheme.colors.onSurface,
            )
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(vertical = AppDimens.Spacing.xl2)) {
                    AccountFieldDump(activeUser)
                }
            }
        }
    }
}

@Composable
private fun AccountsListCard(
    accounts: List<SellerAccountUiModel>,
    expandedReconnectLocalIndex: Int?,
    onSetActiveAccount: (Int) -> Unit,
    onToggleReconnectRow: (Int) -> Unit,
    onReconnectAccount: (Int) -> Unit,
    onDisconnectAccount: (Int) -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            accounts.forEachIndexed { index, account ->
                AccountRow(
                    account = account,
                    isExpanded = expandedReconnectLocalIndex == account.localIndex,
                    onSetActiveAccount = { onSetActiveAccount(account.localIndex) },
                    onToggleReconnectRow = { onToggleReconnectRow(account.localIndex) },
                    onReconnectAccount = { onReconnectAccount(account.localIndex) },
                    onDisconnectAccount = { onDisconnectAccount(account.localIndex) },
                )
                if (index != accounts.lastIndex) {
                    HorizontalDivider(
                        color = AppTheme.colors.onSurface.copy(alpha = 0.08f),
                    )
                }
            }
        }
    }
}

/** One row in the multi-account list (PRD U1/D9). Tapping a `Needs reconnect` row toggles its
 * inline "Reconnect <account>" action in place (D9) - it never switches the active account. */
@Composable
private fun AccountRow(
    account: SellerAccountUiModel,
    isExpanded: Boolean,
    onSetActiveAccount: () -> Unit,
    onToggleReconnectRow: () -> Unit,
    onReconnectAccount: () -> Unit,
    onDisconnectAccount: () -> Unit,
) {
    val displayName = account.displayName.takeIf { it.isNotBlank() }
        ?: stringResource(Res.string.profile_olx_account)
    Column {
        Cell(
            modifier = Modifier.fillMaxWidth(),
            transparent = true,
            onClick = if (account.needsReconnect) onToggleReconnectRow else null,
            leading = {
                AppAvatar(
                    avatarUrl = account.avatarUrl,
                    fallbackInitial = displayName.firstOrNull()?.uppercaseChar()?.toString(),
                )
            },
            headline = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xs),
                ) {
                    Text(
                        text = displayName,
                        style = AppTheme.typography.body,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (account.isActive) {
                        Pill(
                            text = stringResource(Res.string.profile_account_active_badge),
                            iconResource = Res.drawable.ic_check,
                            color = AppTheme.colors.primary,
                        )
                    }
                    if (account.isBusiness) {
                        Pill(
                            text = stringResource(Res.string.account_business_badge),
                            iconResource = Res.drawable.ic_user,
                            color = AppTheme.colors.onSecondaryContainer,
                        )
                    }
                }
            },
            supporting = {
                Column {
                    Text(
                        text = account.email?.takeIf { it.isNotBlank() }
                            ?: stringResource(Res.string.profile_not_provided),
                        style = AppTheme.typography.caption,
                        color = AppTheme.colors.onSurfaceMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (account.needsReconnect) {
                        ErrorPill(label = stringResource(Res.string.account_needs_reconnect_badge))
                    } else if (!account.isActive) {
                        Text(
                            text = stringResource(Res.string.profile_account_set_active),
                            style = AppTheme.typography.caption,
                            color = AppTheme.colors.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .padding(top = AppDimens.Spacing.xs)
                                .clickable(onClick = onSetActiveAccount),
                        )
                    }
                }
            },
            trailing = {
                Text(
                    text = stringResource(Res.string.profile_logout),
                    style = AppTheme.typography.caption,
                    color = AppTheme.colors.error,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(onClick = onDisconnectAccount),
                )
            },
        )
        if (isExpanded && account.needsReconnect) {
            AppButton(
                text = stringResource(Res.string.profile_reconnect_account_action, displayName),
                onClick = onReconnectAccount,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppDimens.Spacing.xl5)
                    .padding(bottom = AppDimens.Spacing.xl3),
                style = AppButtonDefaults.secondary(),
            )
        }
    }
}

@Composable
private fun AddAccountAffordance(
    canAddAccount: Boolean,
    onClick: () -> Unit,
) {
    Column {
        Cell(
            modifier = Modifier.fillMaxWidth(),
            transparent = true,
            onClick = if (canAddAccount) onClick else null,
            leading = {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = if (canAddAccount) AppTheme.colors.primary else AppTheme.colors.onSurfaceMuted,
                )
            },
            headline = {
                Text(
                    text = stringResource(Res.string.profile_account_add_button),
                    style = AppTheme.typography.body,
                    fontWeight = FontWeight.Bold,
                    color = if (canAddAccount) AppTheme.colors.primary else AppTheme.colors.onSurfaceMuted,
                )
            },
        )
        if (!canAddAccount) {
            Text(
                text = stringResource(Res.string.profile_account_cap_reached_message),
                style = AppTheme.typography.caption,
                color = AppTheme.colors.onSurfaceMuted,
                modifier = Modifier.padding(horizontal = AppDimens.Spacing.xl5),
            )
        }
    }
}

@Composable
fun DeleteAccountDataConfirmSheet(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isDeleting: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.Spacing.xl5)
            .padding(bottom = AppDimens.Spacing.xl5),
        verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl4),
    ) {
        Text(
            text = stringResource(Res.string.profile_delete_account_data_title),
            style = AppTheme.typography.headline,
            color = AppTheme.colors.onBackground,
        )
        Text(
            text = stringResource(Res.string.profile_delete_account_data_message),
            style = AppTheme.typography.body,
            color = AppTheme.colors.onSurfaceMuted,
        )
        if (isDeleting) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(AppDimens.Size.xl6),
                    color = AppTheme.colors.primary,
                )
            }
        }
        AppButton(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(Res.string.profile_delete_account_data_confirm),
            onClick = onConfirm,
            enabled = !isDeleting,
            style = AppButtonStyle(
                backgroundColor = AppTheme.colors.error,
                contentColor = AppTheme.colors.onError,
            ),
        )
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            TextButton(
                onClick = onDismiss,
                enabled = !isDeleting,
            ) {
                Text(text = stringResource(Res.string.profile_delete_account_data_cancel))
            }
        }
    }
}

/**
 * SIR-83 U2: confirms adding a second/third OLX account before starting a forced-fresh-login
 * OAuth flow (D5). Wired as a top-level [com.sirelon.sellsnap.navigation.AppDestination] bottom
 * sheet in `App.kt`, next to [DeleteAccountDataConfirmSheet] - see that file for how [onContinue]
 * drives `createAuthorizationRequest()` + the force-reauth launcher.
 */
@Composable
fun AddOlxAccountConfirmSheet(
    siteLabel: String,
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.Spacing.xl5)
            .padding(bottom = AppDimens.Spacing.xl5),
        verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl4),
    ) {
        Text(
            text = stringResource(Res.string.profile_add_account_confirm_title),
            style = AppTheme.typography.headline,
            color = AppTheme.colors.onBackground,
        )
        Text(
            text = stringResource(Res.string.profile_add_account_confirm_subtitle, siteLabel),
            style = AppTheme.typography.body,
            color = AppTheme.colors.onSurfaceMuted,
        )
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(Res.string.profile_add_account_confirm_note),
                style = AppTheme.typography.caption,
                color = AppTheme.colors.onSurface,
                modifier = Modifier.padding(AppDimens.Spacing.xl4),
            )
        }
        AppButton(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(Res.string.profile_add_account_confirm_continue),
            onClick = onContinue,
            style = AppButtonDefaults.primary(),
        )
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(Res.string.profile_add_account_confirm_cancel))
            }
        }
    }
}

/**
 * SIR-83 U3: shown after a failed add/reconnect authorization attempt, and also (re-shown) when
 * the seller taps Add/Reconnect while an earlier failure's cooldown is still in force - never an
 * automatic retry either way. [remainingCooldownSeconds]/[consecutiveFailures] are suppliers
 * rather than snapshots so this composable can tick the countdown by re-reading the repository's
 * live, persisted state once a second (PRD: survives a force-quit mid-cooldown, Q7) instead of
 * running a blind local timer. After a 2nd consecutive failure the primary action stops offering
 * another in-app attempt and points at OLX's own recovery page instead (no cooldown gates that
 * link - opening a web page isn't an authorization attempt).
 */
@Composable
fun OlxAccountAuthFailedSheet(
    remainingCooldownSeconds: () -> Long,
    consecutiveFailures: () -> Int,
    onRetry: () -> Unit,
    onOpenOlxRecovery: () -> Unit,
    onDismiss: () -> Unit,
) {
    var cooldown by remember { mutableStateOf(remainingCooldownSeconds()) }
    val failures = remember { consecutiveFailures() }

    LaunchedEffect(Unit) {
        while (cooldown > 0) {
            delay(1_000L)
            cooldown = remainingCooldownSeconds()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.Spacing.xl5)
            .padding(bottom = AppDimens.Spacing.xl5),
        verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl4),
    ) {
        val recoveryMode = failures >= 2
        Text(
            text = if (recoveryMode) {
                stringResource(Res.string.profile_auth_failed_recovery_title)
            } else {
                stringResource(Res.string.profile_auth_failed_title)
            },
            style = AppTheme.typography.headline,
            color = AppTheme.colors.onBackground,
        )
        Text(
            text = if (recoveryMode) {
                stringResource(Res.string.profile_auth_failed_recovery_message)
            } else {
                stringResource(Res.string.profile_auth_failed_message)
            },
            style = AppTheme.typography.body,
            color = AppTheme.colors.onSurfaceMuted,
        )
        if (recoveryMode) {
            AppButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(Res.string.profile_auth_failed_recovery_action),
                onClick = onOpenOlxRecovery,
                style = AppButtonDefaults.primary(),
            )
        } else {
            AppButton(
                modifier = Modifier.fillMaxWidth(),
                text = if (cooldown > 0) {
                    stringResource(Res.string.profile_auth_failed_retry_countdown, cooldown.toInt())
                } else {
                    stringResource(Res.string.profile_auth_failed_retry)
                },
                onClick = onRetry,
                enabled = cooldown <= 0,
                style = AppButtonDefaults.primary(),
            )
        }
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(Res.string.profile_auth_failed_dismiss))
            }
        }
    }
}

/**
 * SIR-83 U5/D10: confirms disconnecting one account. Copy is careful to say SellSnap only forgets
 * local access - OLX exposes no revocation endpoint (TRD §6), so this must never claim SellSnap
 * revokes access on OLX's side. [onOpenOlxSettings] is the honest completion of that: a secondary
 * link to OLX's own account settings, the only place a seller can actually withdraw access.
 */
@Composable
fun DisconnectOlxAccountConfirmSheet(
    accountName: String?,
    isDisconnecting: Boolean,
    onConfirm: () -> Unit,
    onOpenOlxSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val displayName = accountName?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.profile_olx_account)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.Spacing.xl5)
            .padding(bottom = AppDimens.Spacing.xl5),
        verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl4),
    ) {
        Text(
            text = stringResource(Res.string.profile_disconnect_confirm_title, displayName),
            style = AppTheme.typography.headline,
            color = AppTheme.colors.onBackground,
        )
        Text(
            text = stringResource(Res.string.profile_disconnect_confirm_message),
            style = AppTheme.typography.body,
            color = AppTheme.colors.onSurfaceMuted,
        )
        TextButton(onClick = onOpenOlxSettings) {
            Text(text = stringResource(Res.string.profile_disconnect_confirm_olx_link))
        }
        if (isDisconnecting) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(AppDimens.Size.xl6),
                    color = AppTheme.colors.primary,
                )
            }
        }
        AppButton(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(Res.string.profile_disconnect_confirm_action),
            onClick = onConfirm,
            enabled = !isDisconnecting,
            style = AppButtonStyle(
                backgroundColor = AppTheme.colors.error,
                contentColor = AppTheme.colors.onError,
            ),
        )
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            TextButton(
                onClick = onDismiss,
                enabled = !isDisconnecting,
            ) {
                Text(text = stringResource(Res.string.profile_disconnect_confirm_cancel))
            }
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

@Composable
private fun LocationCard(
    location: OlxLocation?,
    isLoading: Boolean,
    onChangeLocation: () -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(AppDimens.Spacing.xl5),
            verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl4),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl3),
            ) {
                Box(
                    modifier = Modifier
                        .size(AppDimens.Size.xl11)
                        .clip(CircleShape)
                        .background(AppTheme.colors.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = AppTheme.colors.onSecondaryContainer,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.profile_location_title),
                        style = AppTheme.typography.title,
                    )
                    Text(
                        text = stringResource(Res.string.profile_location_subtitle),
                        style = AppTheme.typography.body,
                        color = AppTheme.colors.onSurfaceMuted,
                    )
                }
            }

            Text(
                text = when {
                    isLoading -> stringResource(Res.string.location_detecting)
                    location != null -> location.displayName
                    else -> stringResource(Res.string.location_not_available)
                },
                style = AppTheme.typography.body,
                color = if (location == null) AppTheme.colors.onSurfaceMuted else AppTheme.colors.onSurface,
            )

            AppButton(
                text = stringResource(Res.string.change_button),
                onClick = onChangeLocation,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
                style = AppButtonDefaults.outline(),
            )
        }
    }
}

@Composable
private fun ProfileField(
    label: String,
    value: String?,
) {
    Cell(
        transparent = true,
        headline = {
            Text(
                text = value?.takeIf { it.isNotBlank() }
                    ?: stringResource(Res.string.profile_not_provided),
                style = AppTheme.typography.body,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        overline = {
            Text(
                text = label,
                style = AppTheme.typography.caption,
                color = AppTheme.colors.onSurfaceMuted,
            )
        },
    )
}

