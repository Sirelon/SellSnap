package com.sirelon.sellsnap.features.media

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.camera_rationale_message
import com.sirelon.sellsnap.generated.resources.camera_rationale_title
import com.sirelon.sellsnap.generated.resources.camera_settings_message_android
import com.sirelon.sellsnap.generated.resources.camera_settings_message_ios
import com.sirelon.sellsnap.generated.resources.camera_settings_title
import com.sirelon.sellsnap.generated.resources.cancel
import com.sirelon.sellsnap.generated.resources.not_now
import com.sirelon.sellsnap.generated.resources.open_settings
import com.sirelon.sellsnap.generated.resources.retry
import com.sirelon.sellsnap.platform.PlatformTargets
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

data class PermissionDialogContent(
    val title: StringResource,
    val message: StringResource,
    val confirmText: StringResource,
    val dismissText: StringResource,
)

@Composable
fun PermissionDialogs(
    controller: PermissionController,
    isIosDevice: Boolean = PlatformTargets.isIos(),
    rationaleContent: PermissionDialogContent? = null,
    settingsContentProvider: ((Boolean) -> PermissionDialogContent)? = null,
) {
    val resolvedRationaleContent = rationaleContent ?: PermissionDialogContent(
        title = Res.string.camera_rationale_title,
        message = Res.string.camera_rationale_message,
        confirmText = Res.string.retry,
        dismissText = Res.string.not_now,
    )
    val resolvedSettingsContent = (settingsContentProvider ?: { ios ->
        PermissionDialogContent(
            title = Res.string.camera_settings_title,
            message = if (ios) {
                Res.string.camera_settings_message_ios
            } else {
                Res.string.camera_settings_message_android
            },
            confirmText = Res.string.open_settings,
            dismissText = Res.string.cancel,
        )
    })(isIosDevice)
    val permissionState by controller.uiState

    if (permissionState.showRationale) {
        AlertDialog(
            onDismissRequest = controller::dismissRationale,
            title = { Text(stringResource(resolvedRationaleContent.title)) },
            text = { Text(stringResource(resolvedRationaleContent.message)) },
            confirmButton = {
                TextButton(onClick = controller::retry) {
                    Text(stringResource(resolvedRationaleContent.confirmText))
                }
            },
            dismissButton = {
                TextButton(onClick = controller::dismissRationale) {
                    Text(stringResource(resolvedRationaleContent.dismissText))
                }
            },
        )
    }

    if (permissionState.showSettings) {
        AlertDialog(
            onDismissRequest = controller::dismissSettings,
            title = { Text(stringResource(resolvedSettingsContent.title)) },
            text = { Text(stringResource(resolvedSettingsContent.message)) },
            confirmButton = {
                TextButton(onClick = controller::openSettings) {
                    Text(stringResource(resolvedSettingsContent.confirmText))
                }
            },
            dismissButton = {
                TextButton(onClick = controller::dismissSettings) {
                    Text(stringResource(resolvedSettingsContent.dismissText))
                }
            },
        )
    }
}
