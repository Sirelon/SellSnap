package com.sirelon.sellsnap.designsystem.buttons

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.sirelon.sellsnap.designsystem.AppDimens
import com.sirelon.sellsnap.designsystem.AppTheme

/**
 * Previews for every [AppButton] variant declared in [AppButtonDefaults].
 *
 * The stack demonstrates, top-to-bottom, the same set of variants described
 * in `Design/ClaudeDesign/project/ui.jsx`: primary → secondary → ghost →
 * outline → magic → success. Each variant is rendered twice — enabled and
 * disabled — so the press/disabled treatment is easy to eyeball.
 *
 * `@PreviewLightDark` renders the function twice (UI_MODE_NIGHT off and on)
 * so a single composable covers both themes — no need for separate light/dark
 * preview functions.
 */
@PreviewLightDark
@Composable
private fun AppButtonVariantsPreview() {
    AppTheme {
        AppButtonGallery()
    }
}

@Composable
private fun AppButtonGallery() {
    Column(
        modifier = Modifier
            .background(AppTheme.colors.background)
            .fillMaxWidth()
            .padding(AppDimens.Spacing.xl5),
        verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl3),
    ) {
        VariantSection(label = "primary") {
            AppButton(
                text = "Continue",
                onClick = {},
                style = AppButtonDefaults.primary(),
                modifier = Modifier.fillMaxWidth(),
            )
            AppButton(
                text = "Continue",
                onClick = {},
                style = AppButtonDefaults.primary(),
                enabled = false,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        VariantSection(label = "secondary") {
            AppButton(
                text = "Add photo",
                onClick = {},
                style = AppButtonDefaults.secondary(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        VariantSection(label = "ghost") {
            AppButton(
                text = "Skip for now",
                onClick = {},
                style = AppButtonDefaults.ghost(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        VariantSection(label = "outline (legacy)") {
            AppButton(
                text = "Continue as guest",
                onClick = {},
                style = AppButtonDefaults.outline(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        VariantSection(label = "magic") {
            AppButton(
                text = "Generate Ad with AI",
                onClick = {},
                style = AppButtonDefaults.magic(),
                modifier = Modifier.fillMaxWidth(),
            )
            AppButton(
                text = "Generate Ad with AI",
                onClick = {},
                style = AppButtonDefaults.magic(),
                enabled = false,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        VariantSection(label = "success") {
            AppButton(
                text = "Publish",
                onClick = {},
                style = AppButtonDefaults.success(),
                modifier = Modifier.fillMaxWidth(),
            )
            AppButton(
                text = "Publish",
                onClick = {},
                style = AppButtonDefaults.success(),
                enabled = false,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun VariantSection(
    label: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.m)) {
        Text(
            text = label,
            color = AppTheme.colors.onSurfaceMuted,
            fontWeight = FontWeight.SemiBold,
        )
        content()
    }
}
