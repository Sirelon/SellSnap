package com.sirelon.sellsnap.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import com.mohamedrejeb.calf.io.KmpFile

@Stable
interface CameraLauncher {
    fun launch()
}

data class CameraCaptureResult(
    val file: KmpFile? = null,
    val displayName: String? = null,
    val error: String? = null,
    val cancelled: Boolean = false,
)

@Composable
expect fun rememberCameraCaptureLauncher(
    onResult: (CameraCaptureResult) -> Unit,
): CameraLauncher
