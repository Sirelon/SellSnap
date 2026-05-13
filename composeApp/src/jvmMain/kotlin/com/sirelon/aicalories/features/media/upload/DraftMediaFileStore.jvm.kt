package com.sirelon.sellsnap.features.media.upload

import com.mohamedrejeb.calf.io.KmpFile
import com.mohamedrejeb.calf.io.getName
import com.mohamedrejeb.calf.io.getPath
import com.mohamedrejeb.calf.io.readByteArray
import java.io.File
import kotlin.uuid.Uuid

actual fun createDraftMediaFileStore(): DraftMediaFileStore =
    JvmDraftMediaFileStore(
        File(System.getProperty("user.home"), ".sellsnap/draft_media"),
    )

private class JvmDraftMediaFileStore(
    private val directory: File,
) : DraftMediaFileStore {

    override suspend fun persist(file: KmpFile): PersistedDraftPhoto? {
        directory.mkdirs()
        val displayName = file.getName()
        val destination = File(directory, "${Uuid.random()}${displayName.extensionSuffix()}")
        destination.writeBytes(file.readByteArray())
        val restored = KmpFile(destination)
        val photo = DraftPhoto(
            id = destination.nameWithoutExtension,
            path = stablePath(restored) ?: return null,
            displayName = displayName,
        )
        return PersistedDraftPhoto(file = restored, photo = photo)
    }

    override fun restore(photo: DraftPhoto): KmpFile? = KmpFile(File(photo.path))

    override fun stablePath(file: KmpFile): String? = file.getPath()

    override suspend fun delete(photos: List<DraftPhoto>) {
        photos.forEach { photo -> File(photo.path).delete() }
    }
}

private fun String?.extensionSuffix(): String {
    val extension = this
        ?.substringAfterLast('.', missingDelimiterValue = "")
        ?.takeIf { it.isNotBlank() && it.length <= 5 }
        ?.lowercase()
    return if (extension == null) ".jpg" else ".$extension"
}
