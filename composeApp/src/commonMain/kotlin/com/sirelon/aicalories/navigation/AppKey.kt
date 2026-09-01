package com.sirelon.sellsnap.navigation

import androidx.navigation3.runtime.NavKey
import com.sirelon.sellsnap.analytics.AnalyticsScreen
import com.sirelon.sellsnap.features.seller.ad.AdvertisementWithAttributes
import com.sirelon.sellsnap.features.seller.ad.publish_success.PublishSuccessData
import kotlinx.serialization.Serializable

/**
 * Single back-stack key hierarchy for the whole app, rendered by one app-level `NavDisplay`.
 */
@Serializable
sealed interface AppKey : NavKey, AnalyticsScreen {

    @Serializable
    data object Splash : AppKey {
        override val screenName = "Splash"
    }

    @Serializable
    data object SellerOnboarding : AppKey {
        override val screenName = "SellerOnboarding"
    }

    @Serializable
    data object ConsentPrompt : AppKey {
        override val screenName = "ConsentPrompt"
    }

    @Serializable
    data object SellerLanding : AppKey {
        override val screenName = "SellerLanding"
    }

    @Serializable
    data object OlxCountryPicker : AppKey {
        override val screenName = "OlxCountryPicker"
    }

    @Serializable
    data object DeleteAccountDataConfirm : AppKey {
        override val screenName = "DeleteAccountDataConfirm"
    }

    @Serializable
    data object AddOlxAccountConfirm : AppKey {
        override val screenName = "AddOlxAccountConfirm"
    }

    @Serializable
    data object OlxAccountAuthFailed : AppKey {
        override val screenName = "OlxAccountAuthFailed"
    }

    @Serializable
    data class DisconnectOlxAccountConfirm(val localIndex: Int) : AppKey {
        override val screenName = "DisconnectOlxAccountConfirm"
    }

    // Seller flow - bottom-nav tabs.
    @Serializable
    data object GenerateAd : AppKey {
        override val screenName = "GenerateAd"
    }

    @Serializable
    data object MyAdverts : AppKey {
        override val screenName = "MyAdverts"
    }

    @Serializable
    data class Profile(val reason: String? = null) : AppKey {
        override val screenName = "Profile"
    }

    @Serializable
    data object Settings : AppKey {
        override val screenName = "Settings"
    }

    @Serializable
    data object WhatsNewPrompt : AppKey {
        override val screenName = "WhatsNewPrompt"
    }

    @Serializable
    data object AllReleases : AppKey {
        override val screenName = "AllReleases"
    }

    @Serializable
    data class SellerPublishSuccess(val data: PublishSuccessData) : AppKey {
        override val screenName = "SellerPublishSuccess"
    }

    @Serializable
    data class ImagesPreview(val images: List<String>, val initialPage: Int) : AppKey {
        override val screenName = "ImagesPreview"
    }

    // Preview-ad flow. PreviewAd is the flow root (fixed contentKey, see App.kt) that owns the
    // shared PreviewAdViewModel; the other three are sheets over it. There is no separate
    // "Publishing" key - that's PreviewAdViewModel.state.isPublishing rendered as an overlay
    // inside PreviewAd's own entry, so it survives regardless of which sheet (if any) is open.
    @Serializable
    data class PreviewAd(val advertisement: AdvertisementWithAttributes) : AppKey {
        override val screenName = "PreviewAd"
    }

    @Serializable
    data object SelectCategory : AppKey {
        override val screenName = "SelectCategory"
    }

    @Serializable
    data object PreviewBackInfo : AppKey {
        override val screenName = "PreviewAdBackInfo"
    }

    @Serializable
    data object PreviewPublishConfirm : AppKey {
        override val screenName = "PreviewAdPublishConfirm"
    }

    @Serializable
    data object PreviewAccountPicker : AppKey {
        override val screenName = "PreviewAdAccountPicker"
    }
}

/**
 * True for the four bottom-nav tab roots. A restored back stack whose bottom entry satisfies
 * this represents a real in-progress position within the seller flow (mid-draft on PreviewAd,
 * a non-default tab, ...) - see [com.sirelon.sellsnap.startup.AppNavigationViewModel].
 */
val AppKey.isSellerFlowEntry: Boolean
    get() = this is AppKey.GenerateAd ||
        this is AppKey.MyAdverts ||
        (this is AppKey.Profile && reason == null) ||
        this is AppKey.Settings
