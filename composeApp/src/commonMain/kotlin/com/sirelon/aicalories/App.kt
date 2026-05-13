package com.sirelon.sellsnap

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import com.mohamedrejeb.calf.picker.coil.KmpFileFetcher
import com.sirelon.sellsnap.designsystem.AppTheme
import com.sirelon.sellsnap.designsystem.screens.LoadingOverlay
import com.sirelon.sellsnap.di.appModule
import com.sirelon.sellsnap.di.networkModule
import com.sirelon.sellsnap.features.seller.ad.AdRootScreen
import com.sirelon.sellsnap.features.seller.auth.presentation.SellerLandingScreenRoute
import com.sirelon.sellsnap.features.seller.onboarding.OnboardingScreen
import com.sirelon.sellsnap.navigation.AppDestination
import com.sirelon.sellsnap.navigation.appNavigationSavedStateConfiguration
import com.sirelon.sellsnap.startup.AppNavigationViewModel
import org.koin.compose.KoinApplication
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
        AppTheme {
            val navVm: AppNavigationViewModel = koinViewModel()
            val backStackList by navVm.backStack.collectAsStateWithLifecycle()

            val navBackStack = rememberNavBackStack(
                appNavigationSavedStateConfiguration,
                AppDestination.Splash,
            )
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
                entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator<NavKey>()),
                entryProvider = entryProvider<NavKey> {

                    entry<AppDestination.Splash> {
                        LoadingOverlay(isLoading = true) {}
                    }

                    entry<AppDestination.SellerOnboarding> {
                        OnboardingScreen {
                            navVm.replaceWith(AppDestination.SellerLanding)
                        }
                    }

                    entry<AppDestination.SellerLanding> {
                        SellerLandingScreenRoute(
                            openHome = { navVm.replaceWith(AppDestination.Seller) },
                        )
                    }

                    entry<AppDestination.Seller> {
                        AdRootScreen(
                            onConnectOlxClick = navVm::exitGuestModeToLanding,
                            onLogout = { navVm.replaceWith(AppDestination.SellerLanding) },
                            popToAdRoot = navVm::popToAdRoot,
                        )
                    }
                },
            )
        }
    }
}
