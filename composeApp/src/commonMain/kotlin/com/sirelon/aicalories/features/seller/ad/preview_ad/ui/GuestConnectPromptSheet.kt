package com.sirelon.sellsnap.features.seller.ad.preview_ad.ui

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
import com.sirelon.sellsnap.generated.resources.guest_connect_prompt_body
import com.sirelon.sellsnap.generated.resources.guest_connect_prompt_connect
import com.sirelon.sellsnap.generated.resources.guest_connect_prompt_title
import com.sirelon.sellsnap.generated.resources.not_now
import org.jetbrains.compose.resources.stringResource

/**
 * One-time nudge for a guest who has copied three generated listings out by hand
 * (`ReviewPromptCoordinator.shouldNudgeGuestToConnect`): the moment copy-and-paste has become a
 * habit is the moment one-tap publishing is worth a login. Same sheet setup and layout as
 * `OlxLoginClosedSheet`. Swiping it away is reported as `dismissed`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuestConnectPromptSheet(
    onConnect: () -> Unit,
    onNotNow: () -> Unit,
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
                .testTag("guest_connect_prompt_sheet")
                .padding(horizontal = AppDimens.Spacing.xl4)
                .padding(bottom = AppDimens.Spacing.xl5),
            verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl3),
        ) {
            Text(
                text = stringResource(Res.string.guest_connect_prompt_title),
                style = AppTheme.typography.headline,
                color = AppTheme.colors.onBackground,
            )
            Text(
                text = stringResource(Res.string.guest_connect_prompt_body),
                style = AppTheme.typography.body,
                color = AppTheme.colors.onSurfaceMuted,
            )
            AppButton(
                modifier = Modifier.fillMaxWidth().testTag("guest_connect_prompt_connect"),
                text = stringResource(Res.string.guest_connect_prompt_connect),
                onClick = onConnect,
            )
            AppButton(
                modifier = Modifier.fillMaxWidth(),
                style = AppButtonDefaults.outline(),
                text = stringResource(Res.string.not_now),
                onClick = onNotNow,
            )
        }
    }
}
