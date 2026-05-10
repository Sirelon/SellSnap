package com.sirelon.sellsnap.features.seller.onboarding

import androidx.compose.animation.animateBounds
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.sirelon.sellsnap.designsystem.AppDimens
import com.sirelon.sellsnap.designsystem.AppScaffold
import com.sirelon.sellsnap.designsystem.AppTheme
import com.sirelon.sellsnap.designsystem.buttons.AppButton
import com.sirelon.sellsnap.designsystem.buttons.AppButtonDefaults
import com.sirelon.sellsnap.designsystem.buttons.AppIconButton
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.get_started
import com.sirelon.sellsnap.generated.resources.ic_arrow_left
import com.sirelon.sellsnap.generated.resources.ic_arrow_right
import com.sirelon.sellsnap.generated.resources.img_seller_onboarding_ai
import com.sirelon.sellsnap.generated.resources.img_seller_onboarding_publish
import com.sirelon.sellsnap.generated.resources.img_seller_onboarding_snap
import com.sirelon.sellsnap.generated.resources.next
import com.sirelon.sellsnap.generated.resources.onboarding_step1_subtitle
import com.sirelon.sellsnap.generated.resources.onboarding_step1_title
import com.sirelon.sellsnap.generated.resources.onboarding_step2_subtitle
import com.sirelon.sellsnap.generated.resources.onboarding_step2_title
import com.sirelon.sellsnap.generated.resources.onboarding_step3_subtitle
import com.sirelon.sellsnap.generated.resources.onboarding_step3_title
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private sealed interface OnboardingStepIcon {
    data object Snap : OnboardingStepIcon
    data object AiWrites : OnboardingStepIcon
    data object Done : OnboardingStepIcon
}

private data class OnboardingItem(
    val title: String,
    val subtitle: String,
    val icon: OnboardingStepIcon,
)

@Composable
private fun onboardingItems() = listOf(
    OnboardingItem(
        title = stringResource(Res.string.onboarding_step1_title),
        subtitle = stringResource(Res.string.onboarding_step1_subtitle),
        icon = OnboardingStepIcon.Snap,
    ),
    OnboardingItem(
        title = stringResource(Res.string.onboarding_step2_title),
        subtitle = stringResource(Res.string.onboarding_step2_subtitle),
        icon = OnboardingStepIcon.AiWrites,
    ),
    OnboardingItem(
        title = stringResource(Res.string.onboarding_step3_title),
        subtitle = stringResource(Res.string.onboarding_step3_subtitle),
        icon = OnboardingStepIcon.Done,
    ),
)

@Composable
fun OnboardingScreen(onClose: () -> Unit) {
    val items = onboardingItems()
    val state = rememberPagerState { items.size }
    AppScaffold(
        bottomBar = {
            BottomButtons(state = state, onClose = onClose)
        }
    ) { paddingValues ->
        HorizontalPager(
            state = state,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues),
        ) {
            OnboardingPage(items[it])
        }
    }
}

@Composable
private fun OnboardingPage(item: OnboardingItem) {
    Column(
        modifier = Modifier.fillMaxSize().padding(AppDimens.Spacing.xl3),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(
            AppDimens.Spacing.xl3,
            Alignment.CenterVertically,
        ),
    ) {
        FadeInUp(durationMs = 380) {
            OnboardingStepIconContent(item.icon)
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl),
        ) {
            FadeInUp(durationMs = 460) {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = item.title,
                    style = AppTheme.typography.headline,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.onBackground,
                    textAlign = TextAlign.Center,
                )
            }
            FadeInUp(durationMs = 540) {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = item.subtitle,
                    style = AppTheme.typography.title,
                    color = AppTheme.colors.onSurfaceSoft,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun FadeInUp(
    durationMs: Int,
    translation: Dp = AppDimens.Spacing.xl3,
    content: @Composable () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = durationMs,
                easing = FastOutSlowInEasing,
            ),
        )
    }
    val translationPx = with(LocalDensity.current) { translation.toPx() }
    Box(
        modifier = Modifier
            .graphicsLayer {
                alpha = progress.value
                translationY = (1f - progress.value) * translationPx
            },
    ) {
        content()
    }
}

@Composable
private fun OnboardingStepIconContent(icon: OnboardingStepIcon) {
    val shape = RoundedCornerShape(AppDimens.BorderRadius.xl11)
    Image(
        painter = painterResource(
            when (icon) {
                OnboardingStepIcon.Snap -> Res.drawable.img_seller_onboarding_snap
                OnboardingStepIcon.AiWrites -> Res.drawable.img_seller_onboarding_ai
                OnboardingStepIcon.Done -> Res.drawable.img_seller_onboarding_publish
            },
        ),
        contentDescription = null,
        modifier = Modifier
            .size(AppDimens.Size.xl23)
            .shadow(
                elevation = AppDimens.Spacing.xl5,
                shape = shape,
                ambientColor = AppTheme.colors.primary,
                spotColor = AppTheme.colors.primary,
            )
            .clip(shape),
        contentScale = ContentScale.Crop,
    )
}

@Composable
private fun BottomButtons(
    state: PagerState,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    LookaheadScope {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimens.Spacing.xl)
                .animateBounds(this)
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl3),
        ) {
            if (state.currentPage > 0) {
                AppIconButton(
                    icon = painterResource(Res.drawable.ic_arrow_left),
                    onClick = {
                        scope.launch {
                            state.animateScrollToPage(state.currentPage - 1)
                        }
                    },
                )
            }

            val lastPage = state.currentPage == state.pageCount - 1
            val text = if (lastPage) {
                stringResource(Res.string.get_started)
            } else {
                stringResource(Res.string.next)
            }

            AppButton(
                modifier = Modifier.fillMaxWidth(),
                text = text,
                style = AppButtonDefaults.secondary(),
                onClick = {
                    if (lastPage) {
                        onClose()
                    } else {
                        scope.launch {
                            state.animateScrollToPage(state.currentPage + 1)
                        }
                    }
                },
                trailingIcon = painterResource(Res.drawable.ic_arrow_right),
            )
        }
    }
}

@Preview
@Composable
private fun OnboardingScreenPreview() {
    AppTheme {
        OnboardingScreen {}
    }
}
