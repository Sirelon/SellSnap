package com.sirelon.sellsnap.features.seller.ad.generate_ad

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.sirelon.sellsnap.designsystem.AppBackgroundGradient
import com.sirelon.sellsnap.designsystem.AppCard
import com.sirelon.sellsnap.designsystem.AppDimens
import com.sirelon.sellsnap.designsystem.AppTheme
import com.sirelon.sellsnap.designsystem.PulsingCircles
import com.sirelon.sellsnap.designsystem.performStepFeedback
import com.sirelon.sellsnap.designsystem.performSuccessFeedback
import com.sirelon.sellsnap.designsystem.templates.TitleWithSubtitle
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.ai_analyzing_photo
import com.sirelon.sellsnap.generated.resources.ai_creating_ad_title
import com.sirelon.sellsnap.generated.resources.ai_processing_status_done
import com.sirelon.sellsnap.generated.resources.ai_processing_status_in_progress
import com.sirelon.sellsnap.generated.resources.ai_processing_tip_capture_details
import com.sirelon.sellsnap.generated.resources.ai_processing_tip_connect_olx_faster
import com.sirelon.sellsnap.generated.resources.ai_processing_tip_good_lighting
import com.sirelon.sellsnap.generated.resources.ai_processing_tip_keep_background_clean
import com.sirelon.sellsnap.generated.resources.ai_step_analyzing_image
import com.sirelon.sellsnap.generated.resources.ai_step_calculating_price
import com.sirelon.sellsnap.generated.resources.ai_step_generating_title
import com.sirelon.sellsnap.generated.resources.ai_step_preparing_guest_ad
import com.sirelon.sellsnap.generated.resources.ai_step_uploading_photos
import com.sirelon.sellsnap.generated.resources.ai_step_writing_description
import com.sirelon.sellsnap.generated.resources.ic_check
import com.sirelon.sellsnap.generated.resources.ic_sparkles
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

private enum class ProcessingStepStatus {
    Pending,
    Active,
    Complete,
}

@Composable
private fun processingSteps(isGuestMode: Boolean) = if (isGuestMode) {
    listOf(
        stringResource(Res.string.ai_step_uploading_photos),
        stringResource(Res.string.ai_step_analyzing_image),
        stringResource(Res.string.ai_step_preparing_guest_ad),
    )
} else {
    listOf(
        stringResource(Res.string.ai_step_uploading_photos),
        stringResource(Res.string.ai_step_analyzing_image),
        stringResource(Res.string.ai_step_generating_title),
        stringResource(Res.string.ai_step_writing_description),
        stringResource(Res.string.ai_step_calculating_price),
    )
}

@Composable
private fun processingTips(isGuestMode: Boolean) = buildList {
    if (isGuestMode) {
        add(stringResource(Res.string.ai_processing_tip_connect_olx_faster))
    }
    add(stringResource(Res.string.ai_processing_tip_good_lighting))
    add(stringResource(Res.string.ai_processing_tip_capture_details))
    add(stringResource(Res.string.ai_processing_tip_keep_background_clean))
}

@Composable
fun AiProcessingScreen(
    completedSteps: Int,
    isGuestMode: Boolean,
    modifier: Modifier = Modifier,
) {
    AppBackgroundGradient(modifier = modifier.fillMaxSize()) {
        AiProcessingContent(
            completedSteps = completedSteps,
            isGuestMode = isGuestMode,
        )
    }
}

@Composable
private fun AiProcessingContent(
    completedSteps: Int,
    isGuestMode: Boolean,
    modifier: Modifier = Modifier,
) {
    val steps = processingSteps(isGuestMode = isGuestMode)
    val tips = processingTips(isGuestMode = isGuestMode)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = AppDimens.Spacing.xl6),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            PulsingCircles {
                SpinningIcon()
            }
            BouncingBadge(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-4).dp, y = (-4).dp),
            )
        }

        Spacer(modifier = Modifier.height(AppDimens.Spacing.xl8))

        TitleWithSubtitle(
            title = stringResource(Res.string.ai_creating_ad_title),
            subtitle = stringResource(Res.string.ai_analyzing_photo),
        )

        Spacer(modifier = Modifier.height(AppDimens.Spacing.xl6))

        ProcessingStepsList(
            steps = steps,
            completedSteps = completedSteps,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = AppDimens.Size.xl24),
        )

        Spacer(modifier = Modifier.height(AppDimens.Spacing.xl3))

        ProcessingTipCard(
            tips = tips,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = AppDimens.Size.xl24),
        )
    }
}

@Composable
private fun SpinningIcon(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "heroIcon")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000),
        ),
        label = "heroRotation",
    )

    Icon(
        painter = painterResource(Res.drawable.ic_sparkles),
        contentDescription = null,
        modifier = modifier
            .size(AppDimens.Size.xl8)
            .graphicsLayer { rotationZ = rotation },
        tint = AppTheme.colors.onPrimary,
    )
}

@Composable
private fun BouncingBadge(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "heroBadge")
    val offsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "badgeOffset",
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                translationY = offsetY
            }
            .size(AppDimens.Size.xl8)
            .background(AppTheme.colors.success, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.FlashOn,
            contentDescription = null,
            modifier = Modifier.size(AppDimens.Size.xl3),
            tint = AppTheme.colors.onPrimary,
        )
    }
}

@Composable
private fun ProcessingStepsList(
    steps: List<String>,
    completedSteps: Int,
    modifier: Modifier = Modifier,
) {
    val activeSubtitle = stringResource(Res.string.ai_processing_status_in_progress)
    val completeSubtitle = stringResource(Res.string.ai_processing_status_done)
    val clampedCompletedSteps = completedSteps.coerceAtLeast(0)
    val hapticFeedback = LocalHapticFeedback.current

    LaunchedEffect(clampedCompletedSteps) {
        if (clampedCompletedSteps <= 0) return@LaunchedEffect
        if (clampedCompletedSteps >= steps.size) {
            hapticFeedback.performSuccessFeedback()
        } else {
            hapticFeedback.performStepFeedback()
        }
    }

    AppCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.Spacing.xl3, vertical = AppDimens.Spacing.xl2)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xs),
        ) {
            steps.forEachIndexed { index, stepText ->
                val status = when {
                    index < clampedCompletedSteps -> ProcessingStepStatus.Complete
                    index == clampedCompletedSteps && clampedCompletedSteps < steps.size -> ProcessingStepStatus.Active
                    else -> ProcessingStepStatus.Pending
                }

                ProcessingStepItem(
                    text = stepText,
                    status = status,
                    subtitle = when (status) {
                        ProcessingStepStatus.Active -> activeSubtitle
                        ProcessingStepStatus.Complete -> completeSubtitle
                        ProcessingStepStatus.Pending -> null
                    },
                )
            }
        }
    }
}

@Composable
private fun ProcessingStepItem(
    text: String,
    status: ProcessingStepStatus,
    subtitle: String?,
    modifier: Modifier = Modifier,
) {
    val titleColor by animateColorAsState(
        targetValue = when (status) {
            ProcessingStepStatus.Active -> AppTheme.colors.onSurface
            ProcessingStepStatus.Complete -> AppTheme.colors.onSurfaceSoft
            ProcessingStepStatus.Pending -> AppTheme.colors.onSurfaceMuted
        },
        animationSpec = tween(durationMillis = 250),
        label = "stepTitleColor",
    )
    val subtitleColor by animateColorAsState(
        targetValue = when (status) {
            ProcessingStepStatus.Active -> AppTheme.colors.primary
            ProcessingStepStatus.Complete -> AppTheme.colors.onSurfaceSoft
            ProcessingStepStatus.Pending -> Color.Transparent
        },
        animationSpec = tween(durationMillis = 250),
        label = "stepSubtitleColor",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .padding(horizontal = AppDimens.Spacing.xs, vertical = AppDimens.Spacing.xl),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl2),
    ) {
        StepStatusIndicator(status = status)

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xs2),
        ) {
            Text(
                text = text,
                style = AppTheme.typography.body,
                fontWeight = if (status == ProcessingStepStatus.Active) FontWeight.SemiBold else FontWeight.Medium,
                color = titleColor,
            )

            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = AppTheme.typography.caption,
                    color = subtitleColor,
                )
            }
        }
    }
}

@Composable
private fun StepStatusIndicator(
    status: ProcessingStepStatus,
    modifier: Modifier = Modifier,
) {
    val backgroundColor by animateColorAsState(
        targetValue = when (status) {
            ProcessingStepStatus.Complete -> AppTheme.colors.success
            ProcessingStepStatus.Active -> AppTheme.colors.primary.copy(alpha = 0.12f)
            ProcessingStepStatus.Pending -> Color.Transparent
        },
        animationSpec = tween(durationMillis = 250),
        label = "statusBackground",
    )
    val borderColor by animateColorAsState(
        targetValue = when (status) {
            ProcessingStepStatus.Pending -> AppTheme.colors.primary.copy(alpha = 0.4f)
            ProcessingStepStatus.Active -> AppTheme.colors.primary.copy(alpha = 0.18f)
            ProcessingStepStatus.Complete -> Color.Transparent
        },
        animationSpec = tween(durationMillis = 250),
        label = "statusBorder",
    )

    Box(
        modifier = modifier
            .size(AppDimens.Size.xl9)
            .background(backgroundColor, CircleShape)
            .border(
                width = AppDimens.BorderWidth.m,
                color = borderColor,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        when (status) {
            ProcessingStepStatus.Complete -> {
                Icon(
                    painter = painterResource(Res.drawable.ic_check),
                    contentDescription = null,
                    modifier = Modifier.size(AppDimens.Size.xl2),
                    tint = AppTheme.colors.onPrimary,
                )
            }

            ProcessingStepStatus.Active -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(AppDimens.Size.xl5),
                    color = AppTheme.colors.primary,
                    strokeWidth = AppDimens.BorderWidth.l,
                )
            }

            ProcessingStepStatus.Pending -> Unit
        }
    }
}

@Composable
private fun ProcessingTipCard(
    tips: List<String>,
    modifier: Modifier = Modifier,
) {
    val isInspectionMode = LocalInspectionMode.current
    var tipIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(tips, isInspectionMode) {
        if (isInspectionMode || tips.isEmpty()) {
            tipIndex = 0
            return@LaunchedEffect
        }

        while (true) {
            delay(Random.nextLong(3_000, 6_000).milliseconds)
            tipIndex = (tipIndex + 1) % tips.size
        }
    }

    if (tips.isEmpty()) return

    AppCard(
        modifier = modifier,
        containerColor = AppTheme.colors.primary.copy(alpha = 0.08f),
    ) {
        Text(
            text = tips[tipIndex.coerceIn(0, tips.lastIndex)],
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.Spacing.xl3, vertical = AppDimens.Spacing.xl2),
            style = AppTheme.typography.caption,
            color = AppTheme.colors.onSurface,
        )
    }
}

@PreviewLightDark
@Composable
private fun AiProcessingContentStartPreview() {
    AiProcessingContentPreview(completedSteps = 0)
}

@PreviewLightDark
@Composable
private fun AiProcessingContentMiddlePreview() {
    AiProcessingContentPreview(completedSteps = 2)
}

@PreviewLightDark
@Composable
private fun AiProcessingContentCompletePreview() {
    AiProcessingContentPreview(completedSteps = 5)
}

@Composable
private fun AiProcessingContentPreview(
    completedSteps: Int,
    isGuestMode: Boolean = false,
) {
    AppTheme {
        AiProcessingScreen(
            completedSteps = completedSteps,
            isGuestMode = isGuestMode,
        )
    }
}
