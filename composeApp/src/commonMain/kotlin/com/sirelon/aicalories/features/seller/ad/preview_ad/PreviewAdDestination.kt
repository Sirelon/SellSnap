package com.sirelon.sellsnap.features.seller.ad.preview_ad

import androidx.navigation3.runtime.NavKey
import com.sirelon.sellsnap.analytics.AnalyticsScreen
import kotlinx.serialization.Serializable

internal sealed interface PreviewAdDestination : NavKey, AnalyticsScreen {
    @Serializable
    data object Content : PreviewAdDestination {
        override val screenName = "PreviewAdContent"
    }

    @Serializable
    data object BackInfo : PreviewAdDestination {
        override val screenName = "PreviewAdBackInfo"
    }

    @Serializable
    data object PublishConfirm : PreviewAdDestination {
        override val screenName = "PreviewAdPublishConfirm"
    }

    @Serializable
    data object Publishing : PreviewAdDestination {
        override val screenName = "PreviewAdPublishing"
    }

    @Serializable
    data object AccountPicker : PreviewAdDestination {
        override val screenName = "PreviewAdAccountPicker"
    }
}
