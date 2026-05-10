package com.sirelon.sellsnap.designsystem.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.sirelon.sellsnap.designsystem.AppDimens
import com.sirelon.sellsnap.designsystem.PulseIndicator

@Composable
fun LoadingOverlay(
    isLoading: Boolean,
    content: @Composable () -> Unit
) {
    // Animate blur radius and overlay alpha
    val blurRadius by animateDpAsState(
        targetValue = if (isLoading) AppDimens.Size.s else 0.dp,
        animationSpec = tween(durationMillis = 300)
    )
    val overlayAlpha by animateFloatAsState(
        targetValue = if (isLoading) 0.5f else 0f,
        animationSpec = tween(durationMillis = 300)
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // 🔹 Main content with animated blur
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(blurRadius)
        ) {
            content()
        }

        // 🔹 Dimmed overlay + circular progress
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
//                    .drawBehind {
//                        drawRect(color = Color.Black.copy(alpha = overlayAlpha))
//                    }
                    // Block touches
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) awaitPointerEvent()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                FancyCircularLoader()
            }
        }
    }
}

@Composable
fun FancyCircularLoader() {
    PulseIndicator(
        icon = {
            CircularProgressIndicator()
        },
    )
}