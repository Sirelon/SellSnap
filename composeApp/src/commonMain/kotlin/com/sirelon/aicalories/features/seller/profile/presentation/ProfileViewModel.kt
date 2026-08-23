package com.sirelon.sellsnap.features.seller.profile.presentation

import androidx.lifecycle.viewModelScope
import com.sirelon.sellsnap.features.common.presentation.BaseViewModel
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountRecord
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountState
import com.sirelon.sellsnap.features.seller.auth.data._currentOlxCountry
import com.sirelon.sellsnap.features.seller.profile.data.AddAccountOutcome
import com.sirelon.sellsnap.features.seller.profile.data.SellerAccountRepository
import com.sirelon.sellsnap.features.seller.profile.presentation.ProfileContract.ProfileEffect
import com.sirelon.sellsnap.features.seller.profile.presentation.ProfileContract.ProfileEvent
import com.sirelon.sellsnap.features.seller.profile.presentation.ProfileContract.ProfileState
import com.sirelon.sellsnap.features.seller.profile.presentation.ProfileContract.SellerAccountUiModel
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.error_location_fetch_failed
import com.sirelon.sellsnap.generated.resources.error_olx_auth_prepare_failed
import com.sirelon.sellsnap.generated.resources.error_user_profile_fetch_failed
import com.sirelon.sellsnap.generated.resources.profile_olx_account
import com.sirelon.sellsnap.generated.resources.profile_add_account_duplicate_message
import com.sirelon.sellsnap.startup.AnalyticsConsent
import com.sirelon.sellsnap.startup.AnalyticsConsentRepository
import com.sirelon.sellsnap.startup.AppThemeRepository
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

class ProfileViewModel(
    private val accountRepository: SellerAccountRepository,
    private val themeRepository: AppThemeRepository,
    private val analyticsConsentRepository: AnalyticsConsentRepository,
) : BaseViewModel<ProfileState, ProfileEvent, ProfileEffect>() {

    init {
        accountRepository
            .user
            .onEach { user ->
                setState {
                    it.copy(user = user)
                }
            }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            val initialSessionMode = accountRepository.currentSession().mode
            setState { it.copy(sessionMode = initialSessionMode) }
        }
        accountRepository
            .sessionModeFlow
            .onEach { mode ->
                setState { it.copy(sessionMode = mode) }
            }
            .launchIn(viewModelScope)

        themeRepository
            .themeMode
            .onEach { themeMode ->
                setState {
                    it.copy(themeMode = themeMode)
                }
            }
            .launchIn(viewModelScope)

        analyticsConsentRepository
            .consent
            .onEach { consent ->
                setState {
                    it.copy(analyticsConsentGranted = consent == AnalyticsConsent.Granted)
                }
            }
            .launchIn(viewModelScope)

        // SIR-83: the accounts list for the current OLX country, active account first. Size <= 1
        // keeps ProfileScreen's single-account layout exactly as it renders today (PRD U1 AC).
        accountRepository
            .accountsRecordFlow
            .onEach { record ->
                val countryCode = _currentOlxCountry.code
                val activeIndex = record.activeByCountry[countryCode]
                val countryAccounts = record.accounts
                    .filter { it.countryCode == countryCode }
                    .sortedByDescending { it.localIndex == activeIndex }
                setState {
                    it.copy(
                        accounts = countryAccounts.map { account ->
                            account.toUiModel(isActive = account.localIndex == activeIndex)
                        },
                        canAddAccount = accountRepository.canAddAccount(countryCode),
                    )
                }
            }
            .launchIn(viewModelScope)

        refresh()
    }

    override fun initialState(): ProfileState = ProfileState()

    override fun onEvent(event: ProfileEvent) {
        when (event) {
            ProfileEvent.LoginClicked -> startAuthorization(forceReauth = false)
            ProfileEvent.LogoutClicked -> logout()
            ProfileEvent.ChangeLocationClicked -> updateLocation()
            ProfileEvent.RefreshClicked -> refresh()
            is ProfileEvent.ThemeModeSelected -> themeRepository.setThemeMode(event.themeMode)
            is ProfileEvent.SetAnalyticsConsent -> analyticsConsentRepository.setConsent(event.enabled)
            is ProfileEvent.SetActiveAccountClicked -> setActiveAccount(event.localIndex)
            is ProfileEvent.NeedsReconnectRowClicked -> toggleReconnectRow(event.localIndex)
            is ProfileEvent.ReconnectClicked -> reconnect(event.localIndex)
        }
    }

    /**
     * Handles the OLX OAuth callback for BOTH add-account and reconnect (D9's rationale: a
     * reconnect is just another add-account attempt that happens to dedupe against an existing
     * localIndex - see [SellerAccountRepository.addAccount]'s doc). Calls [addAccount] directly
     * rather than the back-compat [SellerAccountRepository.completeAuthorization] wrapper so the
     * full [AddAccountOutcome] is available to drive the duplicate-account message (U2) and the
     * failed-authorization sheet (U3).
     */
    fun onCallbackReceived(callbackUrl: String) {
        viewModelScope.launch {
            setState { it.copy(isAuthenticating = true, errorMessage = null) }
            when (val outcome = accountRepository.addAccount(callbackUrl)) {
                is AddAccountOutcome.Added -> {
                    setState { it.copy(isAuthenticating = false, expandedReconnectLocalIndex = null) }
                }

                is AddAccountOutcome.ReconnectedDuplicate -> {
                    setState { it.copy(isAuthenticating = false, expandedReconnectLocalIndex = null) }
                    val accountName = outcome.account.profile?.name?.takeIf { it.isNotBlank() }
                        ?: getString(Res.string.profile_olx_account)
                    postEffect(
                        ProfileEffect.ShowMessage(
                            getString(Res.string.profile_add_account_duplicate_message, accountName),
                        ),
                    )
                }

                is AddAccountOutcome.Failed -> {
                    setState { it.copy(isAuthenticating = false) }
                    postEffect(ProfileEffect.AuthorizationFailed)
                }
            }
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            setState { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                val profileResult = accountRepository.refreshProfile()
                val location = accountRepository.savedLocation()
                profileResult to location
            }
                .onSuccess { (profileResult, location) ->
                    profileResult.onFailure { error ->
                        showError(getString(Res.string.error_user_profile_fetch_failed))
                    }
                    setState {
                        it.copy(
                            isLoading = false,
                            user = profileResult.getOrNull(),
                            location = location,
                        )
                    }
                }
                .onFailure { error ->
                    showError(getString(Res.string.error_user_profile_fetch_failed))
                    setState { it.copy(isLoading = false) }
                }
        }
    }

    private fun startAuthorization(forceReauth: Boolean) {
        viewModelScope.launch {
            setState { it.copy(isAuthenticating = true, errorMessage = null) }
            runCatching { accountRepository.createAuthorizationRequest() }
                .onSuccess { request ->
                    setState { it.copy(isAuthenticating = false) }
                    postEffect(ProfileEffect.LaunchOlxAuthFlow(request.url, forceReauth))
                }
                .onFailure { error ->
                    showError(getString(Res.string.error_olx_auth_prepare_failed))
                    setState { it.copy(isAuthenticating = false) }
                }
        }
    }

    private fun setActiveAccount(localIndex: Int) {
        viewModelScope.launch {
            accountRepository.setActiveAccount(countryCode = _currentOlxCountry.code, localIndex = localIndex)
        }
    }

    private fun toggleReconnectRow(localIndex: Int) {
        setState {
            it.copy(
                expandedReconnectLocalIndex = if (it.expandedReconnectLocalIndex == localIndex) {
                    null
                } else {
                    localIndex
                },
            )
        }
    }

    /** Reconnect shares the add-account cooldown (country-level only - see the SIR-83 task brief
     * on why a specific [localIndex] is never passed to the cooldown/failure reads) and never
     * auto-retries (PRD U3): if a cooldown from an earlier failure is still in force, this surfaces
     * the same failed-authorization sheet instead of starting a fresh attempt. */
    private fun reconnect(localIndex: Int) {
        val countryCode = _currentOlxCountry.code
        if (accountRepository.remainingCooldownSeconds(localIndex = null, countryCode = countryCode) > 0) {
            postEffect(ProfileEffect.AuthorizationFailed)
            return
        }
        startAuthorization(forceReauth = true)
    }

    private fun logout() {
        viewModelScope.launch {
            accountRepository.logout()
            postEffect(ProfileEffect.NavigateToLanding)
        }
    }

    private fun updateLocation() {
        viewModelScope.launch {
            setState { it.copy(isLocationLoading = true, errorMessage = null) }
            runCatching { accountRepository.refreshLocationFromDevice() }
                .onSuccess { location ->
                    if (location == null) {
                        postEffect(ProfileEffect.ShowMessage(getString(Res.string.error_location_fetch_failed)))
                    }
                    setState {
                        it.copy(
                            isLocationLoading = false,
                            location = location ?: it.location,
                        )
                    }
                }
                .onFailure { error ->
                    setState { it.copy(isLocationLoading = false) }
                    showError(getString(Res.string.error_location_fetch_failed))
                }
        }
    }

    private fun showError(message: String) {
        setState { it.copy(errorMessage = message) }
        postEffect(ProfileEffect.ShowMessage(message))
    }
}

private fun OlxAccountRecord.toUiModel(isActive: Boolean): SellerAccountUiModel {
    val snapshot = profile
    return SellerAccountUiModel(
        localIndex = localIndex,
        displayName = snapshot?.name?.takeIf { it.isNotBlank() }.orEmpty(),
        email = snapshot?.email,
        avatarUrl = snapshot?.avatarUrl,
        isBusiness = snapshot?.isBusiness ?: false,
        needsReconnect = state == OlxAccountState.NeedsReconnect,
        isActive = isActive,
    )
}
