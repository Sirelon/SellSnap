package com.sirelon.sellsnap.features.notifications.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.mohamedrejeb.calf.permissions.Notification
import com.mohamedrejeb.calf.permissions.Permission
import com.mohamedrejeb.calf.permissions.rememberPermissionState
import com.sirelon.sellsnap.designsystem.AppDimens
import com.sirelon.sellsnap.designsystem.AppTheme
import com.sirelon.sellsnap.designsystem.buttons.AppButton
import com.sirelon.sellsnap.designsystem.buttons.AppButtonDefaults
import com.sirelon.sellsnap.features.notifications.presentation.NotificationsPromptViewModel
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.not_now
import com.sirelon.sellsnap.generated.resources.notifications_prompt_enable
import com.sirelon.sellsnap.generated.resources.notifications_prompt_message
import com.sirelon.sellsnap.generated.resources.notifications_prompt_title
import org.jetbrains.compose.resources.stringResource

/**
 * Wires [NotificationsPromptSheet] to the OS permission request and the ViewModel. [onClose] pops
 * the sheet. Every way out reports exactly one answer: the buttons answer explicitly, and
 * swiping down, tapping the scrim or pressing back pop the sheet, which counts as "Not now"
 * (the ViewModel ignores a second report). A dispose while [isOnBackStack] is still true is an
 * Activity recreation (rotation, dark mode): the sheet comes back, so it is not an answer.
 */
@Composable
fun NotificationsPromptRoute(
    viewModel: NotificationsPromptViewModel,
    isOnBackStack: () -> Boolean,
    onClose: () -> Unit,
) {
    val permissionState = rememberPermissionState(Permission.Notification) { granted ->
        viewModel.onAnswered(enabled = true, granted = granted)
        onClose()
    }
    val currentViewModel by rememberUpdatedState(viewModel)
    val currentIsOnBackStack by rememberUpdatedState(isOnBackStack)

    DisposableEffect(Unit) {
        onDispose {
            if (!currentIsOnBackStack()) currentViewModel.onAnswered(enabled = false, granted = false)
        }
    }

    NotificationsPromptSheet(
        onEnable = {
            viewModel.onEnableClicked()
            permissionState.launchPermissionRequest()
        },
        onNotNow = {
            viewModel.onAnswered(enabled = false, granted = false)
            onClose()
        },
    )
}

@Composable
fun NotificationsPromptSheet(
    onEnable: () -> Unit,
    onNotNow: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("notifications_prompt_sheet")
            .padding(horizontal = AppDimens.Spacing.xl5)
            .padding(bottom = AppDimens.Spacing.xl5),
        verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl3),
    ) {
        Text(
            text = stringResource(Res.string.notifications_prompt_title),
            style = AppTheme.typography.headline,
            color = AppTheme.colors.onBackground,
        )
        Text(
            text = stringResource(Res.string.notifications_prompt_message),
            style = AppTheme.typography.body,
            color = AppTheme.colors.onSurfaceMuted,
        )
        AppButton(
            modifier = Modifier.fillMaxWidth().testTag("notifications_prompt_enable"),
            text = stringResource(Res.string.notifications_prompt_enable),
            onClick = onEnable,
        )
        AppButton(
            modifier = Modifier.fillMaxWidth().testTag("notifications_prompt_not_now"),
            style = AppButtonDefaults.outline(),
            text = stringResource(Res.string.not_now),
            onClick = onNotNow,
        )
    }
}
