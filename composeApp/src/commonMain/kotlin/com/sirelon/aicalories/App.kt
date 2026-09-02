package com.sirelon.sellsnap

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.get
import androidx.navigation3.runtime.metadata
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.compose.setSingletonImageLoaderFactory
import com.mohamedrejeb.calf.picker.coil.KmpFileFetcher
import com.sirelon.sellsnap.analytics.Analytics
import com.sirelon.sellsnap.analytics.AnalyticsEvents
import com.sirelon.sellsnap.analytics.TrackScreenViews
import com.sirelon.sellsnap.designsystem.AppTheme
import com.sirelon.sellsnap.designsystem.ObserveAsEvents
import com.sirelon.sellsnap.designsystem.screens.ImagesPreview
import com.sirelon.sellsnap.designsystem.screens.LoadingOverlay
import com.sirelon.sellsnap.di.appModule
import com.sirelon.sellsnap.di.networkModule
import com.sirelon.sellsnap.features.consent.ConsentScreen
import com.sirelon.sellsnap.features.seller.ad.generate_ad.GenerateAdScreen
import com.sirelon.sellsnap.features.seller.ad.preview_ad.PreviewAdContentRoute
import com.sirelon.sellsnap.features.seller.ad.preview_ad.PreviewAdContract
import com.sirelon.sellsnap.features.seller.ad.preview_ad.PreviewAdContract.PreviewAdEvent
import com.sirelon.sellsnap.features.seller.ad.preview_ad.PreviewAdViewModel
import com.sirelon.sellsnap.features.seller.ad.preview_ad.ui.PreviewBackInfoSheet
import com.sirelon.sellsnap.features.seller.ad.preview_ad.ui.PublishAccountPickerSheet
import com.sirelon.sellsnap.features.seller.ad.preview_ad.ui.PublishConfirmSheet
import com.sirelon.sellsnap.features.seller.ad.preview_ad.ui.PublishingScreen
import com.sirelon.sellsnap.features.seller.ad.publish_success.PublishSuccessScreen
import com.sirelon.sellsnap.features.seller.ad.screenshotMode
import com.sirelon.sellsnap.features.seller.auth.data._currentOlxCountry
import com.sirelon.sellsnap.features.seller.auth.presentation.OlxCountryPickerScreenRoute
import com.sirelon.sellsnap.features.seller.auth.presentation.SellerLandingScreenRoute
import com.sirelon.sellsnap.features.seller.auth.presentation.rememberOlxAuthLauncher
import com.sirelon.sellsnap.features.seller.categories.domain.OlxCategory
import com.sirelon.sellsnap.features.seller.categories.presentation.CategoryPickerSheet
import com.sirelon.sellsnap.features.seller.my_ads.ui.MyAdvertsScreenRoute
import com.sirelon.sellsnap.features.seller.onboarding.OnboardingScreen
import com.sirelon.sellsnap.features.seller.profile.data.SellerAccountRepository
import com.sirelon.sellsnap.features.seller.profile.ui.AddOlxAccountConfirmSheet
import com.sirelon.sellsnap.features.seller.profile.ui.DeleteAccountDataConfirmSheet
import com.sirelon.sellsnap.features.seller.profile.ui.DisconnectOlxAccountConfirmSheet
import com.sirelon.sellsnap.features.seller.profile.ui.OlxAccountAuthFailedSheet
import com.sirelon.sellsnap.features.seller.profile.ui.ProfileScreenRoute
import com.sirelon.sellsnap.features.seller.settings.ui.SettingsScreenRoute
import com.sirelon.sellsnap.features.whatsnew.presentation.WhatsNewViewModel
import com.sirelon.sellsnap.features.whatsnew.ui.AllReleasesScreenRoute
import com.sirelon.sellsnap.features.whatsnew.ui.WhatsNewPromptSheet
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.guest_connect_olx_cta
import com.sirelon.sellsnap.generated.resources.ic_camera
import com.sirelon.sellsnap.generated.resources.ic_tag
import com.sirelon.sellsnap.generated.resources.ic_user
import com.sirelon.sellsnap.generated.resources.nav_my_ads
import com.sirelon.sellsnap.generated.resources.new_listing
import com.sirelon.sellsnap.generated.resources.profile_screen_title
import com.sirelon.sellsnap.generated.resources.settings_screen_title
import com.sirelon.sellsnap.legal.LegalLinks
import com.sirelon.sellsnap.navigation.AppKey
import com.sirelon.sellsnap.navigation.BottomSheetSceneStrategy
import com.sirelon.sellsnap.navigation.LocalSharedViewModelStoreOwner
import com.sirelon.sellsnap.navigation.SharedViewModelStoreNavEntryDecorator
import com.sirelon.sellsnap.navigation.rememberSharedViewModelStoreNavEntryDecorator
import com.sirelon.sellsnap.platform.openUrl
import com.sirelon.sellsnap.startup.AppNavigationViewModel
import com.sirelon.sellsnap.startup.AppThemeRepository
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
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
            val analytics: Analytics = koinInject()
            val coroutineScope = rememberCoroutineScope()
            var isDeletingAccountData by remember { mutableStateOf(false) }
            var isDisconnectingAccount by remember { mutableStateOf(false) }
            val uriHandler = LocalUriHandler.current
            // SIR-83: add-account/reconnect must force a fresh OLX login (D5). This sheet-level
            // launcher is independent of the one the Profile entry threads into ProfileScreenRoute
            // for its own inline Reconnect action - both are stateless wrappers around the same
            // platform mechanism, so having two instances is harmless (see OlxExternalAuthLauncher).
            val addAccountAuthLauncher = rememberOlxAuthLauncher(forceReauth = true)
            fun startAddOrReconnectAuthorization() {
                coroutineScope.launch {
                    runCatching { accountRepository.createAuthorizationRequest(forceReauth = true) }
                        .onSuccess { request -> addAccountAuthLauncher(request.url) }
                }
            }

            // Seller-flow state, scoped for as long as the App composable is alive (it's never
            // itself popped) - matches what these already effectively were before the NavDisplay
            // merge, since the entries that read them never had per-entry ViewModelStore scoping
            // either.
            val whatsNewViewModel: WhatsNewViewModel = koinViewModel()
            var pendingCategory by remember { mutableStateOf<OlxCategory?>(null) }
            var isGeneratingAd by remember { mutableStateOf(false) }
            var isPreviewPublishing by remember { mutableStateOf(false) }
            val authLauncher = rememberOlxAuthLauncher()
            // SIR-83 (D5): a second launcher that forces a fresh OLX login, used only for
            // add-account and reconnect so the seller isn't silently bounced back into an account
            // they already have.
            val authLauncherForceReauth = rememberOlxAuthLauncher(forceReauth = true)
            val connectOlxReason = stringResource(Res.string.guest_connect_olx_cta)

            fun leaveSellerFlowToLanding() {
                pendingCategory = null
                navVm.replaceWith(AppKey.SellerLanding)
            }

            TrackScreenViews(navVm.backStack.lastOrNull())

            // Bottom sheets/dialogs can sit on top of a tab without being one themselves
            // (WhatsNewPrompt, DeleteAccountDataConfirm, ...) - search back past those specific
            // overlay entries only for the tab underneath, so the bar stays visible under a sheet
            // but still correctly disappears under a genuinely pushed full-screen destination
            // (PreviewAd, ImagesPreview, AllReleases, SellerPublishSuccess, ...), where tapping a
            // tab would silently discard whatever's in progress there without its own confirm step.
            val selectedRootTab = navVm.backStack.lastOrNull { !it.isOverlayEntry() }.toSellerRootTab()

            // Keyed on whether we're in the seller flow at all (not on the top entry, and not on
            // the specific tab) so a sheet like WhatsNewPrompt pushing/popping on top - which
            // doesn't change selectedRootTab - never re-fires this and re-adds a prompt the seller
            // just dismissed before its async "seen" write has landed.
            val isInSellerFlow = selectedRootTab != null
            LaunchedEffect(isInSellerFlow) {
                if (isInSellerFlow && whatsNewViewModel.shouldShowDialog()) {
                    navVm.backStack.add(AppKey.WhatsNewPrompt)
                }
            }
            fun switchRootTab(tab: SellerRootTab) {
                if (selectedRootTab == tab) return
                navVm.backStack.apply {
                    clear()
                    add(tab.destination)
                }
            }

            val showNavigation = !isGeneratingAd && selectedRootTab != null
            val layoutType = if (showNavigation) {
                NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())
            } else {
                NavigationSuiteType.None
            }

            val sceneStrategies = remember {
                listOf(
                    BottomSheetSceneStrategy<AppKey>(),
                    DialogSceneStrategy<AppKey>(),
                    SinglePaneSceneStrategy<AppKey>(),
                )
            }

            NavigationSuiteScaffold(
                modifier = Modifier.fillMaxSize().testTag("home_root"),
                layoutType = layoutType,
                navigationSuiteItems = {
                    SellerRootTab.entries.forEach { tab ->
                        item(
                            selected = selectedRootTab == tab,
                            onClick = { switchRootTab(tab) },
                            icon = {
                                val icon = tab.icon
                                if (icon != null) {
                                    Icon(
                                        painter = painterResource(icon),
                                        contentDescription = null,
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = null,
                                    )
                                }
                            },
                            label = {
                                Text(
                                    text = stringResource(tab.label),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            alwaysShowLabel = false,
                        )
                    }
                },
            ) {
                NavDisplay(
                    modifier = Modifier.fillMaxSize(),
                    backStack = navVm.backStack,
                    onBack = {
                        val top = navVm.backStack.lastOrNull()
                        when {
                            isDeletingAccountData -> Unit
                            isPreviewPublishing -> Unit
                            top is AppKey.PreviewAd -> {
                                if (screenshotMode) {
                                    navVm.popDestination()
                                } else {
                                    navVm.backStack.add(AppKey.PreviewBackInfo)
                                }
                            }

                            else -> navVm.popDestination()
                        }
                    },
                    sceneStrategies = sceneStrategies,
                    transitionSpec = {
                        if (isTopLevelTransition(initialState, targetState)) {
                            fadeIn() togetherWith fadeOut()
                        } else {
                            slideInHorizontally(initialOffsetX = { it }) togetherWith
                                slideOutHorizontally(targetOffsetX = { -it })
                        }
                    },
                    popTransitionSpec = {
                        if (isTopLevelTransition(initialState, targetState)) {
                            fadeIn() togetherWith fadeOut()
                        } else {
                            slideInHorizontally(initialOffsetX = { -it }) togetherWith
                                slideOutHorizontally(targetOffsetX = { it })
                        }
                    },
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator<AppKey>(),
                        rememberSharedViewModelStoreNavEntryDecorator(),
                    ),
                    entryProvider = entryProvider<AppKey> {

                        entry<AppKey.Splash> {
                            LoadingOverlay(isLoading = true) {}
                        }

                        entry<AppKey.SellerOnboarding> {
                            OnboardingScreen {
                                analytics.logEvent(AnalyticsEvents.ONBOARDING_COMPLETED)
                                navVm.onOnboardingCompleted()
                            }
                        }

                        entry<AppKey.ConsentPrompt> {
                            val consentUriHandler = LocalUriHandler.current
                            ConsentScreen(
                                onAllow = { navVm.onConsentAllow() },
                                onDecline = { navVm.onConsentDecline() },
                                onOpenPrivacy = { consentUriHandler.openUri(LegalLinks.PRIVACY_URL) },
                                onOpenTerms = { consentUriHandler.openUri(LegalLinks.TERMS_URL) },
                            )
                        }

                        entry<AppKey.SellerLanding> {
                            SellerLandingScreenRoute(
                                openHome = { navVm.replaceWith(AppKey.GenerateAd) },
                                openCountryPicker = { navVm.navigateTo(AppKey.OlxCountryPicker) },
                            )
                        }

                        entry<AppKey.OlxCountryPicker> {
                            OlxCountryPickerScreenRoute(
                                onBack = { navVm.popDestination() },
                                openHome = { navVm.replaceWith(AppKey.GenerateAd) },
                            )
                        }

                        entry<AppKey.GenerateAd>(metadata = topLevelMetadata) {
                            GenerateAdScreen(
                                openAdPreview = { navVm.backStack.add(AppKey.PreviewAd(it)) },
                                onLoadingChanged = { isGeneratingAd = it },
                            )
                        }

                        entry<AppKey.MyAdverts>(metadata = topLevelMetadata) {
                            MyAdvertsScreenRoute(
                                onConnectOlxClick = {
                                    pendingCategory = null
                                    navVm.exitGuestModeToLanding()
                                },
                                onCreateListingClick = { switchRootTab(SellerRootTab.GenerateAd) },
                            )
                        }

                        entry<AppKey.PreviewAd>(
                            clazzContentKey = { PREVIEW_AD_FLOW_KEY },
                        ) { key ->
                            val previewViewModel: PreviewAdViewModel =
                                koinViewModel { parametersOf(key.advertisement) }
                            val previewState by previewViewModel.state.collectAsStateWithLifecycle()
                            val snackbarHostState = remember { SnackbarHostState() }

                            LaunchedEffect(previewState.isPublishing) {
                                isPreviewPublishing = previewState.isPublishing
                            }

                            ObserveAsEvents(previewViewModel.effects) { effect ->
                                when (effect) {
                                    is PreviewAdContract.PreviewAdEffect.ShowMessage ->
                                        snackbarHostState.showSnackbar(effect.message)

                                    PreviewAdContract.PreviewAdEffect.GoToGategoryPicker ->
                                        navVm.backStack.add(AppKey.SelectCategory)

                                    is PreviewAdContract.PreviewAdEffect.PublishSuccess -> {
                                        navVm.backStack.apply {
                                            clear()
                                            add(AppKey.GenerateAd)
                                            add(AppKey.SellerPublishSuccess(effect.data))
                                        }
                                    }

                                    is PreviewAdContract.PreviewAdEffect.PublishFailure ->
                                        snackbarHostState.showSnackbar(effect.message)

                                    is PreviewAdContract.PreviewAdEffect.NavigateToProfile ->
                                        navVm.backStack.add(AppKey.Profile(effect.reason))

                                    is PreviewAdContract.PreviewAdEffect.PublishAccountMismatch ->
                                        snackbarHostState.showSnackbar(effect.message)

                                    is PreviewAdContract.PreviewAdEffect.PublishNeedsReconnect -> {
                                        // Never auto-launches OAuth: the action only takes the
                                        // seller to Profile, where reconnecting is a deliberate,
                                        // separate tap.
                                        val result = snackbarHostState.showSnackbar(
                                            message = effect.message,
                                            actionLabel = effect.actionLabel,
                                            duration = SnackbarDuration.Long,
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            navVm.backStack.add(AppKey.Profile(effect.message))
                                        }
                                    }
                                }
                            }

                            Box(modifier = Modifier.fillMaxSize()) {
                                PreviewAdContentRoute(
                                    viewModel = previewViewModel,
                                    snackbarHostState = snackbarHostState,
                                    onBack = {
                                        if (screenshotMode) {
                                            navVm.popDestination()
                                        } else {
                                            navVm.backStack.add(AppKey.PreviewBackInfo)
                                        }
                                    },
                                    onChangeCategoryClick = { navVm.backStack.add(AppKey.SelectCategory) },
                                    pendingCategory = pendingCategory,
                                    onCategoryConsumed = { pendingCategory = null },
                                    onConnectOlxClick = {
                                        navVm.backStack.add(AppKey.Profile(reason = connectOlxReason))
                                    },
                                    onPublishConfirmationRequested = {
                                        if (navVm.backStack.lastOrNull() !is AppKey.PreviewPublishConfirm) {
                                            navVm.backStack.add(AppKey.PreviewPublishConfirm)
                                        }
                                    },
                                    onTargetAccountRowClick = {
                                        if (navVm.backStack.lastOrNull() !is AppKey.PreviewAccountPicker) {
                                            navVm.backStack.add(AppKey.PreviewAccountPicker)
                                        }
                                    },
                                    showImagesPreview = { images, initialPage ->
                                        navVm.backStack.add(AppKey.ImagesPreview(images, initialPage))
                                    },
                                )
                                if (previewState.isPublishing) {
                                    PublishingScreen()
                                }
                            }
                        }

                        entry<AppKey.PreviewPublishConfirm>(
                            metadata = BottomSheetSceneStrategy.bottomSheet() +
                                SharedViewModelStoreNavEntryDecorator.parent(PREVIEW_AD_FLOW_KEY),
                        ) {
                            val sharedViewModel = koinViewModel<PreviewAdViewModel>(
                                viewModelStoreOwner = LocalSharedViewModelStoreOwner.current,
                            )
                            val state by sharedViewModel.state.collectAsStateWithLifecycle()

                            PublishConfirmSheet(
                                imageUrls = state.images,
                                title = sharedViewModel.titleState.text.toString(),
                                categoryLabel = state.categoryLabel,
                                priceFormatted = state.currency.format(state.price),
                                onConfirm = {
                                    // Pop first: PublishingScreen() renders inside the PreviewAd
                                    // entry underneath, so leaving this sheet up would hide the
                                    // loading state (and any failure snackbar/Reconnect action)
                                    // behind the sheet's scrim - see PreviewAdEvent.Publish's lack
                                    // of a re-entry guard for why a stuck sheet also risks a
                                    // double-publish.
                                    if (navVm.backStack.lastOrNull() is AppKey.PreviewPublishConfirm) {
                                        navVm.popDestination()
                                    }
                                    sharedViewModel.onEvent(PreviewAdEvent.Publish)
                                },
                                onDismiss = {
                                    if (navVm.backStack.lastOrNull() is AppKey.PreviewPublishConfirm) {
                                        navVm.popDestination()
                                    }
                                },
                            )
                        }

                        entry<AppKey.PreviewAccountPicker>(
                            metadata = BottomSheetSceneStrategy.bottomSheet() +
                                SharedViewModelStoreNavEntryDecorator.parent(PREVIEW_AD_FLOW_KEY),
                        ) {
                            val sharedViewModel = koinViewModel<PreviewAdViewModel>(
                                viewModelStoreOwner = LocalSharedViewModelStoreOwner.current,
                            )
                            val state by sharedViewModel.state.collectAsStateWithLifecycle()

                            PublishAccountPickerSheet(
                                items = state.accountPickerItems,
                                onAccountSelected = { localIndex ->
                                    sharedViewModel.onEvent(PreviewAdEvent.SwitchAccountRequested(localIndex))
                                    if (navVm.backStack.lastOrNull() is AppKey.PreviewAccountPicker) {
                                        navVm.popDestination()
                                    }
                                },
                                onDismiss = {
                                    if (navVm.backStack.lastOrNull() is AppKey.PreviewAccountPicker) {
                                        navVm.popDestination()
                                    }
                                },
                            )
                        }

                        entry<AppKey.PreviewBackInfo>(
                            metadata = BottomSheetSceneStrategy.bottomSheet() +
                                SharedViewModelStoreNavEntryDecorator.parent(PREVIEW_AD_FLOW_KEY),
                        ) {
                            PreviewBackInfoSheet(
                                onStay = {
                                    analytics.logEvent(
                                        AnalyticsEvents.AD_DRAFT_EXIT_CHOICE,
                                        mapOf("choice" to "stay"),
                                    )
                                    if (navVm.backStack.lastOrNull() is AppKey.PreviewBackInfo) {
                                        navVm.popDestination()
                                    }
                                },
                                onLeave = {
                                    analytics.logEvent(
                                        AnalyticsEvents.AD_DRAFT_EXIT_CHOICE,
                                        mapOf("choice" to "leave"),
                                    )
                                    // Pop the whole preview-ad flow, back to GenerateAd.
                                    navVm.backStack.removeAll {
                                        it is AppKey.PreviewBackInfo || it is AppKey.PreviewAd
                                    }
                                },
                            )
                        }

                        entry<AppKey.SelectCategory>(
                            metadata = BottomSheetSceneStrategy.bottomSheet(),
                        ) {
                            CategoryPickerSheet(
                                onCategorySelected = { category ->
                                    navVm.popDestination()
                                    pendingCategory = category
                                },
                            )
                        }

                        entry<AppKey.Profile>(metadata = topLevelMetadata) { destination ->
                            ProfileScreenRoute(
                                onBack = if (destination.reason == null) {
                                    null
                                } else {
                                    { navVm.popDestination() }
                                },
                                onOpenOlxAuth = authLauncher,
                                onOpenOlxAuthForceReauth = authLauncherForceReauth,
                                onLogout = { leaveSellerFlowToLanding() },
                                onAddAccountRequested = {
                                    // U3: never let a tap start a fresh attempt while an earlier
                                    // failure's cooldown is still in force - show that state
                                    // instead.
                                    val countryCode = _currentOlxCountry.code
                                    if (accountRepository.remainingCooldownSeconds(null, countryCode) > 0) {
                                        navVm.navigateTo(AppKey.OlxAccountAuthFailed)
                                    } else {
                                        navVm.navigateTo(AppKey.AddOlxAccountConfirm)
                                    }
                                },
                                onAddAccountFailed = {
                                    navVm.navigateTo(AppKey.OlxAccountAuthFailed)
                                },
                                onDisconnectRequested = { localIndex ->
                                    navVm.navigateTo(AppKey.DisconnectOlxAccountConfirm(localIndex))
                                },
                                reason = destination.reason,
                            )
                        }

                        entry<AppKey.Settings>(metadata = topLevelMetadata) {
                            SettingsScreenRoute(
                                onDeleteAccountDataRequested = {
                                    navVm.navigateTo(AppKey.DeleteAccountDataConfirm)
                                },
                                onOpenWhatsNew = { navVm.backStack.add(AppKey.AllReleases) },
                            )
                        }

                        entry<AppKey.WhatsNewPrompt>(
                            metadata = BottomSheetSceneStrategy.bottomSheet(),
                        ) {
                            WhatsNewPromptSheet(
                                viewModel = whatsNewViewModel,
                                onDismiss = { navVm.popDestination() },
                                onViewAll = {
                                    navVm.popDestination()
                                    navVm.backStack.add(AppKey.AllReleases)
                                },
                            )
                        }

                        entry<AppKey.AllReleases> {
                            AllReleasesScreenRoute(
                                viewModel = whatsNewViewModel,
                                onBack = { navVm.popDestination() },
                            )
                        }

                        entry<AppKey.ImagesPreview> {
                            ImagesPreview(
                                images = it.images,
                                initialPage = it.initialPage,
                                onDismiss = { navVm.popDestination() },
                            )
                        }

                        entry<AppKey.SellerPublishSuccess> { destination ->
                            PublishSuccessScreen(
                                data = destination.data,
                                onViewOnOlx = {
                                    val url = destination.data.url
                                        .ifBlank { "https://www.${_currentOlxCountry.domain}/myaccount/" }
                                    openUrl(url)
                                },
                                onCreateAnother = { navVm.popToAdRoot() },
                            )
                        }

                        entry<AppKey.DeleteAccountDataConfirm>(
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
                                                leaveSellerFlowToLanding()
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

                        // SIR-83: siblings of DeleteAccountDataConfirm above. Kept at this top
                        // level rather than folded into a feature-specific module because a
                        // sibling task owns features/seller/ad/** for this release (Publish/
                        // Preview + My Ads).
                        entry<AppKey.AddOlxAccountConfirm>(
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

                        entry<AppKey.OlxAccountAuthFailed>(
                            metadata = BottomSheetSceneStrategy.bottomSheet(),
                        ) {
                            val countryCode = _currentOlxCountry.code
                            OlxAccountAuthFailedSheet(
                                remainingCooldownSeconds = {
                                    accountRepository.remainingCooldownSeconds(null, countryCode)
                                },
                                consecutiveFailures = {
                                    accountRepository.consecutiveAuthFailures(null, countryCode)
                                },
                                onRetry = {
                                    navVm.popDestination()
                                    startAddOrReconnectAuthorization()
                                },
                                onOpenOlxRecovery = {
                                    // Best-effort guess at OLX's own sign-in page, following the
                                    // same authBaseUrl/apiBaseUrl/logoutUrl convention as
                                    // OlxCountry - UNVERIFIED against a live session; see the
                                    // SIR-83 follow-ups doc.
                                    uriHandler.openUri("https://www.${_currentOlxCountry.domain}/login/")
                                },
                                onDismiss = { navVm.popDestination() },
                            )
                        }

                        entry<AppKey.DisconnectOlxAccountConfirm>(
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
                                    // settings page for partner-API integrations, so this points
                                    // at the general account settings page already used elsewhere
                                    // in this codebase (the publish-success fallback link).
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
}

private const val PREVIEW_AD_FLOW_KEY = "PreviewAdFlow"

private object TopLevelTransitionKey : NavMetadataKey<Boolean>
private val topLevelMetadata = metadata { put(TopLevelTransitionKey, true) }

private fun isTopLevelTransition(
    initial: Scene<AppKey>,
    target: Scene<AppKey>,
): Boolean = initial.metadata[TopLevelTransitionKey] == true &&
    target.metadata[TopLevelTransitionKey] == true

private enum class SellerRootTab(
    val destination: AppKey,
    val icon: DrawableResource?,
    val label: StringResource,
) {
    GenerateAd(AppKey.GenerateAd, Res.drawable.ic_camera, Res.string.new_listing),
    MyAdverts(AppKey.MyAdverts, Res.drawable.ic_tag, Res.string.nav_my_ads),
    Profile(AppKey.Profile(), Res.drawable.ic_user, Res.string.profile_screen_title),
    Settings(AppKey.Settings, icon = null, Res.string.settings_screen_title),
}

private fun AppKey?.toSellerRootTab(): SellerRootTab? = when (this) {
    AppKey.GenerateAd -> SellerRootTab.GenerateAd
    AppKey.MyAdverts -> SellerRootTab.MyAdverts
    is AppKey.Profile -> if (reason == null) SellerRootTab.Profile else null
    AppKey.Settings -> SellerRootTab.Settings
    else -> null
}

// Bottom-sheet/dialog entries only - i.e. every entry registered with
// BottomSheetSceneStrategy.bottomSheet() metadata. Deliberately excludes full-screen pushed
// destinations like PreviewAd/ImagesPreview/AllReleases/SellerPublishSuccess, which should hide
// the tab bar rather than let it show through to whatever tab is underneath.
private fun AppKey.isOverlayEntry(): Boolean = when (this) {
    AppKey.DeleteAccountDataConfirm,
    AppKey.AddOlxAccountConfirm,
    AppKey.OlxAccountAuthFailed,
    is AppKey.DisconnectOlxAccountConfirm,
    AppKey.SelectCategory,
    AppKey.WhatsNewPrompt,
    AppKey.PreviewPublishConfirm,
    AppKey.PreviewAccountPicker,
    AppKey.PreviewBackInfo,
    -> true

    else -> false
}
