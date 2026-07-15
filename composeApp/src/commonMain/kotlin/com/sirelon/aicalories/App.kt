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
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import com.mohamedrejeb.calf.picker.coil.KmpFileFetcher
import com.sirelon.sellsnap.analytics.Analytics
import com.sirelon.sellsnap.analytics.AnalyticsEvents
import com.sirelon.sellsnap.designsystem.AppTheme
import com.sirelon.sellsnap.designsystem.screens.LoadingOverlay
import com.sirelon.sellsnap.di.appModule
import com.sirelon.sellsnap.di.networkModule
import com.sirelon.sellsnap.features.seller.ad.AdRootScreen
import com.sirelon.sellsnap.features.seller.auth.presentation.OlxCountryPickerScreenRoute
import com.sirelon.sellsnap.features.seller.auth.presentation.SellerLandingScreenRoute
import com.sirelon.sellsnap.features.seller.onboarding.OnboardingScreen
import com.sirelon.sellsnap.features.seller.profile.data.SellerAccountRepository
import com.sirelon.sellsnap.features.seller.profile.ui.DeleteAccountDataConfirmSheet
import com.sirelon.sellsnap.navigation.BottomSheetSceneStrategy
import com.sirelon.sellsnap.navigation.AppDestination
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
                            navVm.replaceWith(AppDestination.SellerLanding)
                        }
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
                        )
                    }

                    entry<AppDestination.DeleteAccountDataConfirm>(
                        metadata = BottomSheetSceneStrategy.bottomSheet(),
                    ) {
                        DeleteAccountDataConfirmSheet(
                            onConfirm = {
                                if (!isDeletingAccountData) {
                                    coroutineScope.launch {
                                        isDeletingAccountData = true
                                        runCatching {
                                            accountRepository.deleteSellSnapAccountData()
                                        }.onSuccess {
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
                },
            )
        }
    }
}
