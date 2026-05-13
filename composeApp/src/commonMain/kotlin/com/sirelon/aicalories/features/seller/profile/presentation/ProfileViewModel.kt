package com.sirelon.sellsnap.features.seller.profile.presentation

import androidx.lifecycle.viewModelScope
import com.sirelon.sellsnap.features.common.presentation.BaseViewModel
import com.sirelon.sellsnap.features.seller.profile.data.SellerAccountRepository
import com.sirelon.sellsnap.features.seller.profile.presentation.ProfileContract.ProfileEffect
import com.sirelon.sellsnap.features.seller.profile.presentation.ProfileContract.ProfileEvent
import com.sirelon.sellsnap.features.seller.profile.presentation.ProfileContract.ProfileState
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.error_location_fetch_failed
import com.sirelon.sellsnap.generated.resources.error_olx_auth_complete_failed
import com.sirelon.sellsnap.generated.resources.error_olx_auth_prepare_failed
import com.sirelon.sellsnap.generated.resources.error_user_profile_fetch_failed
import com.sirelon.sellsnap.startup.AppThemeRepository
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

class ProfileViewModel(
    private val accountRepository: SellerAccountRepository,
    private val themeRepository: AppThemeRepository,
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

        themeRepository
            .themeMode
            .onEach { themeMode ->
                setState {
                    it.copy(themeMode = themeMode)
                }
            }
            .launchIn(viewModelScope)

        refresh()
    }

    override fun initialState(): ProfileState = ProfileState()

    override fun onEvent(event: ProfileEvent) {
        when (event) {
            ProfileEvent.LoginClicked -> startAuthorization()
            ProfileEvent.LogoutClicked -> logout()
            ProfileEvent.ChangeLocationClicked -> updateLocation()
            ProfileEvent.RefreshClicked -> refresh()
            is ProfileEvent.ThemeModeSelected -> themeRepository.setThemeMode(event.themeMode)
        }
    }

    fun onCallbackReceived(callbackUrl: String) {
        viewModelScope.launch {
            setState { it.copy(isAuthenticating = true, errorMessage = null) }
            accountRepository.completeAuthorization(callbackUrl)
                .onSuccess { user ->
                    setState {
                        it.copy(
                            isAuthenticating = false,
                            user = user,
                            errorMessage = null,
                        )
                    }
                }
                .onFailure { error ->
                    showError(getString(Res.string.error_olx_auth_complete_failed))
                    setState { it.copy(isAuthenticating = false) }
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

    private fun startAuthorization() {
        viewModelScope.launch {
            setState { it.copy(isAuthenticating = true, errorMessage = null) }
            runCatching { accountRepository.createAuthorizationRequest() }
                .onSuccess { request ->
                    setState { it.copy(isAuthenticating = false) }
                    postEffect(ProfileEffect.LaunchOlxAuthFlow(request.url))
                }
                .onFailure { error ->
                    showError(getString(Res.string.error_olx_auth_prepare_failed))
                    setState { it.copy(isAuthenticating = false) }
                }
        }
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
