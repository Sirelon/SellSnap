package com.sirelon.sellsnap.features.seller.ad

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay
import com.sirelon.sellsnap.designsystem.AppScaffold
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
import com.sirelon.sellsnap.features.seller.whisper.WhisperDemoScreenRoute
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.ic_camera
import com.sirelon.sellsnap.generated.resources.ic_tag
import com.sirelon.sellsnap.generated.resources.ic_user
import com.sirelon.sellsnap.generated.resources.nav_my_ads
import com.sirelon.sellsnap.generated.resources.new_listing
import com.sirelon.sellsnap.generated.resources.profile_screen_title
import com.sirelon.sellsnap.platform.openUrl
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource


@Composable
fun AdRootScreen(
    onConnectOlxClick: () -> Unit,
    onLogout: () -> Unit,
    popToAdRoot: () -> Unit,
) {

    val navBackStack = remember {
        mutableStateListOf<AdDestination>(AdDestination.GenerateAd)
//        mutableStateListOf<AdDestination>(
//            AdDestination.PreviewAd(
//                Advertisement(
//                    title = "Test",
//                    description = "Test",
//                    suggestedPrice = 100.0f,
//                    images = emptyList(),
//                    minPrice = 20.0f,
//                    maxPrice = 200.0f,
//                    condition = AdCondition.NEW,
//                )
//            )
//        )
    }

    // TODO: WHat is it???
    var pendingCategory by remember { mutableStateOf<OlxCategory?>(null) }
    val sceneStrategies = remember {
        listOf(
            BottomSheetSceneStrategy<AdDestination>(),
            DialogSceneStrategy<AdDestination>(),
            SinglePaneSceneStrategy<AdDestination>(),
        )
    }

    val selectedRootTab = navBackStack.lastOrNull().toSellerRootTab()
    fun switchRootTab(tab: SellerRootTab) {
        if (selectedRootTab == tab) return
        navBackStack.clear()
        navBackStack.add(tab.destination)
    }

    AppScaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0.dp),
        bottomBar = {
            selectedRootTab?.let { selected ->
                SellerBottomNavigation(
                    selectedTab = selected,
                    onTabSelected = { switchRootTab(it) },
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            NavDisplay(
                modifier = Modifier.fillMaxSize(),
                backStack = navBackStack,
                sceneStrategies = sceneStrategies,
                transitionSpec = {
                    slideInHorizontally(initialOffsetX = { it }) togetherWith
                            slideOutHorizontally(targetOffsetX = { -it })
                },
                popTransitionSpec = {
                    slideInHorizontally(initialOffsetX = { -it }) togetherWith
                            slideOutHorizontally(targetOffsetX = { it })
                },
                entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator<AdDestination>()),
                entryProvider = entryProvider {
                    entry<AdDestination.GenerateAd> {
                        GenerateAdScreen(
                            openAdPreview = { navBackStack.add(AdDestination.PreviewAd(it)) },
                            onWhisperClick = { navBackStack.add(AdDestination.WhisperDemo) },
                        )
                    }

                    entry<AdDestination.MyAdverts> {
                        MyAdvertsScreenRoute(
                            onConnectOlxClick = onConnectOlxClick,
                            onCreateListingClick = { switchRootTab(SellerRootTab.GenerateAd) },
                        )
                    }

                    entry<AdDestination.WhisperDemo> {
                        WhisperDemoScreenRoute(
                            onBack = { navBackStack.removeAt(navBackStack.lastIndex) },
                        )
                    }

                    entry<AdDestination.PreviewAd> { destination ->
                        PreviewAdScreen(
                            advertisement = destination.advertisement,
                            onBackToGenerate = { navBackStack.removeAt(navBackStack.lastIndex) },
                            onChangeCategoryClick = { navBackStack.add(AdDestination.SelectCategory) },
                            onPublishSuccess = {
                                navBackStack.add(AdDestination.SellerPublishSuccess(it))
                            },
                            pendingCategory = pendingCategory,
                            onCategoryConsumed = { pendingCategory = null },
                            onConnectOlxClick = onConnectOlxClick,
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

                    entry<AdDestination.Profile> { destination ->
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
                            onViewOnOlx = { openUrl(destination.data.url) },
                            onCreateAnother = popToAdRoot,
                        )
                    }
                },
            )
        }
    }
}

private enum class SellerRootTab(val destination: AdDestination) {
    GenerateAd(AdDestination.GenerateAd),
    MyAdverts(AdDestination.MyAdverts),
    Profile(AdDestination.Profile()),
}

private fun AdDestination?.toSellerRootTab(): SellerRootTab? = when (this) {
    AdDestination.GenerateAd -> SellerRootTab.GenerateAd
    AdDestination.MyAdverts -> SellerRootTab.MyAdverts
    is AdDestination.Profile -> if (reason == null) SellerRootTab.Profile else null
    else -> null
}

@Composable
private fun SellerBottomNavigation(
    selectedTab: SellerRootTab,
    onTabSelected: (SellerRootTab) -> Unit,
) {
    NavigationBar {
        SellerNavigationItem(
            selected = selectedTab == SellerRootTab.GenerateAd,
            icon = Res.drawable.ic_camera,
            label = stringResource(Res.string.new_listing),
            onClick = { onTabSelected(SellerRootTab.GenerateAd) },
        )
        SellerNavigationItem(
            selected = selectedTab == SellerRootTab.MyAdverts,
            icon = Res.drawable.ic_tag,
            label = stringResource(Res.string.nav_my_ads),
            onClick = { onTabSelected(SellerRootTab.MyAdverts) },
        )
        SellerNavigationItem(
            selected = selectedTab == SellerRootTab.Profile,
            icon = Res.drawable.ic_user,
            label = stringResource(Res.string.profile_screen_title),
            onClick = { onTabSelected(SellerRootTab.Profile) },
        )
    }
}

@Composable
private fun RowScope.SellerNavigationItem(
    selected: Boolean,
    icon: org.jetbrains.compose.resources.DrawableResource,
    label: String,
    onClick: () -> Unit,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
            )
        },
        label = { Text(label) },
    )
}
