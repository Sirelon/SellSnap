package com.sirelon.sellsnap.features.seller.auth.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.sirelon.sellsnap.designsystem.AppDimens
import com.sirelon.sellsnap.designsystem.AppTheme
import com.sirelon.sellsnap.designsystem.buttons.AppButton
import com.sirelon.sellsnap.designsystem.buttons.AppButtonDefaults
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.continue_as_guest
import com.sirelon.sellsnap.generated.resources.guest_description
import com.sirelon.sellsnap.generated.resources.not_now
import com.sirelon.sellsnap.generated.resources.olx_login_closed_title
import org.jetbrains.compose.resources.stringResource

/**
 * Shown after the seller closes the OLX login (SIR-123): offers guest mode instead of switching to
 * it silently. Same sheet setup as `BottomSheetSceneStrategy` and the same layout as
 * `PreviewBackInfoSheet`. Swiping it away counts as "Not now".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OlxLoginClosedSheet(
    onContinueAsGuest: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppTheme.colors.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("olx_login_closed_sheet")
                .padding(horizontal = AppDimens.Spacing.xl4)
                .padding(bottom = AppDimens.Spacing.xl5),
            verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl3),
        ) {
            Text(
                text = stringResource(Res.string.olx_login_closed_title),
                style = AppTheme.typography.headline,
                color = AppTheme.colors.onBackground,
            )
            Text(
                text = stringResource(Res.string.guest_description),
                style = AppTheme.typography.body,
                color = AppTheme.colors.onSurfaceMuted,
            )
            AppButton(
                modifier = Modifier.fillMaxWidth().testTag("olx_login_closed_continue_as_guest"),
                style = AppButtonDefaults.secondary(),
                text = stringResource(Res.string.continue_as_guest),
                onClick = onContinueAsGuest,
            )
            AppButton(
                modifier = Modifier.fillMaxWidth(),
                style = AppButtonDefaults.outline(),
                text = stringResource(Res.string.not_now),
                onClick = onDismiss,
            )
        }
    }
}
