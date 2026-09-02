package com.sirelon.sellsnap.startup

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.serialization.saved
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavBackStack
import com.sirelon.sellsnap.config.AppConfig
import com.sirelon.sellsnap.features.seller.ad.AdFlowTimerStore
import com.sirelon.sellsnap.features.media.SharedImagesBridge
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountMigration
import com.sirelon.sellsnap.features.seller.auth.data.OlxAuthRepository
import com.sirelon.sellsnap.features.seller.auth.data.OlxCountryStore
import com.sirelon.sellsnap.features.seller.auth.domain.SellerSessionMode
import com.sirelon.sellsnap.features.seller.profile.data.SellerAccountRepository
import com.sirelon.sellsnap.features.whatsnew.data.WhatsNewStore
import com.sirelon.sellsnap.navigation.AppKey
import com.sirelon.sellsnap.navigation.appNavigationSavedStateConfiguration
import com.sirelon.sellsnap.navigation.isSellerFlowEntry
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class AppNavigationViewModel(
    savedStateHandle: SavedStateHandle,
    private val authRepository: OlxAuthRepository,
    private val startupStore: AppStartupStore,
    private val adFlowTimerStore: AdFlowTimerStore,
    private val olxCountryStore: OlxCountryStore,
    private val analyticsConsentRepository: AnalyticsConsentRepository,
    private val olxAccountMigration: OlxAccountMigration,
    private val sellerAccountRepository: SellerAccountRepository,
    private val whatsNewStore: WhatsNewStore,
) : ViewModel() {

    // Owns the real back stack directly (persisted across process death via SavedStateHandle) -
    // there is no separate shadow list to keep in sync with it.
    val backStack: NavBackStack<AppKey> by savedStateHandle.saved(
        serializer = NavBackStack.serializer(AppKey.serializer()),
        configuration = appNavigationSavedStateConfiguration,
    ) { NavBackStack(AppKey.Splash) }

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

        // Photos shared in from the OS share sheet (MainActivity.publishSharedImages) always
        // force the user onto GenerateAd, regardless of where the back stack currently sits -
        // GenerateAdViewModel picks the files themselves up independently from the same bridge.
        SharedImagesBridge.pending
            .filterNotNull()
            .onEach { if (backStack.lastOrNull() != AppKey.GenerateAd) popToAdRoot() }
            .launchIn(viewModelScope)
    }

    fun navigateTo(destination: AppKey) {
        if (backStack.lastOrNull() != destination) {
            backStack.add(destination)
        }
    }

    fun popDestination() {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
        }
    }

    fun popToAdRoot() {
        adFlowTimerStore.clear()
        backStack.apply {
            clear()
            add(AppKey.GenerateAd)
        }
    }

    fun replaceWith(destination: AppKey) {
        backStack.apply {
            clear()
            add(destination)
        }
    }

    fun exitGuestModeToLanding() {
        viewModelScope.launch {
            authRepository.exitGuestMode()
            backStack.apply {
                clear()
                add(AppKey.SellerLanding)
            }
        }
    }

    fun onOnboardingCompleted() {
        viewModelScope.launch {
            val next = if (analyticsConsentRepository.currentConsent() == AnalyticsConsent.Undecided) {
                AppKey.ConsentPrompt
            } else {
                sessionDestination()
            }
            backStack.apply {
                clear()
                add(next)
            }
        }
    }

    fun onConsentAllow() {
        analyticsConsentRepository.setConsent(true)
        viewModelScope.launch {
            backStack.apply {
                clear()
                add(sessionDestination())
            }
        }
    }

    fun onConsentDecline() {
        analyticsConsentRepository.setConsent(false)
        viewModelScope.launch {
            backStack.apply {
                clear()
                add(sessionDestination())
            }
        }
    }

    private suspend fun resolveStartupDestination() {
        val initial: AppKey = when {
            !startupStore.hasSeenOnboarding() -> {
                startupStore.markOnboardingSeen()
                // A fresh install has nothing to catch up on — seed the marker so the
                // What's New prompt never fires for this, the user's very first session.
                whatsNewStore.markVersionSeen(AppConfig.appVersionName)
                AppKey.SellerOnboarding
            }

            analyticsConsentRepository.currentConsent() == AnalyticsConsent.Undecided ->
                AppKey.ConsentPrompt

            else -> sessionDestination()
        }
        // A restored stack whose bottom entry is already a seller-flow tab represents a real
        // in-progress position (mid-draft on PreviewAd, a non-default tab, ...) that a fresh
        // single-entry resolution would otherwise destroy on every process recreation - keep it
        // as long as resolution independently agrees we still belong in the seller flow.
        val restoredRoot = backStack.firstOrNull()
        if (initial == AppKey.GenerateAd && restoredRoot?.isSellerFlowEntry == true) {
            return
        }
        backStack.apply {
            clear()
            add(initial)
        }
    }

    private suspend fun sessionDestination(): AppKey = runCatching {
        val session = authRepository.currentSession()
        when (session.mode) {
            // F4/D7 fix: Authenticated now means "at least one account is on file for the active
            // country" (see OlxAuthRepository.currentSession), regardless of whether its token is
            // healthy - so this always routes into the seller flow, never back to SellerLanding.
            // The getAuthenticatedUser() call below just warms the profile cache and, via the
            // authorized client's bearer-refresh plugin, proactively detects and marks a dead
            // token NeedsReconnect; its failure (network blip or otherwise) must not bounce an
            // existing seller back to the landing/guest screen.
            SellerSessionMode.Authenticated -> {
                // Also backfills the migrated (pre-SIR-83) account's olxUserId/profile on its
                // first successful fetch after migration - see SellerAccountRepository.
                // refreshProfile()'s doc. Without this, that account can never be matched by a
                // later add-account attempt that resolves to the same OLX user.
                sellerAccountRepository.refreshProfile().exceptionOrNull()?.printStackTrace()
                AppKey.GenerateAd
            }

            SellerSessionMode.Guest -> AppKey.GenerateAd
            SellerSessionMode.Unauthenticated -> AppKey.SellerLanding
        }
    }.getOrElse {
        it.printStackTrace()
        AppKey.SellerLanding
    }
}
