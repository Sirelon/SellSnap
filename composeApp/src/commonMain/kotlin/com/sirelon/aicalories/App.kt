package com.sirelon.sellsnap

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.compose.setSingletonImageLoaderFactory
import com.mohamedrejeb.calf.picker.coil.KmpFileFetcher
import com.sirelon.sellsnap.analytics.Analytics
import com.sirelon.sellsnap.analytics.AnalyticsEvents
import com.sirelon.sellsnap.designsystem.AppTheme
import com.sirelon.sellsnap.designsystem.screens.LoadingOverlay
import com.sirelon.sellsnap.di.appModule
import com.sirelon.sellsnap.di.networkModule
import com.sirelon.sellsnap.features.consent.ConsentScreen
import com.sirelon.sellsnap.features.seller.ad.AdRootScreen
import com.sirelon.sellsnap.features.seller.auth.data._currentOlxCountry
import com.sirelon.sellsnap.features.seller.auth.presentation.OlxCountryPickerScreenRoute
import com.sirelon.sellsnap.features.seller.auth.presentation.SellerLandingScreenRoute
import com.sirelon.sellsnap.features.seller.auth.presentation.rememberOlxAuthLauncher
import com.sirelon.sellsnap.features.seller.onboarding.OnboardingScreen
import com.sirelon.sellsnap.features.seller.profile.data.SellerAccountRepository
import com.sirelon.sellsnap.features.seller.profile.ui.AddOlxAccountConfirmSheet
import com.sirelon.sellsnap.features.seller.profile.ui.DeleteAccountDataConfirmSheet
import com.sirelon.sellsnap.features.seller.profile.ui.DisconnectOlxAccountConfirmSheet
import com.sirelon.sellsnap.features.seller.profile.ui.OlxAccountAuthFailedSheet
import com.sirelon.sellsnap.navigation.BottomSheetSceneStrategy
import com.sirelon.sellsnap.navigation.AppDestination
import com.sirelon.sellsnap.legal.LegalLinks
import com.sirelon.sellsnap.navigation.appNavigationSavedStateConfiguration
import com.sirelon.sellsnap.startup.AppNavigationViewModel
import com.sirelon.sellsnap.startup.AppThemeRepository
import kotlinx.coroutines.launch
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.dsl.koinConfiguration

@Composable
@Preview
fun App() {
    setSingletonImageLoaderFactory {
        ImageLoader.Builder(it)
            .components {
                add(KmpFileFetcher.Factory())
            }
            .build()
    }

    KoinApplication(
        configuration = koinConfiguration {
            modules(appModule, networkModule)
        },
    ) {
        val themeRepository: AppThemeRepository = koinInject()
        val themeMode by themeRepository.themeMode.collectAsStateWithLifecycle()

        AppTheme(themeMode = themeMode) {
            val navVm: AppNavigationViewModel = koinViewModel()
            val accountRepository: SellerAccountRepository = koinInject()
            val backStackList by navVm.backStack.collectAsStateWithLifecycle()
            val coroutineScope = rememberCoroutineScope()
            var isDeletingAccountData by remember { mutableStateOf(false) }
            var isDisconnectingAccount by remember { mutableStateOf(false) }
            val uriHandler = LocalUriHandler.current
            // SIR-83: add-account/reconnect must force a fresh OLX login (D5). This sheet-level
            // launcher is independent of the one AdRootScreen threads into ProfileScreenRoute for
            // its own inline Reconnect action - both are stateless wrappers around the same
            // platform mechanism, so having two instances is harmless (see OlxExternalAuthLauncher).
            val addAccountAuthLauncher = rememberOlxAuthLauncher(forceReauth = true)
            fun startAddOrReconnectAuthorization() {
                coroutineScope.launch {
                    runCatching { accountRepository.createAuthorizationRequest(forceReauth = true) }
                        .onSuccess { request -> addAccountAuthLauncher(request.url) }
                }
            }

            val navBackStack = rememberNavBackStack(
                appNavigationSavedStateConfiguration,
                AppDestination.Splash,
            )
            val sceneStrategies = remember {
                listOf(
                    BottomSheetSceneStrategy<NavKey>(),
                    SinglePaneSceneStrategy<NavKey>(),
                )
            }
            LaunchedEffect(backStackList) {
                val restoredFromSavedState = navBackStack.toList() != listOf(AppDestination.Splash)
                val hasResolvedStartup = backStackList != listOf(AppDestination.Splash)
                if ((hasResolvedStartup || !restoredFromSavedState) && navBackStack.toList() != backStackList) {
                    navBackStack.clear()
                    navBackStack.addAll(backStackList)
                }
            }

            NavDisplay(
                modifier = Modifier.fillMaxSize(),
                backStack = navBackStack,
                onBack = {
                    if (!isDeletingAccountData) {
                        navVm.popDestination()
                    }
                },
                sceneStrategies = sceneStrategies,
                entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator<NavKey>()),
                entryProvider = entryProvider<NavKey> {

                    entry<AppDestination.Splash> {
                        LoadingOverlay(isLoading = true) {}
                    }

                    entry<AppDestination.SellerOnboarding> {
                        val analytics: Analytics = koinInject()
                        OnboardingScreen {
                            analytics.logEvent(AnalyticsEvents.ONBOARDING_COMPLETED)
                            navVm.onOnboardingCompleted()
                        }
                    }

                    entry<AppDestination.ConsentPrompt> {
                        val uriHandler = LocalUriHandler.current
                        ConsentScreen(
                            onAllow = { navVm.onConsentAllow() },
                            onDecline = { navVm.onConsentDecline() },
                            onOpenPrivacy = { uriHandler.openUri(LegalLinks.PRIVACY_URL) },
                            onOpenTerms = { uriHandler.openUri(LegalLinks.TERMS_URL) },
                        )
                    }

                    entry<AppDestination.SellerLanding> {
                        SellerLandingScreenRoute(
                            openHome = { navVm.replaceWith(AppDestination.Seller) },
                            openCountryPicker = { navVm.navigateTo(AppDestination.OlxCountryPicker) },
                        )
                    }

                    entry<AppDestination.OlxCountryPicker> {
                        OlxCountryPickerScreenRoute(
                            onBack = { navVm.popDestination() },
                            openHome = { navVm.replaceWith(AppDestination.Seller) },
                        )
                    }

                    entry<AppDestination.Seller> {
                        AdRootScreen(
                            onConnectOlxClick = navVm::exitGuestModeToLanding,
                            onLogout = { navVm.replaceWith(AppDestination.SellerLanding) },
                            onDeleteAccountDataRequested = {
                                navVm.navigateTo(AppDestination.DeleteAccountDataConfirm)
                            },
                            popToAdRoot = navVm::popToAdRoot,
                            onAddAccountRequested = {
                                // U3: never let a tap start a fresh attempt while an earlier
                                // failure's cooldown is still in force - show that state instead.
                                val countryCode = _currentOlxCountry.code
                                if (accountRepository.remainingCooldownSeconds(null, countryCode) > 0) {
                                    navVm.navigateTo(AppDestination.OlxAccountAuthFailed)
                                } else {
                                    navVm.navigateTo(AppDestination.AddOlxAccountConfirm)
                                }
                            },
                            onAddAccountFailed = {
                                navVm.navigateTo(AppDestination.OlxAccountAuthFailed)
                            },
                            onDisconnectRequested = { localIndex ->
                                navVm.navigateTo(AppDestination.DisconnectOlxAccountConfirm(localIndex))
                            },
                        )
                    }

                    entry<AppDestination.DeleteAccountDataConfirm>(
                        metadata = BottomSheetSceneStrategy.bottomSheet(),
                    ) {
                        val platformContext = LocalPlatformContext.current
                        DeleteAccountDataConfirmSheet(
                            onConfirm = {
                                if (!isDeletingAccountData) {
                                    coroutineScope.launch {
                                        isDeletingAccountData = true
                                        runCatching {
                                            accountRepository.deleteSellSnapAccountData()
                                        }.onSuccess {
                                            // Drop cached copies of the user's listing photos too.
                                            SingletonImageLoader.get(platformContext).apply {
                                                memoryCache?.clear()
                                                diskCache?.clear()
                                            }
                                            navVm.replaceWith(AppDestination.SellerLanding)
                                        }.onFailure { error ->
                                            error.printStackTrace()
                                        }
                                        isDeletingAccountData = false
                                    }
                                }
                            },
                            onDismiss = {
                                if (!isDeletingAccountData) {
                                    navVm.popDestination()
                                }
                            },
                            isDeleting = isDeletingAccountData,
                        )
                    }

                    // SIR-83: siblings of DeleteAccountDataConfirm above. Kept at this top level
                    // rather than inside AdRootScreen's own nested NavDisplay because a sibling
                    // task owns features/seller/ad/** for this release (Publish/Preview + My Ads).
                    entry<AppDestination.AddOlxAccountConfirm>(
                        metadata = BottomSheetSceneStrategy.bottomSheet(),
                    ) {
                        AddOlxAccountConfirmSheet(
                            siteLabel = _currentOlxCountry.domain,
                            onContinue = {
                                navVm.popDestination()
                                startAddOrReconnectAuthorization()
                            },
                            onDismiss = { navVm.popDestination() },
                        )
                    }

                    entry<AppDestination.OlxAccountAuthFailed>(
                        metadata = BottomSheetSceneStrategy.bottomSheet(),
                    ) {
                        val countryCode = _currentOlxCountry.code
                        OlxAccountAuthFailedSheet(
                            remainingCooldownSeconds = { accountRepository.remainingCooldownSeconds(null, countryCode) },
                            consecutiveFailures = { accountRepository.consecutiveAuthFailures(null, countryCode) },
                            onRetry = {
                                navVm.popDestination()
                                startAddOrReconnectAuthorization()
                            },
                            onOpenOlxRecovery = {
                                // Best-effort guess at OLX's own sign-in page, following the same
                                // authBaseUrl/apiBaseUrl/logoutUrl convention as OlxCountry -
                                // UNVERIFIED against a live session; see the SIR-83 follow-ups doc.
                                uriHandler.openUri("https://www.${_currentOlxCountry.domain}/login/")
                            },
                            onDismiss = { navVm.popDestination() },
                        )
                    }

                    entry<AppDestination.DisconnectOlxAccountConfirm>(
                        metadata = BottomSheetSceneStrategy.bottomSheet(),
                    ) { destination ->
                        val accountsRecord by accountRepository.accountsRecordFlow.collectAsStateWithLifecycle()
                        val account = accountsRecord.accounts.find { it.localIndex == destination.localIndex }
                        DisconnectOlxAccountConfirmSheet(
                            accountName = account?.profile?.name,
                            isDisconnecting = isDisconnectingAccount,
                            onConfirm = {
                                if (!isDisconnectingAccount) {
                                    coroutineScope.launch {
                                        isDisconnectingAccount = true
                                        runCatching {
                                            accountRepository.disconnectAccount(
                                                countryCode = account?.countryCode ?: _currentOlxCountry.code,
                                                localIndex = destination.localIndex,
                                            )
                                        }
                                        isDisconnectingAccount = false
                                        navVm.popDestination()
                                    }
                                }
                            },
                            onOpenOlxSettings = {
                                // Best-effort guess: OLX documents no "connected applications"
                                // settings page for partner-API integrations, so this points at
                                // the general account settings page already used elsewhere in
                                // this codebase (AdRootScreen's publish-success fallback link).
                                // UNVERIFIED; see the SIR-83 follow-ups doc.
                                uriHandler.openUri("https://www.${_currentOlxCountry.domain}/myaccount/")
                            },
                            onDismiss = {
                                if (!isDisconnectingAccount) {
                                    navVm.popDestination()
                                }
                            },
                        )
                    }
                },
            )
        }
    }
}
