package com.sirelon.sellsnap.features.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.mohamedrejeb.calf.core.LocalPlatformContext
import com.mohamedrejeb.calf.io.KmpFile
import com.mohamedrejeb.calf.io.getName
import com.mohamedrejeb.calf.picker.FilePickerFileType
import com.mohamedrejeb.calf.picker.FilePickerSelectionMode
import com.mohamedrejeb.calf.picker.rememberFilePickerLauncher
import com.sirelon.sellsnap.camera.rememberCameraCaptureLauncher

data class PhotoPickerUiState(
    val files: List<KmpFile> = emptyList(),
    val errorMessage: String? = null,
)

// refactor PhotoPickerController. Split for
@Stable
interface PhotoPickerController {
    fun pickFromGallery()
    fun captureWithCamera()
}

@Composable
fun rememberPhotoPickerController(
    permissionController: PermissionController,
    onResult: (Result<List<KmpFile>>) -> Unit,
    type: FilePickerFileType = FilePickerFileType.Image,
    selectionMode: FilePickerSelectionMode = FilePickerSelectionMode.Multiple,
): PhotoPickerController {
    val cameraLauncher = rememberCameraCaptureLauncher { result ->
        if (result.file != null) {
            onResult(Result.success(listOf(result.file)))
        } else if (result.error != null && !result.cancelled) {
            onResult(Result.failure(RuntimeException(result.error)))
        }
    }

    val filePickerLauncher = rememberFilePickerLauncher(
        type = type,
        selectionMode = selectionMode,
    ) { files ->
        onResult(Result.success(files))
    }

    return remember(permissionController) {
        object : PhotoPickerController {
            override fun pickFromGallery() {
                permissionController.requestPermission {
                    filePickerLauncher.launch()
                }
            }

            override fun captureWithCamera() {
                permissionController.requestPermission {
                    cameraLauncher.launch()
                }
            }
        }
    }
}
