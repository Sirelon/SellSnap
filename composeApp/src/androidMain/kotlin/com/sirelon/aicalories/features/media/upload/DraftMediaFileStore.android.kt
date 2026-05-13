package com.sirelon.sellsnap.features.media.upload

import android.net.Uri
import com.mohamedrejeb.calf.io.KmpFile
import com.mohamedrejeb.calf.io.getName
import com.mohamedrejeb.calf.io.getPath
import com.mohamedrejeb.calf.io.readByteArray
import java.io.File
import kotlin.uuid.Uuid

private var androidFilesDir: String = ""

fun initAndroidDraftMediaFileStore(filesDir: String) {
    androidFilesDir = filesDir
}

actual fun createDraftMediaFileStore(): DraftMediaFileStore {
    require(androidFilesDir.isNotEmpty()) {
        "Call initAndroidDraftMediaFileStore(context.filesDir.absolutePath) before creating DraftMediaFileStore."
    }
    return AndroidDraftMediaFileStore(File(androidFilesDir, "draft_media"))
}

private class AndroidDraftMediaFileStore(
    private val directory: File,
) : DraftMediaFileStore {

    override suspend fun persist(file: KmpFile): PersistedDraftPhoto? {
        directory.mkdirs()
        val displayName = file.getName()
        val destination = File(directory, "${Uuid.random()}${displayName.extensionSuffix()}")
        destination.writeBytes(file.readByteArray())
        val restored = KmpFile(Uri.fromFile(destination))
        val photo = DraftPhoto(
            id = destination.nameWithoutExtension,
            path = stablePath(restored) ?: return null,
            displayName = displayName,
        )
        return PersistedDraftPhoto(file = restored, photo = photo)
    }

    override fun restore(photo: DraftPhoto): KmpFile? =
        runCatching { KmpFile(Uri.parse(photo.path)) }.getOrNull()

    override fun stablePath(file: KmpFile): String? = file.getPath()

    override suspend fun delete(photos: List<DraftPhoto>) {
        photos.forEach { photo ->
            val file = Uri.parse(photo.path).path?.let(::File)
            if (file != null && file.parentFile == directory) {
                file.delete()
            }
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
