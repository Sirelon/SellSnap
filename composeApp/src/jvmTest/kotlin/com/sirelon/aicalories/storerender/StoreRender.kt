package com.sirelon.sellsnap.storerender

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import coil3.ColorImage
import coil3.annotation.ExperimentalCoilApi
import coil3.asImage
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import com.mohamedrejeb.calf.io.KmpFile
import com.sirelon.sellsnap.designsystem.AppTheme
import com.sirelon.sellsnap.features.seller.auth.data._currentOlxCountry
import com.sirelon.sellsnap.features.seller.auth.domain.OlxCountry
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import java.util.Locale

/**
 * Store-screenshot renderer: draws shared screens headless, at store pixel sizes, per locale and
 * theme, from demo data. No login, no OLX, no OpenAI, no device.
 *
 * Output: `store/captures/<device>/<lang>/<scene>_<theme>.png`, the input of
 * `store/tools/generate-store-screenshots.mjs`. Workflow: `.claude/skills/sellsnap-screenshots/`.
 */
enum class StoreDevice(
    val dir: String,
    val widthPx: Int,
    val heightPx: Int,
    val density: Float,
    /** Status-bar strip the frame draws its clock and icons into. JVM has no window insets. */
    val topInset: Dp,
    /** Home-indicator / gesture-bar strip. */
    val bottomInset: Dp,
) {
    // iPhone 6.9" (Dynamic Island large), 1320x2868 @3x = 440x956 pt: the App Store slot size.
    IPHONE("iphone", 1320, 2868, 3f, 62.dp, 34.dp),

    // iPad 13" landscape, 2752x2064 @2x = 1376x1032 pt.
    IPAD("ipad", 2752, 2064, 2f, 24.dp, 20.dp),

    // Inferred: iPhone Duo point sizes are not published on the screenshot page; @3x assumed.
    // Inner display 2007x2853 and outer 1398x2034 are App Store Connect's accepted pixel sizes.
    DUO_INNER("iphone-duo-inner", 2007, 2853, 3f, 40.dp, 24.dp),
    DUO_OUTER("iphone-duo-outer", 1398, 2034, 3f, 44.dp, 24.dp),

    // A 20:9 Android phone, 1080x2400 at 420 dpi = 411x914 dp.
    ANDROID_PHONE("android-phone", 1080, 2400, 2.625f, 36.dp, 24.dp),

    // A 16:10 Android tablet in landscape, 2560x1600 @2x = 1280x800 dp.
    ANDROID_TABLET("android-tablet", 2560, 1600, 2f, 28.dp, 24.dp),
}

enum class StoreLang(val code: String, val country: OlxCountry) {
    EN("en", OlxCountry.PT),
    UK("uk", OlxCountry.UA),
    PL("pl", OlxCountry.PL),
    RO("ro", OlxCountry.RO),
    BG("bg", OlxCountry.BG),
    PT("pt", OlxCountry.PT),
    KK("kk", OlxCountry.KZ),
}

/** What a scene gets to render with. */
class StoreRenderContext(
    val device: StoreDevice,
    val lang: StoreLang,
    val dark: Boolean,
) {
    val demo: StoreDemo get() = StoreDemo.forLang(lang)
}

class StoreScene(
    val id: String,
    /** Devices this scene makes sense on; a phone-only sheet is pointless on a tablet. */
    val devices: Set<StoreDevice> = StoreDevice.entries.toSet(),
    /** Frames to advance before the saved one, 50 ms apart, for entry animations. */
    val settleFrames: Int = 40,
    /**
     * The scene draws into the home-bar strip itself ([AppChrome] paints it in the navigation
     * bar's colour); otherwise the harness leaves a background-coloured strip.
     */
    val fullBleedBottom: Boolean = false,
    val content: @Composable (StoreRenderContext) -> Unit,
)

/** Height of the home-bar strip, for scenes with [StoreScene.fullBleedBottom]. */
val LocalStoreBottomInset = androidx.compose.runtime.staticCompositionLocalOf { 0.dp }

object StoreRenderer {

    private val repoRoot: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    val capturesRoot = File(repoRoot, "store/captures")

    fun renderAll(scenes: List<StoreScene>) {
        val sceneFilter = envList("STORE_RENDER_SCENES")
        val deviceFilter = envList("STORE_RENDER_DEVICES")
        val langFilter = envList("STORE_RENDER_LANGS")
        val themeFilter = envList("STORE_RENDER_THEMES")
        var count = 0
        val failures = mutableListOf<String>()
        for (scene in scenes) {
            if (sceneFilter != null && scene.id !in sceneFilter) continue
            for (device in scene.devices) {
                if (deviceFilter != null && device.dir !in deviceFilter) continue
                for (lang in StoreLang.entries) {
                    if (langFilter != null && lang.code !in langFilter) continue
                    for (dark in listOf(false, true)) {
                        val theme = if (dark) "dark" else "light"
                        if (themeFilter != null && theme !in themeFilter) continue
                        val ctx = StoreRenderContext(device, lang, dark)
                        // An effect left over from the previous scene can resume on a timer thread
                        // and trip Compose's single-thread layout check; one retry renders clean.
                        val result = runCatching { render(scene, ctx) }
                            .recoverCatching { render(scene, ctx) }
                        result.exceptionOrNull()?.let {
                            failures += "${device.dir}/${lang.code}/${scene.id}_$theme: ${it.message?.take(160)}"
                        } ?: count++
                    }
                }
            }
        }
        println("store-render: $count files under ${capturesRoot.absolutePath}")
        check(failures.isEmpty()) { "store-render failed:\n" + failures.joinToString("\n") }
    }

    @OptIn(ExperimentalCoilApi::class, ExperimentalComposeUiApi::class)
    private fun render(scene: StoreScene, ctx: StoreRenderContext) {
        Locale.setDefault(Locale.forLanguageTag(ctx.lang.code))
        _currentOlxCountry = ctx.lang.country
        val theme = if (ctx.dark) "dark" else "light"
        val out = File(capturesRoot, "${ctx.device.dir}/${ctx.lang.code}/${scene.id}_$theme.png")
        out.parentFile.mkdirs()
        ImageComposeScene(
            width = ctx.device.widthPx,
            height = ctx.device.heightPx,
            density = Density(ctx.device.density),
        ) {
            CompositionLocalProvider(
                // Inspection mode makes Coil draw through the preview handler below, synchronously,
                // and stops looping effects (rotating tips) so every frame is deterministic.
                LocalInspectionMode provides true,
                LocalAsyncImagePreviewHandler provides DemoImageHandler,
            ) {
                AppTheme(darkTheme = ctx.dark) {
                    Column(Modifier.fillMaxSize().background(AppTheme.colors.background)) {
                        Spacer(Modifier.fillMaxWidth().height(ctx.device.topInset))
                        Box(Modifier.fillMaxWidth().weight(1f)) {
                            CompositionLocalProvider(LocalStoreBottomInset provides ctx.device.bottomInset) {
                                scene.content(ctx)
                            }
                        }
                        if (!scene.fullBleedBottom) {
                            Spacer(Modifier.fillMaxWidth().height(ctx.device.bottomInset))
                        }
                    }
                }
            }
        }.use { composeScene ->
            var t = 0L
            repeat(scene.settleFrames) { composeScene.render(t); t += 50_000_000L }
            val image = composeScene.render(t)
            out.writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
        println("store-render: ${out.relativeTo(repoRoot)}")
    }

    private fun envList(name: String): Set<String>? =
        System.getenv(name)?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()
            ?.takeIf { it.isNotEmpty() }
}

/**
 * Serves every Coil request from bundled demo photos. Data is either a `demo:<name>` string
 * (resource `store-demo/<name>.jpg`) or anything whose `toString()` ends in a demo photo name.
 */
@OptIn(ExperimentalCoilApi::class)
private val DemoImageHandler = AsyncImagePreviewHandler { request ->
    val data = request.data
    val raw = if (data is KmpFile) data.file.name else data.toString()
    val key = raw.substringAfter("demo:").substringAfterLast('/').substringBefore('.')
    demoImage(key) ?: ColorImage(0xFFCCCCCC.toInt())
}

private val imageCache = mutableMapOf<String, coil3.Image?>()

fun demoImage(name: String): coil3.Image? = imageCache.getOrPut(name) {
    val bytes = StoreRenderer::class.java.getResourceAsStream("/store-demo/$name.jpg")?.readBytes()
        ?: return@getOrPut null
    Bitmap.makeFromImage(Image.makeFromEncoded(bytes)).asImage()
}

/** URL form a domain model can carry; [DemoImageHandler] resolves it back to the photo. */
fun demoPhoto(name: String): String = "https://demo.sellsnap.app/$name.jpg"
