package com.sirelon.sellsnap.startup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sirelon.sellsnap.config.AppConfig
import com.sirelon.sellsnap.features.seller.ad.AdFlowTimerStore
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountMigration
import com.sirelon.sellsnap.features.seller.auth.data.OlxAuthRepository
import com.sirelon.sellsnap.features.seller.auth.data.OlxCountryStore
import com.sirelon.sellsnap.features.seller.auth.domain.SellerSessionMode
import com.sirelon.sellsnap.features.seller.profile.data.SellerAccountRepository
import com.sirelon.sellsnap.features.whatsnew.data.WhatsNewStore
import com.sirelon.sellsnap.navigation.AppDestination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppNavigationViewModel(
    private val authRepository: OlxAuthRepository,
    private val startupStore: AppStartupStore,
    private val adFlowTimerStore: AdFlowTimerStore,
    private val olxCountryStore: OlxCountryStore,
    private val analyticsConsentRepository: AnalyticsConsentRepository,
    private val olxAccountMigration: OlxAccountMigration,
    private val sellerAccountRepository: SellerAccountRepository,
    private val whatsNewStore: WhatsNewStore,
) : ViewModel() {

    private val _backStack = MutableStateFlow<List<AppDestination>>(listOf(AppDestination.Splash))
    val backStack: StateFlow<List<AppDestination>> = _backStack.asStateFlow()

    init {
        viewModelScope.launch {
            olxCountryStore.loadFromStorage()
            olxAccountMigration.migrateIfNeeded()
            resolveStartupDestination()
            // Runs after routing so it never delays startup, but still in this same coroutine -
            // a separate launch here raced migrateIfNeeded() above and lost: the account store's
            // recordFlow is only hydrated by loadFromStorage()/migration, both of which suspend,
            // so a concurrent coroutine reading it first always saw the default empty record and
            // silently swept nothing. Keeps accounts that are still being used, but not currently
            // active, from dying past OLX's ~30-day unused refresh-token window (SIR-83 keep-alive).
            runCatching { sellerAccountRepository.runKeepAliveRefresh() }
        }
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

    fun onOnboardingCompleted() {
        viewModelScope.launch {
            val next = if (analyticsConsentRepository.currentConsent() == AnalyticsConsent.Undecided) {
                AppDestination.ConsentPrompt
            } else {
                sessionDestination()
            }
            _backStack.value = listOf(next)
        }
    }

    fun onConsentAllow() {
        analyticsConsentRepository.setConsent(true)
        viewModelScope.launch {
            _backStack.value = listOf(sessionDestination())
        }
    }

    fun onConsentDecline() {
        analyticsConsentRepository.setConsent(false)
        viewModelScope.launch {
            _backStack.value = listOf(sessionDestination())
        }
    }

    private suspend fun resolveStartupDestination() {
        val initial: AppDestination = when {
            !startupStore.hasSeenOnboarding() -> {
                startupStore.markOnboardingSeen()
                // A fresh install has nothing to catch up on — seed the marker so the
                // What's New prompt never fires for this, the user's very first session.
                whatsNewStore.markVersionSeen(AppConfig.appVersionName)
                AppDestination.SellerOnboarding
            }

            analyticsConsentRepository.currentConsent() == AnalyticsConsent.Undecided ->
                AppDestination.ConsentPrompt

            else -> sessionDestination()
        }
        _backStack.value = listOf(initial)
    }

    private suspend fun sessionDestination(): AppDestination = runCatching {
        val session = authRepository.currentSession()
        when (session.mode) {
            // F4/D7 fix: Authenticated now means "at least one account is on file for the active
            // country" (see OlxAuthRepository.currentSession), regardless of whether its token is
            // healthy - so this always routes to Seller, never back to SellerLanding. The
            // getAuthenticatedUser() call below just warms the profile cache and, via the
            // authorized client's bearer-refresh plugin, proactively detects and marks a dead
            // token NeedsReconnect; its failure (network blip or otherwise) must not bounce an
            // existing seller back to the landing/guest screen.
            SellerSessionMode.Authenticated -> {
                // Also backfills the migrated (pre-SIR-83) account's olxUserId/profile on its
                // first successful fetch after migration - see SellerAccountRepository.
                // refreshProfile()'s doc. Without this, that account can never be matched by a
                // later add-account attempt that resolves to the same OLX user.
                sellerAccountRepository.refreshProfile().exceptionOrNull()?.printStackTrace()
                AppDestination.Seller
            }

            SellerSessionMode.Guest -> AppDestination.Seller
            SellerSessionMode.Unauthenticated -> AppDestination.SellerLanding
        }
    }.getOrElse {
        it.printStackTrace()
        AppDestination.SellerLanding
    }
}
