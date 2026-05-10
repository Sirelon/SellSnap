package com.sirelon.sellsnap.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.uikit.LocalUIViewController
import com.mohamedrejeb.calf.io.KmpFile
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.writeToURL
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerCameraCaptureMode
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol

@Composable
actual fun rememberCameraCaptureLauncher(
    onResult: (CameraCaptureResult) -> Unit,
): CameraLauncher {
    val currentController = LocalUIViewController.current
    val pickerDelegate = remember { CameraCaptureDelegate() }
    pickerDelegate.onResult = onResult

    return remember(currentController, pickerDelegate) {
        CameraLauncherImpl {
            val cameraSourceType =
                UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
            val cameraModePhoto =
                UIImagePickerControllerCameraCaptureMode.UIImagePickerControllerCameraCaptureModePhoto

            if (!UIImagePickerController.isSourceTypeAvailable(cameraSourceType)) {
                onResult(CameraCaptureResult(error = "Camera not available.", cancelled = true))
                return@CameraLauncherImpl
            }

            val picker =
                UIImagePickerController().apply {
                    sourceType = cameraSourceType
                    cameraCaptureMode = cameraModePhoto
                    this.delegate = pickerDelegate
                }
            pickerDelegate.onResult = onResult

            currentController.presentViewController(
                picker,
                animated = true,
                completion = null,
            )
        }
    }
}

private class CameraCaptureDelegate :
    platform.darwin.NSObject(),
    UIImagePickerControllerDelegateProtocol,
    UINavigationControllerDelegateProtocol {

    var onResult: ((CameraCaptureResult) -> Unit)? = null

    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>,
    ) {
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        val data = image?.jpegData()
        val fileName = "camera_${NSUUID().UUIDString}.jpg"
        val tempDirectory = NSTemporaryDirectory()
        val fileUrl = if (data != null) NSURL.fileURLWithPath(tempDirectory + fileName) else null

        val writeSucceeded = if (data != null && fileUrl != null) {
            data.writeToURL(fileUrl, true)
        } else {
            false
        }

        val result =
            if (writeSucceeded && fileUrl != null) {
                CameraCaptureResult(
                    file = KmpFile(url = fileUrl, originalUrl = fileUrl),
                    displayName = fileName,
                )
            } else {
                CameraCaptureResult(error = "Failed to capture photo.")
            }

        onResult?.invoke(result)
        picker.dismissViewControllerAnimated(true, completion = null)
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        onResult?.invoke(CameraCaptureResult(cancelled = true))
        picker.dismissViewControllerAnimated(true, completion = null)
    }
}

private fun UIImage.jpegData() = UIImageJPEGRepresentation(this, 0.9)

private class CameraLauncherImpl(
    private val onLaunch: () -> Unit,
) : CameraLauncher {
    override fun launch() {
        onLaunch()
    }
}
