package com.sirelon.aicalories.features.seller.ad

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay
import com.sirelon.aicalories.designsystem.screens.ImagesPreview
import com.sirelon.aicalories.features.seller.ad.generate_ad.GenerateAdScreen
import com.sirelon.aicalories.features.seller.ad.preview_ad.PreviewAdScreen
import com.sirelon.aicalories.features.seller.ad.publish_success.PublishSuccessScreen
import com.sirelon.aicalories.features.seller.auth.data.OlxAuthCallbackBridge
import com.sirelon.aicalories.features.seller.auth.presentation.OlxAuthDialogScreen
import com.sirelon.aicalories.features.seller.categories.domain.OlxCategory
import com.sirelon.aicalories.features.seller.categories.presentation.CategoryPickerSheet
import com.sirelon.aicalories.features.seller.profile.ui.ProfileScreenRoute
import com.sirelon.aicalories.features.seller.whisper.WhisperDemoScreenRoute
import com.sirelon.aicalories.navigation.BottomSheetSceneStrategy
import com.sirelon.aicalories.platform.openUrl


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
                    onProfileClick = { navBackStack.add(AdDestination.Profile()) },
                    onWhisperClick = { navBackStack.add(AdDestination.WhisperDemo) },
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
                    onBack = { navBackStack.removeAt(navBackStack.lastIndex) },
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
