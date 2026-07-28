package com.sirelon.sellsnap.designsystem

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter

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
