package com.sirelon.sellsnap.designsystem

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.ic_user
import org.jetbrains.compose.resources.painterResource

// Exposed only so screenshot flows can wait for a remote image to actually be on
// screen. `waitForAnimationToEnd` cannot serve that purpose: ImageShimmer runs an
// infinite transition while loading, so the wait never settles and just expires.
const val AsyncImageLoadedTestTag = "async_image_loaded"

@Composable
fun AppAsyncImage(
    model: Any?,
    modifier: Modifier = Modifier,
) {
    var isLoading by remember(model) { mutableStateOf(true) }
    var isLoaded by remember(model) { mutableStateOf(false) }

    Box {
        AsyncImage(
            model = model,
            contentDescription = null,
            // Tagged on Success only — an Error must not look like a rendered image.
            modifier = if (isLoaded) modifier.testTag(AsyncImageLoadedTestTag) else modifier,
            contentScale = ContentScale.Crop,
            onState = { state ->
                isLoading = state !is AsyncImagePainter.State.Success &&
                    state !is AsyncImagePainter.State.Error
                isLoaded = state is AsyncImagePainter.State.Success
            },
        )
        if (isLoading) {
            ImageShimmer(modifier = Modifier.matchParentSize())
        }
    }
}

/** Circular avatar with a remote-image / initial-letter / generic-icon fallback chain, shared
 * by any screen that shows a seller identity (Profile's account rows, the publish target row
 * and its account picker). [useGradientBackground] picks between the larger "hero" identity
 * treatment and the flatter chip treatment used in compact rows/lists. */
@Composable
fun AppAvatar(
    avatarUrl: String?,
    fallbackInitial: String?,
    modifier: Modifier = Modifier,
    size: Dp = AppDimens.Size.xl14,
    useGradientBackground: Boolean = true,
    initialStyle: TextStyle = AppTheme.typography.title,
) {
    val primaryBright = AppTheme.colors.primaryBright
    val primary = AppTheme.colors.primary
    val surfaceLow = AppTheme.colors.surfaceLow
    val background = remember(useGradientBackground, primaryBright, primary, surfaceLow) {
        if (useGradientBackground) Brush.linearGradient(listOf(primaryBright, primary)) else SolidColor(surfaceLow)
    }
    val contentColor = if (useGradientBackground) AppTheme.colors.onPrimary else AppTheme.colors.onSurfaceMuted

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            AppAsyncImage(
                model = avatarUrl,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (!fallbackInitial.isNullOrBlank()) {
            Text(
                text = fallbackInitial,
                style = initialStyle,
                fontWeight = FontWeight.Bold,
                color = contentColor,
            )
        } else {
            Icon(
                painter = painterResource(Res.drawable.ic_user),
                contentDescription = null,
                tint = contentColor,
            )
        }
    }
}

@Composable
private fun ImageShimmer(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val offset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer_offset",
    )

    val shimmerColors = listOf(
        AppTheme.colors.surfaceLow,
        AppTheme.colors.surfaceHigh,
        AppTheme.colors.surfaceLow,
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(offset - 400f, offset - 400f),
        end = Offset(offset + 400f, offset + 400f),
    )

    Box(modifier = modifier.background(brush))
}
