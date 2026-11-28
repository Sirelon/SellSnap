package com.sirelon.sellsnap.storerender

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.sirelon.sellsnap.analytics.Analytics
import com.sirelon.sellsnap.features.seller.ad.ScreenshotPlaceholderAccount
import com.sirelon.sellsnap.features.seller.ad.preview_ad.OlxAttributeState
import com.sirelon.sellsnap.features.seller.ad.preview_ad.PreviewAdContract.AttributesLoadState
import com.sirelon.sellsnap.features.seller.ad.preview_ad.PreviewAdContract.PreviewAdState
import com.sirelon.sellsnap.features.seller.ad.preview_ad.PreviewAdScreenContent
import com.sirelon.sellsnap.features.seller.ad.publish_success.AdvertStatus
import com.sirelon.sellsnap.features.seller.ad.publish_success.PublishSuccessData
import com.sirelon.sellsnap.features.seller.ad.publish_success.PublishSuccessScreen
import com.sirelon.sellsnap.features.seller.categories.domain.AttributeInputType
import com.sirelon.sellsnap.features.seller.categories.domain.AttributeValidationRules
import com.sirelon.sellsnap.features.seller.categories.domain.OlxAttribute
import com.sirelon.sellsnap.features.seller.categories.domain.OlxAttributeValue
import com.sirelon.sellsnap.features.seller.categories.domain.OlxCategory
import com.sirelon.sellsnap.features.seller.currency.domain.OlxCurrency
import com.sirelon.sellsnap.features.seller.location.OlxLocation
import org.koin.compose.KoinApplication
import org.koin.dsl.module
import kotlinx.coroutines.flow.first
import kotlin.test.Test

/**
 * Scenes cut from the listing preview (the listing the AI wrote) and the publish-success screen.
 * Both render the real screen composables from demo state: no view model, no OLX, no network.
 *
 * Everything per market here (category path, attribute labels, location) is demo content for a
 * fictional listing. Inferred: the category names follow how each OLX market words fashion >
 * men's shoes > sneakers; they were not checked against each market's live category tree.
 */
private val PhoneDevices = setOf(
    StoreDevice.IPHONE,
    StoreDevice.ANDROID_PHONE,
    StoreDevice.DUO_OUTER,
    StoreDevice.DUO_INNER,
    StoreDevice.IPAD,
    StoreDevice.ANDROID_TABLET,
)

val previewScenes: List<StoreScene> = listOf(
    StoreScene(id = "listing_ready", devices = PhoneDevices) { ctx ->
        PreviewScreen(ctx = ctx, tailDp = null, scale = 1f)
    },
    StoreScene(id = "price_details", devices = PhoneDevices) { ctx ->
        PreviewScreen(ctx = ctx, tailDp = ctx.device.priceDetailsTailDp(), scale = ctx.device.priceDetailsUiScale())
    },
    // The confetti falls for 2.5 s; 80 frames of 50 ms leaves a clean, finished screen.
    StoreScene(id = "published", devices = PhoneDevices, settleFrames = 80) { ctx ->
        val density = LocalDensity.current
        val scale = ctx.device.publishedUiScale()
        // The screen scrolls; a frame cannot, so on short windows it is drawn a little smaller to
        // keep the status cards from hiding behind the buttons.
        CompositionLocalProvider(LocalDensity provides Density(density.density * scale, density.fontScale)) {
            WithDemoKoin {
                PublishSuccessScreen(
                    data = ctx.publishSuccessData(),
                    onViewOnOlx = {},
                    onCreateAnother = {},
                )
            }
        }
    },
)

/** The Android phone is a little too short for the price card, category and details at full size. */
private fun StoreDevice.priceDetailsUiScale(): Float = if (this == StoreDevice.ANDROID_PHONE) 0.92f else 1f

/** Shrink that makes the whole publish-success screen fit the window without scrolling. */
private fun StoreDevice.publishedUiScale(): Float = when (this) {
    StoreDevice.IPHONE -> 0.85f
    StoreDevice.ANDROID_PHONE -> 0.9f
    StoreDevice.DUO_OUTER -> 0.68f
    else -> 1f
}

/**
 * How far above the end of the page the price_details frame stops, so the price card, the
 * category and the details fill the window. Measured from the end because the text above them
 * wraps differently per language and window, while the cards below the description do not.
 */
private fun StoreDevice.priceDetailsTailDp(): Int = when (this) {
    StoreDevice.IPHONE -> 105
    StoreDevice.DUO_OUTER -> 257
    else -> 0
}

@Composable
private fun PreviewScreen(ctx: StoreRenderContext, tailDp: Int?, scale: Float) {
    val demo = ctx.demo.listing
    val titleState: TextFieldState = rememberTextFieldState(demo.title)
    val descriptionState: TextFieldState = rememberTextFieldState(demo.description)
    val state = remember(ctx.lang) { ctx.previewState() }
    val scrollState = rememberScrollState()
    if (tailDp != null) {
        val tailPx = with(LocalDensity.current) { tailDp.dp.roundToPx() }
        LaunchedEffect(Unit) {
            snapshotFlow { scrollState.maxValue }.first { it in 1 until Int.MAX_VALUE }
            scrollState.scrollTo(scrollState.maxValue - tailPx)
        }
    }
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density * scale, density.fontScale)) {
        WithDemoKoin {
            PreviewAdScreenContent(
                state = state,
                titleState = titleState,
                descriptionState = descriptionState,
                descriptionDictationLanguageTag = null,
                onEvent = {},
                snackbarHostState = remember { SnackbarHostState() },
                onBack = {},
                onChangeLocationClick = {},
                onConnectOlxClick = {},
                onPublishConfirmationRequested = {},
                onTargetAccountRowClick = {},
                showImagesPreview = { _, _ -> },
                scrollState = scrollState,
                // The round back button floats over the scrolled cards; a store frame has no use for it.
                showBackButton = tailDp == null,
            )
        }
    }
}

/** CopyPill reads Analytics through Koin; the harness has none, so give it one that records nothing. */
@Composable
private fun WithDemoKoin(content: @Composable () -> Unit) {
    KoinApplication(application = { modules(module { single<Analytics> { SilentAnalytics } }) }) {
        content()
    }
}

private object SilentAnalytics : Analytics {
    override fun logEvent(name: String, params: Map<String, Any>) = Unit
    override fun setUserId(userId: String?) = Unit
    override fun setUserProperty(name: String, value: String?) = Unit
    override fun recordException(throwable: Throwable, message: String?) = Unit
    override fun log(message: String) = Unit
    override fun setAnalyticsCollectionEnabled(enabled: Boolean) = Unit
    override fun setCrashlyticsCollectionEnabled(enabled: Boolean) = Unit
}

private fun StoreRenderContext.currency(): OlxCurrency = OlxCurrency.fallbackFor(lang.country)

/** The preview a seller sees right after the AI wrote the listing: connected, valid, ready to publish. */
private fun StoreRenderContext.previewState(): PreviewAdState {
    val listing = demo.listing
    val market = PreviewMarket.forLang(lang)
    val sneakers = OlxCategory(id = 1005, label = market.categoryPath.last(), parentId = 1004, isLeaf = true)
    return PreviewAdState(
        categoryLabel = market.categoryPath.joinToString(" / "),
        selectedCategory = sneakers,
        generationElapsedMs = 14_000L,
        isSessionResolved = true,
        price = listing.price.toFloat(),
        minPrice = listing.priceMin.toFloat(),
        maxPrice = listing.priceMax.toFloat(),
        currency = currency(),
        images = listing.photos,
        location = market.location,
        attributeItems = market.attributes(),
        attributesLoadState = AttributesLoadState.Loaded,
        isGuest = false,
        targetAccount = ScreenshotPlaceholderAccount,
        showTargetAccountRow = true,
    )
}

private fun StoreRenderContext.publishSuccessData(): PublishSuccessData {
    val listing = demo.listing
    val market = PreviewMarket.forLang(lang)
    val currency = currency()
    return PublishSuccessData(
        url = "https://www.${lang.country.domain}/${market.urlPath}/new-balance-990v5-IDa1b2c3.html",
        title = listing.title,
        priceFormatted = currency.format(listing.price.toFloat()),
        primaryImageUrl = listing.photos.first(),
        totalElapsedMs = 52_000L,
        status = AdvertStatus.Active,
        accountName = ScreenshotPlaceholderAccount.name,
    )
}

/** What one OLX market calls things on the sneaker listing. */
private class PreviewMarket(
    val categoryPath: List<String>,
    val location: OlxLocation,
    val urlPath: String,
    private val conditionLabel: String,
    private val conditionValue: String,
    private val sizeLabel: String,
    private val brandLabel: String,
) {
    fun attributes(): List<OlxAttributeState> = listOf(
        selected(code = "state", label = conditionLabel, required = true, value = conditionValue),
        selected(code = "size", label = sizeLabel, required = true, value = "42"),
        selected(code = "brand", label = brandLabel, required = false, value = "New Balance"),
    )

    private fun selected(code: String, label: String, required: Boolean, value: String): OlxAttributeState {
        val chosen = OlxAttributeValue(code = value.lowercase().replace(' ', '_'), label = value)
        return OlxAttributeState(
            attribute = OlxAttribute(
                code = code,
                label = label,
                unit = "",
                inputType = AttributeInputType.SingleSelect,
                validationRules = AttributeValidationRules(
                    required = required,
                    numeric = false,
                    min = null,
                    max = null,
                    allowMultipleValues = false,
                ),
                allowedValues = listOf(chosen),
            ),
            selectedValues = listOf(chosen),
        )
    }

    companion object {
        fun forLang(lang: StoreLang): PreviewMarket = when (lang) {
            StoreLang.EN -> PreviewMarket(
                categoryPath = listOf("Fashion", "Men's shoes", "Sneakers"),
                location = OlxLocation(1, "Lisbon", 11, "Alvalade"),
                urlPath = "d/anuncio",
                conditionLabel = "Condition",
                conditionValue = "Used",
                sizeLabel = "Size",
                brandLabel = "Brand",
            )

            StoreLang.UK -> PreviewMarket(
                categoryPath = listOf("Мода і стиль", "Чоловіче взуття", "Кросівки"),
                location = OlxLocation(2, "Київ", 21, "Шевченківський"),
                urlPath = "d/uk/obyavlenie",
                conditionLabel = "Стан",
                conditionValue = "Вживане",
                sizeLabel = "Розмір",
                brandLabel = "Бренд",
            )

            StoreLang.PL -> PreviewMarket(
                categoryPath = listOf("Moda", "Obuwie męskie", "Buty sportowe"),
                location = OlxLocation(3, "Warszawa", 31, "Mokotów"),
                urlPath = "d/oferta",
                conditionLabel = "Stan",
                conditionValue = "Używane",
                sizeLabel = "Rozmiar",
                brandLabel = "Marka",
            )

            StoreLang.RO -> PreviewMarket(
                categoryPath = listOf("Modă și frumusețe", "Încălțăminte bărbați", "Adidași"),
                location = OlxLocation(4, "București", 41, "Sector 3"),
                urlPath = "d/oferta",
                conditionLabel = "Stare",
                conditionValue = "Folosit",
                sizeLabel = "Mărime",
                brandLabel = "Marcă",
            )

            StoreLang.BG -> PreviewMarket(
                categoryPath = listOf("Мода", "Мъжки обувки", "Спортни обувки"),
                location = OlxLocation(5, "София", 51, "Лозенец"),
                urlPath = "d/ad",
                conditionLabel = "Състояние",
                conditionValue = "Използвано",
                sizeLabel = "Размер",
                brandLabel = "Марка",
            )

            StoreLang.PT -> PreviewMarket(
                categoryPath = listOf("Moda", "Calçado de homem", "Sapatilhas"),
                location = OlxLocation(6, "Lisboa", 61, "Alvalade"),
                urlPath = "d/anuncio",
                conditionLabel = "Estado",
                conditionValue = "Usado",
                sizeLabel = "Tamanho",
                brandLabel = "Marca",
            )

            StoreLang.KK -> PreviewMarket(
                categoryPath = listOf("Сән және стиль", "Ерлер аяқ киімі", "Кроссовкалар"),
                location = OlxLocation(7, "Алматы", 71, "Бостандық ауданы"),
                urlPath = "d/obyavlenie",
                conditionLabel = "Жағдайы",
                conditionValue = "Қолданылған",
                sizeLabel = "Өлшемі",
                brandLabel = "Бренд",
            )
        }
    }
}
