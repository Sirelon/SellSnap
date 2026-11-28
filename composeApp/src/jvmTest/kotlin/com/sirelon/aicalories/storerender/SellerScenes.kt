package com.sirelon.sellsnap.storerender

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.VerticalDivider
import androidx.compose.ui.Modifier
import com.sirelon.sellsnap.SellerRootTab
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.sirelon.sellsnap.features.seller.ad.publish_success.AdvertStatus
import com.sirelon.sellsnap.features.seller.auth.domain.OlxAdvertStatistics
import com.sirelon.sellsnap.features.seller.currency.domain.OlxCurrency
import com.sirelon.sellsnap.features.seller.messages.domain.OlxMessage
import com.sirelon.sellsnap.features.seller.messages.domain.ReplySuggestion
import com.sirelon.sellsnap.features.seller.messages.domain.ScamSignals
import com.sirelon.sellsnap.features.seller.messages.presentation.ConversationContract
import com.sirelon.sellsnap.features.seller.messages.presentation.MessagesContract
import com.sirelon.sellsnap.features.seller.messages.ui.ConversationScreen
import com.sirelon.sellsnap.features.seller.messages.ui.MessagesScreen
import com.sirelon.sellsnap.features.seller.my_ads.domain.availableActions
import com.sirelon.sellsnap.features.seller.my_ads.model.MyAdvertItem
import com.sirelon.sellsnap.features.seller.my_ads.presentation.MyAdvertsContract
import com.sirelon.sellsnap.features.seller.my_ads.ui.MyAdvertsScreen
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Scenes cut from the seller's own screens: My Ads, the listing sheet with its statistics, the
 * buyer conversation (also with a scam warning) and the threads list. Each renders the real screen
 * composable from demo state: no view model, no OLX, no network.
 *
 * Times are relative to the render moment (the screens read the clock themselves), so "2 days
 * left" and "12 minutes ago" stay true on whatever day the frames are cut.
 */
private val TabletDevices = setOf(StoreDevice.IPAD, StoreDevice.ANDROID_TABLET)

private val SellerDevices = setOf(
    StoreDevice.IPHONE,
    StoreDevice.ANDROID_PHONE,
    StoreDevice.DUO_OUTER,
    StoreDevice.DUO_INNER,
)

val sellerScenes: List<StoreScene> = listOf(
    StoreScene(id = "my_ads", devices = SellerDevices + TabletDevices, fullBleedBottom = true) { ctx ->
        AppChrome(tab = SellerRootTab.MyAdverts, unreadMessages = 3) {
            MyAdvertsScreen(
                state = ctx.myAdsState(),
                snackbarHostState = remember { SnackbarHostState() },
                onEvent = {},
            )
        }
    },
    // The sheet is a ModalBottomSheet: it animates in, so give it frames to settle.
    StoreScene(id = "ad_stats", devices = SellerDevices, settleFrames = 60, fullBleedBottom = true) { ctx ->
        val adverts = ctx.adverts()
        val bike = adverts[1]
        AppChrome(tab = SellerRootTab.MyAdverts, unreadMessages = 3) { MyAdvertsScreen(
            state = ctx.myAdsState().copy(
                advertSheet = MyAdvertsContract.AdvertSheet(
                    localIndex = 0,
                    advert = bike,
                    actions = availableActions(bike.status, ctx.lang.country.supportsExtendCommand),
                    extendUnavailableHere = !ctx.lang.country.supportsExtendCommand,
                    statistics = OlxAdvertStatistics(advertViews = 212, phoneViews = 6, usersObserving = 9),
                ),
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onEvent = {},
        ) }
    },
    StoreScene(id = "messages", devices = SellerDevices) { ctx ->
        ConversationScene(ctx = ctx, scam = false)
    },
    StoreScene(id = "messages_scam", devices = SellerDevices) { ctx ->
        ConversationScene(ctx = ctx, scam = true)
    },
    StoreScene(id = "messages_list", devices = SellerDevices, fullBleedBottom = true) { ctx ->
        AppChrome(tab = SellerRootTab.Messages, unreadMessages = 3) {
            MessagesScreen(
                state = ctx.threadsState(),
                selectedThreadId = null,
                onEvent = {},
            )
        }
    },
    // Wide windows: the app shows the thread list and the open conversation side by side
    // (ListDetailSceneStrategy in App.kt, list pane anchored from 320 dp up to half the width).
    StoreScene(id = "messages_two_pane", devices = TabletDevices, fullBleedBottom = true) { ctx ->
        AppChrome(tab = SellerRootTab.Messages, unreadMessages = 2) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(0.42f).fillMaxHeight()) {
                    MessagesScreen(
                        state = ctx.threadsState(),
                        selectedThreadId = 1L, // the guitar thread, open on the right
                        onEvent = {},
                    )
                }
                VerticalDivider()
                Box(Modifier.weight(0.58f).fillMaxHeight()) {
                    ConversationScene(ctx = ctx, scam = false)
                }
            }
        }
    },
)


// ---------------------------------------------------------------------------------------------
// Shared data
// ---------------------------------------------------------------------------------------------

private val StoreRenderContext.seller: SellerDemo get() = sellerDemoFor(lang)

private fun StoreRenderContext.money(value: Long): String =
    OlxCurrency.format(value = value, code = lang.country.currencyCode)

private fun StoreRenderContext.zone(): TimeZone = lang.country.timeZone

/** A currency label that ends in a dot ("грн.") followed by the sentence's own dot reads as "..". */
private fun String.dedot() = replace("..", ".")

private fun two(value: Int) = value.toString().padStart(2, '0')

/** OLX's advert timestamps: ISO-8601 with the market's offset, e.g. `2026-11-28T10:00:00+02:00`. */
private fun Instant.olxIso(zone: TimeZone): String {
    val local = toLocalDateTime(zone)
    return "${local.year}-${two(local.month.number)}-${two(local.day)}T" +
        "${two(local.hour)}:${two(local.minute)}:${two(local.second)}${zone.offsetAt(this)}"
}

/** The thread and message timestamps: the market's wall clock without an offset, `2026-10-30 21:33:53`. */
private fun Instant.olxWallClock(zone: TimeZone): String {
    val local = toLocalDateTime(zone)
    return "${local.year}-${two(local.month.number)}-${two(local.day)} " +
        "${two(local.hour)}:${two(local.minute)}:${two(local.second)}"
}

private const val SneakersId = 1001L
private const val BikeId = 1002L
private const val GuitarId = 1003L
private const val ChairId = 1004L
private const val CameraId = 1005L
private const val LampId = 1006L

/** The seller's listings, the sneakers (just published) first. */
private fun StoreRenderContext.adverts(): List<MyAdvertItem> {
    val now = Clock.System.now()
    val zone = zone()
    val currency = lang.country.currencyCode
    val prices = sellerPricesFor(currency)

    fun advert(
        id: Long,
        title: String,
        photo: String,
        price: Long,
        status: AdvertStatus,
        createdAgo: Duration,
        validFor: Duration,
    ) = MyAdvertItem(
        id = id,
        title = title,
        status = status,
        url = "https://www.${lang.country.domain}/d/demo-$id.html",
        primaryImageUrl = demoPhoto(photo),
        priceFormatted = OlxCurrency.format(value = price, code = currency),
        priceValue = price,
        currencyCode = currency,
        // OLX sends an ISO timestamp and the row prints it as is, which truncates to "2026-11-28T12:15:...".
        // The frame shows the date only (a follow-up: format it in the app).
        createdAt = (now - createdAgo).olxIso(zone).substringBefore("T"),
        validTo = (now + validFor).olxIso(zone),
    )

    val sneakers = demo.listing
    return listOf(
        advert(SneakersId, sneakers.title, "sneakers_1", sneakers.price.toLong(), AdvertStatus.Active, 20.minutes, 30.days),
        advert(BikeId, seller.bike, "bike", prices.bike, AdvertStatus.Active, 9.days, 21.days),
        advert(GuitarId, seller.guitar, "guitar", prices.guitar, AdvertStatus.Active, 12.days, 18.days),
        // Two days and a few hours left: the row calls the expiry out.
        advert(ChairId, seller.chair, "chair", prices.chair, AdvertStatus.Active, 28.days, 2.days + 5.hours),
        // OLX is still checking it.
        advert(CameraId, seller.camera, "camera", prices.camera, AdvertStatus.New, 1.hours, 30.days),
        // The seller took it down.
        advert(LampId, seller.lamp, "lamp", prices.lamp, AdvertStatus.RemovedByUser, 35.days, 5.days),
    )
}

// ---------------------------------------------------------------------------------------------
// My Ads and the listing sheet
// ---------------------------------------------------------------------------------------------

private fun StoreRenderContext.myAdsState(): MyAdvertsContract.State {
    val firstPage = MyAdvertsContract.AccountPage(
        localIndex = 0,
        accountName = seller.account,
        avatarUrl = null,
        isActiveAccount = true,
        needsReconnect = false,
        adverts = adverts(),
        hasLoaded = true,
        unreadByAdvert = mapOf(BikeId to 2, GuitarId to 1),
    )
    val secondPage = MyAdvertsContract.AccountPage(
        localIndex = 1,
        accountName = seller.shop,
        avatarUrl = null,
        isActiveAccount = false,
        needsReconnect = false,
        hasLoaded = true,
    )
    return MyAdvertsContract.State(
        pages = listOf(firstPage, secondPage),
        selectedLocalIndex = 0,
    )
}

// ---------------------------------------------------------------------------------------------
// Messages
// ---------------------------------------------------------------------------------------------

private fun StoreRenderContext.message(id: Long, minutesAgo: Long, isSent: Boolean, text: String) = OlxMessage(
    id = id,
    createdAt = (Clock.System.now() - minutesAgo.minutes).olxWallClock(zone()),
    isSent = isSent,
    text = text,
    attachmentUrls = emptyList(),
)

@Composable
private fun ConversationScene(ctx: StoreRenderContext, scam: Boolean) {
    val adverts = ctx.adverts()
    val listing = if (scam) adverts[1] else adverts[2]
    var messages: List<OlxMessage>
    var suggestions = emptyList<ReplySuggestion>()
    if (scam) {
        val chat = ctx.seller.scamChat
        messages = listOf(
            ctx.message(1, 95, isSent = false, chat.buyerAsks),
            ctx.message(2, 90, isSent = true, chat.sellerYes),
            ctx.message(3, 4, isSent = false, "${chat.buyerPaid} ${chat.buyerLink}"),
        )
    } else {
        val chat = ctx.seller.guitarChat
        val prices = sellerPricesFor(ctx.lang.country.currencyCode)
        messages = listOf(
            ctx.message(1, 95, isSent = false, chat.buyerAsks),
            ctx.message(2, 90, isSent = true, "${chat.sellerYes} ${chat.sellerCase}"),
            ctx.message(3, 4, isSent = false, chat.buyerOffer(ctx.money(prices.guitarOffer))),
        )
        suggestions = listOf(
            ReplySuggestion(
                text = chat.suggestKeep(ctx.money(prices.guitar)).dedot(),
                needsReview = false,
                price = prices.guitar,
            ),
            // Proposes a pickup, so the model flags it: a tap opens it for editing, never sends it.
            ReplySuggestion(
                text = chat.suggestAccept(ctx.money(prices.guitarCounter)).dedot(),
                needsReview = true,
                price = prices.guitarCounter,
            ),
        )
    }
    // The short outer display has room for the last two bubbles; the rest would be cut mid-bubble.
    if (ctx.device == StoreDevice.DUO_OUTER) messages = messages.takeLast(2)
    // The warning comes from the app's own rules, so the demo text has to trip them.
    val flagged = messages.filter { !it.isSent && ScamSignals.detect(it.text).isNotEmpty() }.map { it.id }.toSet()
    check(scam == flagged.isNotEmpty()) { "scam demo text for ${ctx.lang} does not match ScamSignals" }

    ConversationScreen(
        state = ConversationContract.State(
            title = listing.title,
            imageUrl = listing.primaryImageUrl,
            status = listing.status,
            listing = listing,
            messages = messages,
            isLoading = false,
            hasLoaded = true,
            accountName = ctx.seller.account,
            olxCountry = ctx.lang.country,
            flaggedMessageIds = flagged,
            suggestions = suggestions,
        ),
        snackbarHostState = remember { SnackbarHostState() },
        onBack = {},
        onEvent = {},
    )
}

private fun StoreRenderContext.threadsState(): MessagesContract.State {
    val adverts = adverts()
    val sneakers = adverts[0]
    val bike = adverts[1]
    val guitar = adverts[2]
    val camera = adverts[4]
    val now = Clock.System.now()

    fun row(
        threadId: Long,
        advertId: Long,
        title: String,
        photo: String,
        status: AdvertStatus,
        unread: Int,
        startedAgo: Duration,
    ) = MessagesContract.ThreadRow(
        threadId = threadId,
        advertId = advertId,
        title = title,
        imageUrl = demoPhoto(photo),
        status = status,
        isListingDeleted = false,
        unreadCount = unread,
        createdAt = (now - startedAgo).olxWallClock(zone()),
    )

    val threads = listOf(
        row(1, GuitarId, guitar.title, "guitar", AdvertStatus.Active, unread = 1, startedAgo = 12.minutes),
        row(2, BikeId, bike.title, "bike", AdvertStatus.Active, unread = 2, startedAgo = 55.minutes),
        row(3, SneakersId, sneakers.title, "sneakers_1", AdvertStatus.Active, unread = 1, startedAgo = 3.hours),
        row(4, 2001, seller.jacket, "jacket", AdvertStatus.Active, unread = 0, startedAgo = 7.hours),
        row(5, CameraId, camera.title, "camera", AdvertStatus.Active, unread = 0, startedAgo = 1.days + 4.hours),
        row(6, 2002, seller.coffee, "coffee", AdvertStatus.RemovedByUser, unread = 0, startedAgo = 4.days),
    )
    val firstPage = MessagesContract.AccountPage(
        localIndex = 0,
        accountName = seller.account,
        isActiveAccount = true,
        needsReconnect = false,
        hasLoaded = true,
        threads = threads,
    )
    val secondPage = MessagesContract.AccountPage(
        localIndex = 1,
        accountName = seller.shop,
        isActiveAccount = false,
        needsReconnect = false,
        hasLoaded = true,
    )
    return MessagesContract.State(pages = listOf(firstPage, secondPage), selectedLocalIndex = 0)
}
