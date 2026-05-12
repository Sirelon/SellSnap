package com.sirelon.sellsnap.features.seller.ad.publish_success

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.sirelon.sellsnap.designsystem.AppAsyncImage
import com.sirelon.sellsnap.designsystem.AppCard
import com.sirelon.sellsnap.designsystem.AppDimens
import com.sirelon.sellsnap.designsystem.AppScaffold
import com.sirelon.sellsnap.designsystem.AppTheme
import com.sirelon.sellsnap.designsystem.performSuccessFeedback
import com.sirelon.sellsnap.designsystem.buttons.AppButton
import com.sirelon.sellsnap.designsystem.buttons.AppButtonDefaults
import com.sirelon.sellsnap.designsystem.templates.TitleWithSubtitle
import com.sirelon.sellsnap.features.seller.ad.preview_ad.CopyPill
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.ic_arrow_right
import com.sirelon.sellsnap.generated.resources.ic_camera
import com.sirelon.sellsnap.generated.resources.ic_circle_check_big
import com.sirelon.sellsnap.generated.resources.publish_success_create_another
import com.sirelon.sellsnap.generated.resources.publish_success_listing_url_label
import com.sirelon.sellsnap.generated.resources.publish_success_status_label
import com.sirelon.sellsnap.generated.resources.publish_success_status_limited
import com.sirelon.sellsnap.generated.resources.publish_success_status_limited_caption
import com.sirelon.sellsnap.generated.resources.publish_success_status_new
import com.sirelon.sellsnap.generated.resources.publish_success_status_new_caption
import com.sirelon.sellsnap.generated.resources.publish_success_status_unknown
import com.sirelon.sellsnap.generated.resources.publish_success_status_unknown_caption
import com.sirelon.sellsnap.generated.resources.publish_success_subtitle
import com.sirelon.sellsnap.generated.resources.publish_success_subtitle_limited
import com.sirelon.sellsnap.generated.resources.publish_success_subtitle_new
import com.sirelon.sellsnap.generated.resources.publish_success_title
import com.sirelon.sellsnap.generated.resources.publish_success_total_time_caption
import com.sirelon.sellsnap.generated.resources.publish_success_total_time_label
import com.sirelon.sellsnap.generated.resources.publish_success_view_on_olx
import com.sirelon.sellsnap.generated.resources.time_unit_minutes_seconds
import com.sirelon.sellsnap.generated.resources.time_unit_seconds
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.sin
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val ConfettiPiecesCount = 24
private const val ConfettiDurationMs = 2500

@Composable
fun PublishSuccessScreen(
    data: PublishSuccessData,
    onViewOnOlx: () -> Unit,
    onCreateAnother: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hapticFeedback = LocalHapticFeedback.current

    LaunchedEffect(Unit) {
        hapticFeedback.performSuccessFeedback()
    }

    AppScaffold(
        modifier = modifier,
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .background(AppTheme.colors.background)
                    .padding(horizontal = AppDimens.Spacing.xl3)
                    .padding(bottom = AppDimens.Spacing.m, top = AppDimens.Spacing.m),
                verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.m),
            ) {
                AppButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(Res.string.publish_success_view_on_olx),
                    style = AppButtonDefaults.success(),
                    trailingIcon = painterResource(Res.drawable.ic_arrow_right),
                    enabled = data.url.isNotBlank(),
                    onClick = onViewOnOlx,
                )
                AppButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(Res.string.publish_success_create_another),
                    style = AppButtonDefaults.secondary(),
                    onClick = onCreateAnother,
                )
            }
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppTheme.colors.background)
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues),
        ) {
            ConfettiCanvas(Modifier.fillMaxSize())
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = AppDimens.Spacing.xl3)
                    .padding(top = AppDimens.Spacing.xl8, bottom = AppDimens.Spacing.xl8),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl6),
            ) {
                SuccessHero()
                AnimatedTitle(status = data.status)
                ListingSummaryCard(
                    title = data.title,
                    priceFormatted = data.priceFormatted,
                    primaryImageUrl = data.primaryImageUrl,
                    url = data.url,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.Spacing.m),
                ) {
                    val (statusValue, statusCaption) = advertStatusText(data.status)
                    StatMiniCard(
                        modifier = Modifier.weight(1f),
                        value = statusValue,
                        label = stringResource(Res.string.publish_success_status_label),
                        caption = statusCaption,
                    )
                    StatMiniCard(
                        modifier = Modifier.weight(1f),
                        value = formatElapsedTime(data.totalElapsedMs),
                        label = stringResource(Res.string.publish_success_total_time_label),
                        caption = stringResource(Res.string.publish_success_total_time_caption),
                    )
                }
                Spacer(Modifier.size(AppDimens.Size.xl3))
            }
        }
    }
}

@Composable
private fun advertStatusText(status: AdvertStatus): Pair<String, String> = when (status) {
    AdvertStatus.New -> stringResource(Res.string.publish_success_status_new) to
            stringResource(Res.string.publish_success_status_new_caption)
    AdvertStatus.Limited -> stringResource(Res.string.publish_success_status_limited) to
            stringResource(Res.string.publish_success_status_limited_caption)
    AdvertStatus.Unknown -> stringResource(Res.string.publish_success_status_unknown) to
            stringResource(Res.string.publish_success_status_unknown_caption)
}

@Composable
private fun formatElapsedTime(totalElapsedMs: Long): String {
    val duration = elapsedDuration(totalElapsedMs)
    if (duration < 60.seconds) {
        return stringResource(Res.string.time_unit_seconds, duration.inWholeSeconds.toInt())
    }

    return stringResource(
        Res.string.time_unit_minutes_seconds,
        duration.inWholeMinutes.toInt(),
        (duration.inWholeSeconds % 60L).toInt(),
    )
}

@ReadOnlyComposable
@Composable
private fun elapsedDuration(totalElapsedMs: Long): Duration =
    (((totalElapsedMs.coerceAtLeast(0L)) + 999L) / 1000L).seconds

@Composable
private fun ConfettiCanvas(modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    var visible by remember { mutableStateOf(true) }
    val colors = listOf(
        AppTheme.colors.success,
        AppTheme.colors.primary,
        AppTheme.colors.primaryBright,
        AppTheme.colors.warningVariant,
        AppTheme.colors.error,
        Color(0xFF38BDF8),
    )

    LaunchedEffect(Unit) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = ConfettiDurationMs, easing = LinearEasing),
        )
        visible = false
    }

    if (!visible) return

    Canvas(modifier = modifier) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        repeat(ConfettiPiecesCount) { index ->
            val delayFraction = (index % 8) * 0.055f
            val localProgress = ((progress.value - delayFraction) / (1f - delayFraction))
                .coerceIn(0f, 1f)
            if (localProgress <= 0f || localProgress >= 1f) return@repeat

            val eased = FastOutSlowInEasing.transform(localProgress)
            val squareSize = (8 + (index % 4) * 2).dp.toPx()
            val x = canvasWidth * ((index * 37 % 100) / 100f) +
                    sin((localProgress * 5f + index) * 1.7f) * 28.dp.toPx()
            val y = -squareSize + (canvasHeight + squareSize * 2f) * eased
            val alpha = if (localProgress > 0.82f) {
                ((1f - localProgress) / 0.18f).coerceIn(0f, 1f)
            } else {
                1f
            }
            val pivot = Offset(x + squareSize / 2f, y + squareSize / 2f)

            rotate(
                degrees = index * 17f + localProgress * 220f,
                pivot = pivot,
            ) {
                drawRect(
                    color = colors[index % colors.size].copy(alpha = alpha),
                    topLeft = Offset(x, y),
                    size = Size(squareSize, squareSize),
                )
            }
        }
    }
}

@Composable
private fun SuccessHero(modifier: Modifier = Modifier) {
    var entered by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.72f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "PublishSuccess.heroScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(220),
        label = "PublishSuccess.heroAlpha",
    )

    LaunchedEffect(Unit) {
        entered = true
    }

    Box(
        modifier = modifier
            .size(120.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .clip(CircleShape)
            .background(AppTheme.colors.success),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_circle_check_big),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(AppDimens.Size.xl15),
        )
    }
}

@Composable
private fun AnimatedTitle(status: AdvertStatus, modifier: Modifier = Modifier) {
    var entered by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 360, delayMillis = 120),
        label = "PublishSuccess.titleAlpha",
    )
    val offsetY by animateDpAsState(
        targetValue = if (entered) 0.dp else AppDimens.Spacing.xl3,
        animationSpec = tween(durationMillis = 360, delayMillis = 120),
        label = "PublishSuccess.titleOffset",
    )

    LaunchedEffect(Unit) {
        delay(80L.milliseconds)
        entered = true
    }

    val subtitle = when (status) {
        AdvertStatus.New -> stringResource(Res.string.publish_success_subtitle_new)
        AdvertStatus.Limited -> stringResource(Res.string.publish_success_subtitle_limited)
        AdvertStatus.Unknown -> stringResource(Res.string.publish_success_subtitle)
    }
    TitleWithSubtitle(
        title = stringResource(Res.string.publish_success_title),
        subtitle = subtitle,
        modifier = modifier.graphicsLayer {
            this.alpha = alpha
            translationY = offsetY.toPx()
        },
    )
}

@Composable
private fun ListingSummaryCard(
    title: String,
    priceFormatted: String,
    primaryImageUrl: String?,
    url: String,
    modifier: Modifier = Modifier,
) {
    val displayUrl = remember(url) { url.toDisplayUrl() }
    AppCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimens.Spacing.xl3),
            verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl3),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ListingThumbnail(primaryImageUrl = primaryImageUrl)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xs),
                ) {
                    Text(
                        text = title,
                        style = AppTheme.typography.title,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = priceFormatted,
                        style = AppTheme.typography.headline,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xs)) {
                Text(
                    text = stringResource(Res.string.publish_success_listing_url_label),
                    style = AppTheme.typography.caption,
                    color = AppTheme.colors.onSurfaceMuted,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(AppDimens.BorderRadius.xl))
                        .background(AppTheme.colors.surfaceLow)
                        .padding(AppDimens.Spacing.m),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.Spacing.m),
                ) {
                    Text(
                        text = displayUrl,
                        modifier = Modifier.weight(1f),
                        style = AppTheme.typography.caption.copy(fontFamily = FontFamily.Monospace),
                        color = AppTheme.colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    CopyPill(value = url.ifBlank { displayUrl })
                }
            }
        }
    }
}

@Composable
private fun ListingThumbnail(
    primaryImageUrl: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(AppDimens.Size.xl14)
            .clip(RoundedCornerShape(AppDimens.BorderRadius.xl2))
            .background(AppTheme.colors.surfaceLow),
        contentAlignment = Alignment.Center,
    ) {
        if (primaryImageUrl != null) {
            AppAsyncImage(
                model = primaryImageUrl,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                painter = painterResource(Res.drawable.ic_camera),
                contentDescription = null,
                tint = AppTheme.colors.onSurfaceMuted,
                modifier = Modifier.size(AppDimens.Size.xl8),
            )
        }
    }
}

@Composable
private fun StatMiniCard(
    value: String,
    label: String,
    caption: String,
    modifier: Modifier = Modifier,
) {
    AppCard(
        modifier = modifier.heightIn(min = 116.dp),
        containerColor = AppTheme.colors.surfaceLowest,
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimens.Spacing.xl3),
            verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xs),
        ) {
            Text(
                text = value,
                style = AppTheme.typography.headline,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.success,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = label,
                style = AppTheme.typography.label,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = caption,
                style = AppTheme.typography.caption,
                color = AppTheme.colors.onSurfaceMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun String.toDisplayUrl(): String {
    if (isBlank()) return "olx.ua/o/XXXX-XXXX"
    return removePrefix("https://")
        .removePrefix("http://")
        .removePrefix("www.")
}

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
