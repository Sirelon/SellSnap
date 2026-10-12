package com.sirelon.sellsnap.features.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.mohamedrejeb.calf.permissions.Permission
import com.mohamedrejeb.calf.permissions.PermissionStatus
import com.mohamedrejeb.calf.permissions.isGranted
import com.mohamedrejeb.calf.permissions.rememberPermissionState
import com.sirelon.sellsnap.platform.PlatformTargets

data class PermissionUiState(
    val hasPermission: Boolean = false,
    val showRationale: Boolean = false,
    val showSettings: Boolean = false,
    val denialCount: Int = 0,
)

@Stable
interface PermissionController {
    val uiState: State<PermissionUiState>
    /**
     * Runs [onGranted] once the permission is granted, asking the system first if needed.
     * A denial opens the rationale or settings dialog unless [quiet] is true; use [quiet] for
     * requests the app makes on its own, so only a user tap leads to a dialog.
     */
    fun requestPermission(quiet: Boolean = false, onGranted: () -> Unit)
    fun retry()
    fun dismissRationale()
    fun dismissSettings()
    fun openSettings()
}

@Composable
fun rememberPermissionController(
    permission: Permission,
    isIosDevice: Boolean = PlatformTargets.isIos(),
): PermissionController {
    val uiState = remember { mutableStateOf(PermissionUiState()) }
    val pendingAction = remember { mutableStateOf<(() -> Unit)?>(null) }
    val lastAction = remember { mutableStateOf<(() -> Unit)?>(null) }
    val lastHandledDenial = remember { mutableStateOf(0) }
    val quietRequest = remember { mutableStateOf(false) }

    val permissionState = rememberPermissionState(permission) { granted ->
        if (granted) {
            uiState.value = uiState.value.copy(
                hasPermission = true,
                showRationale = false,
                showSettings = false,
                denialCount = 0,
            )
        } else {
            uiState.value = uiState.value.copy(
                hasPermission = false,
                denialCount = uiState.value.denialCount + 1,
            )
            pendingAction.value = null
        }
    }

    // Keyed on denialCount too: a repeat denial (iOS after the first "Don't Allow", Android after
    // "Don't ask again") returns at once without changing the status, and must still show a dialog.
    LaunchedEffect(permissionState.status, uiState.value.denialCount) {
        val status = permissionState.status
        if (status.isGranted) {
            uiState.value = uiState.value.copy(
                hasPermission = true,
                showRationale = false,
                showSettings = false,
            )
            lastHandledDenial.value = 0
            pendingAction.value?.let { action ->
                pendingAction.value = null
                action.invoke()
            }
        } else {
            uiState.value = uiState.value.copy(hasPermission = false)

            val denialCount = uiState.value.denialCount
            if (denialCount == 0 || denialCount == lastHandledDenial.value) return@LaunchedEffect
            lastHandledDenial.value = denialCount
            if (quietRequest.value) return@LaunchedEffect

            val requiresSettings =
                isIosDevice || (status as? PermissionStatus.Denied)?.shouldShowRationale != true
            uiState.value = uiState.value.copy(
                showSettings = requiresSettings,
                showRationale = !requiresSettings,
            )
        }
    }

    return remember(isIosDevice) {
        object : PermissionController {
            override val uiState: State<PermissionUiState> = uiState

            override fun requestPermission(quiet: Boolean, onGranted: () -> Unit) {
                lastAction.value = onGranted
                quietRequest.value = quiet
                if (permissionState.status.isGranted) {
                    uiState.value = uiState.value.copy(
                        hasPermission = true,
                        showRationale = false,
                        showSettings = false,
                    )
                    onGranted.invoke()
                } else {
                    uiState.value = uiState.value.copy(
                        showRationale = false,
                        showSettings = false,
                    )
                    pendingAction.value = onGranted
                    permissionState.launchPermissionRequest()
                }
            }

            override fun retry() {
                val action = lastAction.value ?: return
                uiState.value = uiState.value.copy(
                    showRationale = false,
                    showSettings = false,
                )
                quietRequest.value = false
                pendingAction.value = action
                permissionState.launchPermissionRequest()
            }

            override fun dismissRationale() {
                uiState.value = uiState.value.copy(showRationale = false)
            }

            override fun dismissSettings() {
                uiState.value = uiState.value.copy(showSettings = false)
            }

            override fun openSettings() {
                uiState.value = uiState.value.copy(
                    showSettings = false,
                    showRationale = false,
                    denialCount = 0,
                )
                lastHandledDenial.value = 0
                // Runs when the status turns Granted on return from Settings.
                pendingAction.value = lastAction.value
                permissionState.openAppSettings()
            }
        }
    }
}
