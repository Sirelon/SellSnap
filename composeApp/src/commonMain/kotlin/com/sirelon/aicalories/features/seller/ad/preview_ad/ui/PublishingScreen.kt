package com.sirelon.sellsnap.features.seller.ad.preview_ad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.sirelon.sellsnap.designsystem.AppDimens
import com.sirelon.sellsnap.designsystem.AppScaffold
import com.sirelon.sellsnap.designsystem.AppTheme
import com.sirelon.sellsnap.designsystem.performStepFeedback
import com.sirelon.sellsnap.designsystem.PulsingCircles
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.ic_share_2
import com.sirelon.sellsnap.generated.resources.publishing
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun PublishingScreen(modifier: Modifier = Modifier) {
    val hapticFeedback = LocalHapticFeedback.current
    LaunchedEffect(Unit) {
        hapticFeedback.performStepFeedback()
    }

    AppScaffold(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppTheme.colors.background),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                PulsingCircles {
                    Icon(
                        painter = painterResource(Res.drawable.ic_share_2),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(AppDimens.Size.xl6),
                    )
                }
                Spacer(modifier = Modifier.height(AppDimens.Spacing.xl4))
                Text(
                    text = stringResource(Res.string.publishing),
                    style = AppTheme.typography.title,
                    color = AppTheme.colors.onBackground,
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun PublishingScreenPreview() {
    AppTheme {
        Surface(color = AppTheme.colors.background) {
            PublishingScreen()
        }
    }
}
