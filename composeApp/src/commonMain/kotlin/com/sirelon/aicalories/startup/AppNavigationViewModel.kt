package com.sirelon.sellsnap.startup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sirelon.sellsnap.features.seller.ad.AdFlowTimerStore
import com.sirelon.sellsnap.features.seller.auth.data.OlxApiClient
import com.sirelon.sellsnap.features.seller.auth.data.OlxAuthRepository
import com.sirelon.sellsnap.features.seller.auth.domain.SellerSessionMode
import com.sirelon.sellsnap.navigation.AppDestination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppNavigationViewModel(
    private val authRepository: OlxAuthRepository,
    private val olxApiClient: OlxApiClient,
    private val startupStore: AppStartupStore,
    private val adFlowTimerStore: AdFlowTimerStore,
) : ViewModel() {

    private val _backStack = MutableStateFlow<List<AppDestination>>(listOf(AppDestination.Splash))
    val backStack: StateFlow<List<AppDestination>> = _backStack.asStateFlow()

    init {
        viewModelScope.launch { resolveStartupDestination() }
    }

    fun navigateTo(destination: AppDestination) {
        val current = _backStack.value
        if (current.lastOrNull() != destination) {
            _backStack.value = current + destination
        }
    }

    fun popDestination() {
        val current = _backStack.value
        if (current.size > 1) {
            _backStack.value = current.dropLast(1)
        }
    }

    fun popToAdRoot() {
        adFlowTimerStore.clear()
        _backStack.value = listOf(AppDestination.Seller)
    }

    fun replaceWith(destination: AppDestination) {
        _backStack.value = listOf(destination)
    }

    fun exitGuestModeToLanding() {
        viewModelScope.launch {
            authRepository.exitGuestMode()
            _backStack.value = listOf(AppDestination.SellerLanding)
        }
    }

    private suspend fun resolveStartupDestination() {
        val initial: AppDestination = if (!startupStore.hasSeenOnboarding()) {
            startupStore.markOnboardingSeen()
            AppDestination.SellerOnboarding
        } else {
            val session = authRepository.currentSession()
            when (session.mode) {
                SellerSessionMode.Authenticated -> runCatching {
                    olxApiClient.getAuthenticatedUser()
                    AppDestination.Seller
                }
                    .getOrElse {
                        it.printStackTrace()
                        AppDestination.SellerLanding
                    }

                SellerSessionMode.Guest -> AppDestination.Seller
                SellerSessionMode.Unauthenticated -> AppDestination.SellerLanding
            }
        }
        _backStack.value = listOf(initial)
    }
}
