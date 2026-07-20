package com.sirelon.sellsnap

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.sirelon.sellsnap.datastore.initAndroidKeyValueStore
import com.sirelon.sellsnap.designsystem.AppTheme
import com.sirelon.sellsnap.features.media.upload.initAndroidDraftMediaFileStore
import com.sirelon.sellsnap.features.seller.ad.preview_ad.ui.PublishConfirmSheet
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
        initAndroidDraftMediaFileStore(filesDir.absolutePath)
        initAndroidUrlOpener(this)
        publishOlxCallback(intent)

        setContent {
            // Expose Compose testTags as Android resource-ids so Maestro can target them.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { testTagsAsResourceId = true },
            ) {
                App()
            }
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


@PreviewLightDark
@Preview
@Composable
private fun PublishConfirmSheetPreview() {
    AppTheme {
        PublishConfirmSheet(
            imageUrls = listOf(
                "https://source.unsplash.com/random/",
                "https://source.unsplash.com/random/",
                "https://source.unsplash.com/random/"
            ),
            title = "Nike Air Max 90, size 42, worn 2 months",
            categoryLabel = "Shoes / Sneakers",
            priceFormatted = "₴ 1,800",
            onConfirm = {},
            onDismiss = {},
        )
    }
}

