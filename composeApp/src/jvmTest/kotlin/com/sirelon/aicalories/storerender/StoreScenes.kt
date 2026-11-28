package com.sirelon.sellsnap.storerender

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import com.mohamedrejeb.calf.io.KmpFile
import com.sirelon.sellsnap.SellerRootTab
import com.sirelon.sellsnap.features.media.upload.UploadedFile
import com.sirelon.sellsnap.features.media.upload.UploadingItem
import com.sirelon.sellsnap.features.seller.ad.generate_ad.AiProcessingScreen
import com.sirelon.sellsnap.features.seller.ad.generate_ad.GenerateAdContract
import com.sirelon.sellsnap.features.seller.ad.generate_ad.GenerateAdScreenContent
import java.io.File
import kotlin.test.Test

/** A real file holding demo photo [name], for screens that take picked files. */
fun demoFile(name: String): KmpFile {
    val file = File(System.getProperty("java.io.tmpdir"), "store-demo/$name.jpg")
    if (!file.exists()) {
        file.parentFile.mkdirs()
        file.writeBytes(StoreRenderer::class.java.getResourceAsStream("/store-demo/$name.jpg")!!.readBytes())
    }
    return KmpFile(file)
}

/**
 * Every scene the store screenshots are cut from. Order here is not the store order: the
 * manifest `store/tools/scenes.json` picks scenes and binds each one to its caption.
 *
 * Run: `STORE_RENDER=1 ./gradlew :composeApp:jvmTest --tests '*StoreScenesTest*'`, narrowed with
 * `STORE_RENDER_SCENES`, `STORE_RENDER_DEVICES`, `STORE_RENDER_LANGS`, `STORE_RENDER_THEMES`
 * (comma-separated). Without `STORE_RENDER=1` the test does nothing, so CI stays fast.
 */
val storeScenes: List<StoreScene> = listOf(
    StoreScene(id = "new_listing", fullBleedBottom = true) { ctx ->
        AppChrome(tab = SellerRootTab.GenerateAd) { GenerateAdScreenContent(
            state = GenerateAdContract.GenerateAdState(
                prompt = ctx.demo.hint,
                uploads = listOf("sneakers_1", "sneakers_2", "sneakers_3").associate { name ->
                    demoFile(name) to UploadingItem(
                        progress = 1.0,
                        uploadedFile = UploadedFile(id = name, path = demoPhoto(name)),
                    )
                },
            ),
            hintSpeechToText = null,
            snackbarHostState = remember { SnackbarHostState() },
            onPromptChanged = {},
            onTakePhotoClick = {},
            onUploadClick = {},
            onRemovePhoto = {},
            onSubmitClick = {},
            onOpenDraft = {},
            onSeeAllDrafts = {},
            onReconnect = {},
        ) }
    },
    StoreScene(id = "ai_writing") {
        AiProcessingScreen(
            completedSteps = 3,
            isGuestMode = false,
            onCancelClick = {},
        )
    },
) + previewScenes + sellerScenes

class StoreScenesTest {
    @Test
    fun render() {
        if (System.getenv("STORE_RENDER") != "1") return
        StoreRenderer.renderAll(storeScenes)
    }
}
