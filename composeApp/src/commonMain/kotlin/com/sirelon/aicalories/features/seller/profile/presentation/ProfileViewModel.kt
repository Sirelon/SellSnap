package com.sirelon.sellsnap.features.seller.profile.presentation

import androidx.lifecycle.viewModelScope
import com.sirelon.sellsnap.features.common.presentation.BaseViewModel
import com.sirelon.sellsnap.features.seller.profile.data.SellerAccountRepository
import com.sirelon.sellsnap.features.seller.profile.presentation.ProfileContract.ProfileEffect
import com.sirelon.sellsnap.features.seller.profile.presentation.ProfileContract.ProfileEvent
import com.sirelon.sellsnap.features.seller.profile.presentation.ProfileContract.ProfileState
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val accountRepository: SellerAccountRepository,
) : BaseViewModel<ProfileState, ProfileEvent, ProfileEffect>() {

    init {
        accountRepository
            .user
            .onEach { user ->
                setState {
                    it.copy(
                        isLoading = false,
                        user = user,
                    )
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
                    showError(error.message ?: "Failed to complete OLX authorization.")
                    setState { it.copy(isAuthenticating = false) }
                }
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            setState { it.copy(isLoading = true, errorMessage = null) }
            accountRepository.refreshProfile()
            val location = accountRepository.savedLocation()
            setState {
                it.copy(
                    isLoading = false,
                    location = location,
                )
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
                    showError(error.message ?: "Failed to prepare OLX authorization.")
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
                        postEffect(ProfileEffect.ShowMessage("Location is not available."))
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
                    showError(error.message ?: "Failed to update location.")
                }
        }
    }

    private fun showError(message: String) {
        setState { it.copy(errorMessage = message) }
        postEffect(ProfileEffect.ShowMessage(message))
    }
}
