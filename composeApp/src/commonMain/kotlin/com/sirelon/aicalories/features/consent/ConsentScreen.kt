package com.sirelon.sellsnap.features.consent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.sirelon.sellsnap.designsystem.AppDimens
import com.sirelon.sellsnap.designsystem.AppScaffold
import com.sirelon.sellsnap.designsystem.AppTheme
import com.sirelon.sellsnap.designsystem.buttons.AppButton
import com.sirelon.sellsnap.designsystem.buttons.AppButtonDefaults
import com.sirelon.sellsnap.designsystem.templates.TermsAndPrivacy
import com.sirelon.sellsnap.designsystem.templates.TitleWithSubtitle
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.consent_allow
import com.sirelon.sellsnap.generated.resources.consent_decline
import com.sirelon.sellsnap.generated.resources.consent_message
import com.sirelon.sellsnap.generated.resources.consent_title
import org.jetbrains.compose.resources.stringResource

/**
 * One-time opt-in prompt for analytics + crash reporting, shown after onboarding while consent is
 * [com.sirelon.sellsnap.startup.AnalyticsConsent.Undecided]. Collection stays off unless the user
 * taps "Allow"; either choice is changeable later from Profile.
 */
@Composable
fun ConsentScreen(
    onAllow: () -> Unit,
    onDecline: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenTerms: () -> Unit,
    isProcessing: Boolean = false,
) {
    AppScaffold(
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppDimens.Spacing.xl3)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl),
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(AppDimens.Size.xl6),
                        color = AppTheme.colors.primary,
                    )
                }
                AppButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(Res.string.consent_allow),
                    onClick = onAllow,
                    enabled = !isProcessing,
                )
                AppButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(Res.string.consent_decline),
                    style = AppButtonDefaults.ghost(),
                    onClick = onDecline,
                    enabled = !isProcessing,
                )
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppDimens.Spacing.xl3, vertical = AppDimens.Spacing.xl5),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl3),
        ) {
            TitleWithSubtitle(
                title = stringResource(Res.string.consent_title),
                subtitle = stringResource(Res.string.consent_message),
            )
            TermsAndPrivacy(onTermsClick = onOpenTerms, onPrivacyClick = onOpenPrivacy)
        }
    }
}

@Preview
@Composable
private fun ConsentScreenPreview() {
    AppTheme {
        ConsentScreen(onAllow = {}, onDecline = {}, onOpenPrivacy = {}, onOpenTerms = {})
    }
}
