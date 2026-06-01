package com.sirelon.sellsnap.features.seller.ad

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay
import com.sirelon.sellsnap.designsystem.screens.ImagesPreview
import com.sirelon.sellsnap.features.seller.ad.generate_ad.GenerateAdScreen
import com.sirelon.sellsnap.features.seller.ad.preview_ad.PreviewAdScreen
import com.sirelon.sellsnap.features.seller.ad.publish_success.PublishSuccessScreen
import com.sirelon.sellsnap.features.seller.auth.data.OlxAuthCallbackBridge
import com.sirelon.sellsnap.features.seller.auth.presentation.OlxAuthDialogScreen
import com.sirelon.sellsnap.features.seller.categories.domain.OlxCategory
import com.sirelon.sellsnap.features.seller.categories.presentation.CategoryPickerSheet
import com.sirelon.sellsnap.features.seller.my_ads.ui.MyAdvertsScreenRoute
import com.sirelon.sellsnap.navigation.BottomSheetSceneStrategy
import com.sirelon.sellsnap.features.seller.profile.ui.ProfileScreenRoute
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.ic_camera
import com.sirelon.sellsnap.generated.resources.ic_tag
import com.sirelon.sellsnap.generated.resources.ic_user
import com.sirelon.sellsnap.generated.resources.nav_my_ads
import com.sirelon.sellsnap.generated.resources.new_listing
import com.sirelon.sellsnap.generated.resources.profile_screen_title
import com.sirelon.sellsnap.generated.resources.guest_connect_olx_cta
import com.sirelon.sellsnap.platform.openUrl
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource


@Composable
fun AdRootScreen(
    onConnectOlxClick: () -> Unit,
    onLogout: () -> Unit,
    popToAdRoot: () -> Unit,
) {
    val navBackStack = rememberNavBackStack(
        adNavigationSavedStateConfiguration,
        AdDestination.GenerateAd,
    )
    val connectOlxReason = stringResource(Res.string.guest_connect_olx_cta)

    var pendingCategory by remember { mutableStateOf<OlxCategory?>(null) }
    var isGeneratingAd by remember { mutableStateOf(false) }
    val sceneStrategies = remember {
        listOf(
            BottomSheetSceneStrategy<NavKey>(),
            DialogSceneStrategy<NavKey>(),
            SinglePaneSceneStrategy<NavKey>(),
        )
    }

    val selectedRootTab = navBackStack.lastOrNull().toSellerRootTab()
    fun switchRootTab(tab: SellerRootTab) {
        if (selectedRootTab == tab) return
        navBackStack.clear()
        navBackStack.add(tab.destination)
    }

    val showNavigation = !isGeneratingAd && selectedRootTab != null
    val layoutType = if (showNavigation) {
        NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())
    } else {
        NavigationSuiteType.None
    }

    NavigationSuiteScaffold(
        modifier = Modifier.fillMaxSize(),
        layoutType = layoutType,
        navigationSuiteItems = {
            SellerRootTab.entries.forEach { tab ->
                item(
                    selected = selectedRootTab == tab,
                    onClick = { switchRootTab(tab) },
                    icon = {
                        Icon(
                            painter = painterResource(tab.icon),
                            contentDescription = null,
                        )
                    },
                    label = { Text(stringResource(tab.label)) },
                )
            }
        },
    ) {
        NavDisplay(
            modifier = Modifier.fillMaxSize(),
            backStack = navBackStack,
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
            entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator<NavKey>()),
            entryProvider = entryProvider<NavKey> {
                entry<AdDestination.GenerateAd>(
                    metadata = topLevelMetadata,
                ) {
                    GenerateAdScreen(
                        openAdPreview = { navBackStack.add(AdDestination.PreviewAd(it)) },
                        onLoadingChanged = { isGeneratingAd = it },
                    )
                }

                entry<AdDestination.MyAdverts>(
                    metadata = topLevelMetadata,
                ) {
                    MyAdvertsScreenRoute(
                        onConnectOlxClick = onConnectOlxClick,
                        onCreateListingClick = { switchRootTab(SellerRootTab.GenerateAd) },
                    )
                }

                entry<AdDestination.PreviewAd> { destination ->
                    PreviewAdScreen(
                        advertisement = destination.advertisement,
                        onBackToGenerate = { navBackStack.removeAt(navBackStack.lastIndex) },
                        onChangeCategoryClick = { navBackStack.add(AdDestination.SelectCategory) },
                        onPublishSuccess = {
                            navBackStack.clear()
                            navBackStack.add(AdDestination.GenerateAd)
                            navBackStack.add(AdDestination.SellerPublishSuccess(it))
                        },
                        pendingCategory = pendingCategory,
                        onCategoryConsumed = { pendingCategory = null },
                        onConnectOlxClick = {
                            navBackStack.add(
                                AdDestination.Profile(
                                    reason = connectOlxReason,
                                )
                            )
                        },
                        showImagesPreview = { images, initialPage ->
                            navBackStack.add(AdDestination.ImagesPreview(images, initialPage))
                        },
                        onNavigateToProfile = { reason ->
                            navBackStack.add(AdDestination.Profile(reason))
                        },
                    )
                }

                entry<AdDestination.SelectCategory>(
                    metadata = BottomSheetSceneStrategy.bottomSheet(),
                ) {
                    CategoryPickerSheet(
                        onCategorySelected = { category ->
                            navBackStack.removeAt(navBackStack.lastIndex)
                            pendingCategory = category
                        },
                    )
                }

                entry<AdDestination.Profile>(
                    metadata = topLevelMetadata,
                ) { destination ->
                    ProfileScreenRoute(
                        onBack = if (destination.reason == null) {
                            null
                        } else {
                            { navBackStack.removeAt(navBackStack.lastIndex) }
                        },
                        onOpenOlxAuth = { url -> navBackStack.add(AdDestination.ProfileAuth(url)) },
                        onLogout = onLogout,
                        reason = destination.reason,
                    )
                }

                entry<AdDestination.ProfileAuth>(
                    metadata = DialogSceneStrategy.dialog(
                        DialogProperties(usePlatformDefaultWidth = false),
                    ),
                ) { destination ->
                    OlxAuthDialogScreen(
                        url = destination.url,
                        onDismiss = { navBackStack.removeAt(navBackStack.lastIndex) },
                        onCallbackReceived = { callbackUrl ->
                            navBackStack.removeAt(navBackStack.lastIndex)
                            OlxAuthCallbackBridge.publishCallback(callbackUrl)
                        },
                    )
                }

                entry<AdDestination.ImagesPreview> {
                    ImagesPreview(
                        images = it.images,
                        initialPage = it.initialPage,
                        onDismiss = { navBackStack.removeAt(navBackStack.lastIndex) },
                    )
                }

                entry<AdDestination.SellerPublishSuccess> { destination ->
                    PublishSuccessScreen(
                        data = destination.data,
                        onViewOnOlx = {
                            val url = destination.data.url
                                .ifBlank { "https://www.olx.ua/uk/myaccount/" }
                            openUrl(url)
                        },
                        onCreateAnother = {
                            popToAdRoot()
                            navBackStack.clear()
                            navBackStack.add(AdDestination.GenerateAd)
                        },
                    )
                }
            },
        )
    }
}

private const val TOP_LEVEL_METADATA_KEY = "sellsnap.topLevel"
private val topLevelMetadata: Map<String, Any> = mapOf(TOP_LEVEL_METADATA_KEY to true)

private fun isTopLevelTransition(
    initial: Scene<NavKey>,
    target: Scene<NavKey>,
): Boolean = initial.metadata[TOP_LEVEL_METADATA_KEY] == true &&
        target.metadata[TOP_LEVEL_METADATA_KEY] == true

private enum class SellerRootTab(
    val destination: AdDestination,
    val icon: DrawableResource,
    val label: StringResource,
) {
    GenerateAd(AdDestination.GenerateAd, Res.drawable.ic_camera, Res.string.new_listing),
    MyAdverts(AdDestination.MyAdverts, Res.drawable.ic_tag, Res.string.nav_my_ads),
    Profile(AdDestination.Profile(), Res.drawable.ic_user, Res.string.profile_screen_title),
}

private fun NavKey?.toSellerRootTab(): SellerRootTab? = when (this) {
    AdDestination.GenerateAd -> SellerRootTab.GenerateAd
    AdDestination.MyAdverts -> SellerRootTab.MyAdverts
    is AdDestination.Profile -> if (reason == null) SellerRootTab.Profile else null
    else -> null
}
