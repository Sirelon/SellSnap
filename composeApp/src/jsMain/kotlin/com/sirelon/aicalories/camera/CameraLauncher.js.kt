package com.sirelon.sellsnap.camera

import androidx.compose.runtime.Composable

@Composable
actual fun rememberCameraCaptureLauncher(
    onResult: (CameraCaptureResult) -> Unit,
): CameraLauncher = UnsupportedCameraLauncher(onResult)

private class UnsupportedCameraLauncher(
    private val onResult: (CameraCaptureResult) -> Unit,
) : CameraLauncher {
    override fun launch() {
        onResult(
            CameraCaptureResult(
                error = "Camera capture is not supported on this platform.",
                cancelled = true,
            ),
        )
    }
}
