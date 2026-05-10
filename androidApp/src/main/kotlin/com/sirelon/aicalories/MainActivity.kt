package com.sirelon.sellsnap

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.sirelon.sellsnap.datastore.initAndroidKeyValueStore
import com.sirelon.sellsnap.designsystem.AppTheme
import com.sirelon.sellsnap.features.seller.ad.publish_success.PublishSuccessData
import com.sirelon.sellsnap.features.seller.ad.publish_success.PublishSuccessScreen
import com.sirelon.sellsnap.features.seller.auth.data.OlxAuthCallbackBridge
import com.sirelon.sellsnap.features.seller.auth.presentation.SellerAuthContract
import com.sirelon.sellsnap.features.seller.auth.presentation.SellerLandingScreen
import com.sirelon.sellsnap.platform.initAndroidUrlOpener

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        super.onCreate(savedInstanceState)
        initAndroidKeyValueStore(filesDir.absolutePath)
        initAndroidUrlOpener(this)
        publishOlxCallback(intent)

        setContent {
            App()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        publishOlxCallback(intent)
    }

    private fun publishOlxCallback(intent: Intent?) {
        intent?.dataString
            ?.takeIf { it.startsWith("selolxai://olx-auth") }
            ?.let(OlxAuthCallbackBridge::publishCallback)
    }
}

@Preview
@Composable
private fun SellerLandingScreenPreview() {
    AppTheme {
        SellerLandingScreen(
            state = SellerAuthContract.SellerAuthState(),
            onEvent = {},
        )
    }
}

@Preview
@PreviewLightDark
@Composable
private fun PublishSuccessScreenPreview() {
    AppTheme {
        PublishSuccessScreen(
            data = PublishSuccessData(
                url = "https://www.olx.ua/d/uk/obyavlenie/krosvki-nike-air-max-ID123456.html",
                title = "Кросівки Nike Air Max 90, розмір 42",
                priceFormatted = "₴ 1 850",
                primaryImageUrl = null,
                totalElapsedMs = 92_000L,
            ),
            onViewOnOlx = {},
            onCreateAnother = {},
        )
    }
}
