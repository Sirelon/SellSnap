package com.sirelon.sellsnap

import android.content.Intent
import android.net.Uri
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
import androidx.lifecycle.lifecycleScope
import com.google.firebase.messaging.FirebaseMessaging
import com.sirelon.sellsnap.datastore.initAndroidKeyValueStore
import com.sirelon.sellsnap.designsystem.AppTheme
import com.sirelon.sellsnap.features.media.initAndroidSharedImages
import com.sirelon.sellsnap.features.media.publishSharedImageUris
import com.sirelon.sellsnap.features.media.upload.initAndroidDraftMediaFileStore
import com.sirelon.sellsnap.features.seller.ad.initAndroidScreenshotPhotos
import com.sirelon.sellsnap.features.seller.ad.preview_ad.ui.PublishConfirmSheet
import com.sirelon.sellsnap.features.seller.auth.data.OlxAuthCallbackBridge
import com.sirelon.sellsnap.features.seller.auth.presentation.SellerAuthContract
import com.sirelon.sellsnap.features.seller.auth.presentation.SellerLandingScreen
import com.sirelon.sellsnap.features.seller.drafts.data.initAndroidDatabase
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.notification_channel_updates
import com.sirelon.sellsnap.platform.initAndroidUrlOpener
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        super.onCreate(savedInstanceState)
        installAppCheckProviderFactory()
        initAndroidKeyValueStore(filesDir.absolutePath)
        initAndroidDatabase(applicationContext)
        initAndroidDraftMediaFileStore(filesDir.absolutePath)
        initAndroidScreenshotPhotos(cacheDir.absolutePath)
        initAndroidUrlOpener(this)
        initAndroidSharedImages(applicationContext)
        lifecycleScope.launch {
            createPushChannel(this@MainActivity, getString(Res.string.notification_channel_updates))
        }
        // Debug builds also join `qa`, so test pushes never have to target the `all*` broadcasts.
        if (BuildConfig.DEBUG) FirebaseMessaging.getInstance().subscribeToTopic("qa")
        publishOlxCallback(intent)
        publishSharedImages(intent)
        // A recreated activity (rotation, process restore) re-delivers the original launch intent.
        if (savedInstanceState == null) openPushLink(intent)

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
        publishSharedImages(intent)
        openPushLink(intent)
    }

    private fun openPushLink(intent: Intent?) {
        intent ?: return
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        val link = intent.getStringExtra(PUSH_LINK_KEY) ?: return
        // Consume the extra so the same push is not opened again by a later re-delivery.
        intent.removeExtra(PUSH_LINK_KEY)
        resolvePushLink(link)?.let { url -> openPushUrl(this, url) }
    }

    private fun publishOlxCallback(intent: Intent?) {
        intent?.dataString
            ?.takeIf { it.startsWith("selolxai://olx-auth") }
            ?.let(OlxAuthCallbackBridge::publishCallback)
    }

    private fun publishSharedImages(intent: Intent?) {
        intent ?: return
        if (intent.type?.startsWith("image/") != true) return
        val uris = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(intent.parcelableExtra(Intent.EXTRA_STREAM))
            Intent.ACTION_SEND_MULTIPLE -> intent.parcelableArrayListExtra(Intent.EXTRA_STREAM)
            else -> emptyList()
        }
        publishSharedImageUris(uris)
    }

    @Suppress("DEPRECATION")
    private fun Intent.parcelableExtra(name: String): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(name, Uri::class.java)
        } else {
            getParcelableExtra(name)
        }

    @Suppress("DEPRECATION")
    private fun Intent.parcelableArrayListExtra(name: String): List<Uri> =
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableArrayListExtra(name, Uri::class.java)
        } else {
            getParcelableArrayListExtra(name)
        }).orEmpty()
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

