package com.sirelon.sellsnap.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.time.Clock

private const val COMPLETE_PERCENTAGE = 100.0

@Composable
fun BoxScope.UploadStatusIndicator(
    progress: Double,
) {
    if (progress >= COMPLETE_PERCENTAGE) return

    val percent = progress.coerceIn(0.0, COMPLETE_PERCENTAGE)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .align(Alignment.BottomCenter),
        color = AppTheme.colors.surfaceHigh.copy(alpha = 0.92f),
        contentColor = AppTheme.colors.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = AppDimens.Spacing.xl,
                    vertical = AppDimens.Spacing.m,
                ),
            verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.s),
        ) {
            LinearProgressIndicator(
                progress = { (percent / COMPLETE_PERCENTAGE).toFloat() },
                modifier = Modifier.fillMaxWidth(),
                color = AppTheme.colors.primary,
                trackColor = AppTheme.colors.surfaceHigh,
            )
            Text(
                text = "${percent.toInt()}%",
                style = AppTheme.typography.caption,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun PulseIndicator(
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val periodMs = 3600L
    val offsetsMs = longArrayOf(0L, 1200L, 2400L)

    val startNs = remember { Clock.System.now().toEpochMilliseconds() }
    var frameTimeNs by remember { mutableLongStateOf(startNs) }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { now -> frameTimeNs = now }
        }
    }

    fun phase(offsetMs: Long): Float {
        val elapsedMs = (frameTimeNs - startNs) / 1_000_000L + offsetMs
        return ((elapsedMs % periodMs).toFloat() / periodMs.toFloat())
    }

    Box(modifier.size(80.dp), contentAlignment = Alignment.Center) {
        @Composable
        fun Ring(p: Float) {
            val ringColor = AppTheme.colors.primary.copy(alpha = 0.9f)
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = 1f + 0.8f * p
                        scaleY = 1f + 0.8f * p
                        alpha = 1f - p
                    }
                    .border(1.5.dp, ringColor, CircleShape)
            )
        }

        Ring(phase(offsetsMs[0]))
        Ring(phase(offsetsMs[1]))
        Ring(phase(offsetsMs[2]))

        Box(
            Modifier
                .size(80.dp)
                .background(AppTheme.colors.onPrimary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = Modifier.size(32.dp)) {
                icon()
            }
        }
    }
}
