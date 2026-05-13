package com.sirelon.sellsnap.features.media.upload

import com.mohamedrejeb.calf.io.KmpFile
import com.mohamedrejeb.calf.io.getName
import com.mohamedrejeb.calf.io.getPath
import com.mohamedrejeb.calf.io.readByteArray
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.writeToURL

actual fun createDraftMediaFileStore(): DraftMediaFileStore = IosDraftMediaFileStore()

private class IosDraftMediaFileStore : DraftMediaFileStore {

    private val directoryPath: String by lazy {
        val documentDirectory = NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = false,
            error = null,
        )
        val path = requireNotNull(documentDirectory?.path) { "Could not resolve iOS document directory" } +
            "/draft_media"
        NSFileManager.defaultManager.createDirectoryAtPath(
            path = path,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
        path
    }

    override suspend fun persist(file: KmpFile): PersistedDraftPhoto? {
        val displayName = file.getName()
        val destinationUrl = NSURL.fileURLWithPath(
            path = "$directoryPath/${NSUUID().UUIDString}${displayName.extensionSuffix()}",
            isDirectory = false,
        )
        val success = file.readByteArray().toNSData().writeToURL(destinationUrl, true)
        if (!success) return null

        val restored = KmpFile(url = destinationUrl, originalUrl = destinationUrl)
        val photo = DraftPhoto(
            id = requireNotNull(destinationUrl.lastPathComponent).substringBeforeLast('.'),
            path = stablePath(restored) ?: return null,
            displayName = displayName,
        )
        return PersistedDraftPhoto(file = restored, photo = photo)
    }

    override fun restore(photo: DraftPhoto): KmpFile? {
        val url = NSURL.URLWithString(photo.path) ?: return null
        return KmpFile(url = url, originalUrl = url)
    }

    override fun stablePath(file: KmpFile): String? = file.getPath()

    override suspend fun delete(photos: List<DraftPhoto>) {
        photos.forEach { photo ->
            val url = NSURL.URLWithString(photo.path) ?: return@forEach
            url.path?.let { NSFileManager.defaultManager.removeItemAtPath(it, error = null) }
        }
    }
}

private fun String?.extensionSuffix(): String {
    val extension = this
        ?.substringAfterLast('.', missingDelimiterValue = "")
        ?.takeIf { it.isNotBlank() && it.length <= 5 }
        ?.lowercase()
    return if (extension == null) ".jpg" else ".$extension"
}

private fun ByteArray.toNSData(): NSData =
    usePinned { pinned ->
        NSData.create(
            bytes = pinned.addressOf(0),
            length = size.toULong(),
        )
    }
