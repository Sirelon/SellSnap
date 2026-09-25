package com.sirelon.sellsnap.features.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.mohamedrejeb.calf.io.KmpFile
import com.mohamedrejeb.calf.io.readByteArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.uuid.Uuid

actual fun imageFormatConverter(): ImageFormatConverter = AndroidImageFormatConverter()

private const val JPEG_QUALITY_PERCENT = 80

private class AndroidImageFormatConverter : ImageFormatConverter {

    override suspend fun convert(file: KmpFile): KmpFile = withContext(Dispatchers.Default) {
        val bytes = runCatching { file.readByteArray() }.getOrNull() ?: return@withContext file

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext file

        val orientation = runCatching {
            ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        // BitmapFactory decodes pixels in their raw (pre-rotation) layout, so the target size used
        // to pick inSampleSize must be expressed in that same raw orientation, while the size used
        // for the seller-facing output must be upright. When orientation swaps axes, these differ.
        val exifSwapsAxes = orientation.swapsAxes()
        val uprightWidth = if (exifSwapsAxes) bounds.outHeight else bounds.outWidth
        val uprightHeight = if (exifSwapsAxes) bounds.outWidth else bounds.outHeight
        val targetSize = downscaledImageSize(uprightWidth, uprightHeight)
        val targetRawWidth = if (exifSwapsAxes) targetSize.height else targetSize.width
        val targetRawHeight = if (exifSwapsAxes) targetSize.width else targetSize.height

        val sampleOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, targetRawWidth, targetRawHeight)
        }
        val sampledBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, sampleOptions)
            ?: return@withContext file

        val uprightBitmap = sampledBitmap.applyExifOrientation(orientation)
        val resizedBitmap = if (uprightBitmap.width == targetSize.width && uprightBitmap.height == targetSize.height) {
            uprightBitmap
        } else {
            Bitmap.createScaledBitmap(uprightBitmap, targetSize.width, targetSize.height, true).also {
                if (it !== uprightBitmap) uprightBitmap.recycle()
            }
        }

        val opaqueBitmap = resizedBitmap.flattenOntoWhiteIfTransparent()
        val outputBytes = ByteArrayOutputStream().use { output ->
            opaqueBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY_PERCENT, output)
            output.toByteArray()
        }
        opaqueBitmap.recycle()

        val destination = File.createTempFile("upload_${Uuid.random()}_", ".jpg")
        destination.writeBytes(outputBytes)
        KmpFile(Uri.fromFile(destination))
    }
}

/**
 * JPEG has no alpha channel, so compressing a bitmap with transparency straight to JPEG leaves
 * premultiplied-alpha black where pixels used to be transparent. Draw onto a white canvas first
 * so a transparent PNG/WEBP source (e.g. a cut-out product photo) turns into a plain white
 * background instead.
 */
private fun Bitmap.flattenOntoWhiteIfTransparent(): Bitmap {
    if (!hasAlpha()) return this
    val flattened = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    Canvas(flattened).apply {
        drawColor(Color.WHITE)
        drawBitmap(this@flattenOntoWhiteIfTransparent, 0f, 0f, null)
    }
    recycle()
    return flattened
}

/** True for the four EXIF orientations that are rotated 90/270 degrees from upright. */
private fun Int.swapsAxes(): Boolean = this == ExifInterface.ORIENTATION_ROTATE_90 ||
    this == ExifInterface.ORIENTATION_ROTATE_270 ||
    this == ExifInterface.ORIENTATION_TRANSPOSE ||
    this == ExifInterface.ORIENTATION_TRANSVERSE

private fun Bitmap.applyExifOrientation(orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.postRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.postRotate(270f)
            matrix.postScale(-1f, 1f)
        }
        else -> return this
    }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true).also {
        if (it !== this) recycle()
    }
}

/** Canonical Android sample-size search: developer.android.com/topic/performance/graphics/load-bitmap. */
private fun calculateInSampleSize(rawWidth: Int, rawHeight: Int, targetWidth: Int, targetHeight: Int): Int {
    var inSampleSize = 1
    if (rawHeight > targetHeight || rawWidth > targetWidth) {
        val halfHeight = rawHeight / 2
        val halfWidth = rawWidth / 2
        while ((halfHeight / inSampleSize) >= targetHeight && (halfWidth / inSampleSize) >= targetWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}
