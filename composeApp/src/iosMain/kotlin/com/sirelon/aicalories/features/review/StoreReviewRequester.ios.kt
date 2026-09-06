package com.sirelon.sellsnap.features.review

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.StoreKit.SKStoreReviewController
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow

/**
 * `SKStoreReviewController.requestReviewInScene:` is the only App Store review entry point
 * reachable from Kotlin: StoreKit 2's replacement, `AppStore.requestReview(in:)`, is Swift-only and
 * so is not exposed to Objective-C interop. The scene-taking overload is deprecated as of iOS 18
 * but remains present in the SDK, and bridging to Swift for one void call would mean a permanent
 * second build path.
 *
 * https://developer.apple.com/documentation/storekit/skstorereviewcontroller/requestreview(in:)
 */
@Composable
actual fun rememberStoreReviewRequester(): () -> Unit = remember {
    {
        // The deployment target is iOS 18.2, so a window scene always exists once anything is on
        // screen; reaching it through the key window matches how this app already finds its
        // presentation anchor (see OlxExternalAuthLauncher.ios.kt).
        @Suppress("UNCHECKED_CAST")
        val scene = (UIApplication.sharedApplication.windows as List<UIWindow>)
            .firstOrNull { it.isKeyWindow() }
            ?.windowScene
        if (scene != null) {
            SKStoreReviewController.requestReviewInScene(scene)
        }
    }
}
