package com.sirelon.sellsnap.features.seller.profile.presentation

import com.sirelon.sellsnap.designsystem.AppThemeMode
import com.sirelon.sellsnap.features.seller.auth.domain.OlxUser
import com.sirelon.sellsnap.features.seller.auth.domain.SellerSessionMode
import com.sirelon.sellsnap.features.seller.location.OlxLocation

interface ProfileContract {

    /** Lightweight row model for the Profile accounts list (SIR-83 U1) - deliberately smaller
     * than [OlxUser]: the list only ever shows avatar/name/email/badges, never the full field
     * dump (id, phone, status, etc.) that the single/active-account detail view still renders
     * from [ProfileState.user]. */
    data class SellerAccountUiModel(
        val localIndex: Int,
        val displayName: String,
        val email: String?,
        val avatarUrl: String?,
        val isBusiness: Boolean,
        val needsReconnect: Boolean,
        val isActive: Boolean,
    )

    data class ProfileState(
        val isLoading: Boolean = true,
        val isAuthenticating: Boolean = false,
        val isLocationLoading: Boolean = false,
        val user: OlxUser? = null,
        val location: OlxLocation? = null,
        val themeMode: AppThemeMode = AppThemeMode.System,
        val analyticsConsentGranted: Boolean = false,
        val errorMessage: String? = null,
        // F4 fix: derived from the account-store-backed session mode, not from whether a
        // profile fetch happened to succeed - a dead token (NeedsReconnect) must not read as Guest.
        val sessionMode: SellerSessionMode = SellerSessionMode.Unauthenticated,
        // SIR-83: accounts for the current OLX country, active-first. Size <= 1 keeps today's
        // single-account layout untouched (PRD U1 AC); size >= 2 renders the accounts list.
        val accounts: List<SellerAccountUiModel> = emptyList(),
        // Mirrors SellerAccountRepository.canAddAccount(countryCode) (cap = 3, D4) - drives the
        // "Add OLX account" affordance's enabled/disabled + explanatory copy.
        val canAddAccount: Boolean = true,
        // D9: which NeedsReconnect row currently has its inline "Reconnect <account>" action
        // revealed. Selecting such a row never switches the active account - it only toggles this.
        val expandedReconnectLocalIndex: Int? = null,
    ) {
        val isGuest: Boolean
            get() = sessionMode == SellerSessionMode.Guest
    }

    sealed interface ProfileEvent {
        data object LoginClicked : ProfileEvent
        data object LogoutClicked : ProfileEvent
        data object ChangeLocationClicked : ProfileEvent
        data object RefreshClicked : ProfileEvent
        data class ThemeModeSelected(val themeMode: AppThemeMode) : ProfileEvent
        data class SetAnalyticsConsent(val enabled: Boolean) : ProfileEvent

        // SIR-83
        data class SetActiveAccountClicked(val localIndex: Int) : ProfileEvent
        data class NeedsReconnectRowClicked(val localIndex: Int) : ProfileEvent
        data class ReconnectClicked(val localIndex: Int) : ProfileEvent
    }

    sealed interface ProfileEffect {
        data class LaunchOlxAuthFlow(val url: String, val forceReauth: Boolean = false) : ProfileEffect
        data class ShowMessage(val message: String) : ProfileEffect
        data object NavigateToLanding : ProfileEffect

        // SIR-83 (U3): the Route reacts to this by opening the top-level "couldn't connect"
        // bottom sheet (AppDestination.OlxAccountAuthFailed), which reads the live
        // cooldown/consecutive-failure state straight from SellerAccountRepository rather than
        // carrying a snapshot here - it's shown both after a genuine failure and when a seller
        // taps Add/Reconnect while a cooldown from an earlier failure is still in force.
        data object AuthorizationFailed : ProfileEffect
    }
}
