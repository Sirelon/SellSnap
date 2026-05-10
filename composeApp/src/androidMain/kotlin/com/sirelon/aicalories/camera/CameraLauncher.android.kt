package com.sirelon.sellsnap.camera

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.mohamedrejeb.calf.io.KmpFile
import java.io.File

@Composable
actual fun rememberCameraCaptureLauncher(
    onResult: (CameraCaptureResult) -> Unit,
): CameraLauncher {
    val context = LocalContext.current

    var currentPhotoFile by remember { mutableStateOf<File?>(null) }
    var currentPhotoUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val photoFile = currentPhotoFile
            val photoUri = currentPhotoUri
            if (!success || photoFile == null || photoUri == null) {
                photoFile?.delete()
                currentPhotoFile = null
                currentPhotoUri = null
                onResult(CameraCaptureResult(cancelled = true))
                return@rememberLauncherForActivityResult
            }

            onResult(
                CameraCaptureResult(
                    file = KmpFile(photoUri),
                    displayName = photoFile.name,
                ),
            )

            currentPhotoFile = null
            currentPhotoUri = null
        }

    return remember(context, cameraLauncher) {
        CameraLauncherImpl {
            try {
                val photoFile = createTempImageFile(context)
                val authority = "${context.packageName}.fileprovider"
                val photoUri = FileProvider.getUriForFile(context, authority, photoFile)

                currentPhotoFile = photoFile
                currentPhotoUri = photoUri

                cameraLauncher.launch(photoUri)
            } catch (error: Throwable) {
                onResult(
                    CameraCaptureResult(
                        error = error.message ?: "Unable to start camera.",
                    ),
                )
                currentPhotoFile?.delete()
                currentPhotoFile = null
                currentPhotoUri = null
            }
        }
    }
}

private fun createTempImageFile(context: Context): File {
    val imagesDir = File(context.cacheDir, "camera")
    if (!imagesDir.exists()) {
        imagesDir.mkdirs()
    }
    return File.createTempFile("camera_capture_", ".jpg", imagesDir)
}

private class CameraLauncherImpl(
    private val onLaunch: () -> Unit,
) : CameraLauncher {
    override fun launch() {
        onLaunch()
    }
}
