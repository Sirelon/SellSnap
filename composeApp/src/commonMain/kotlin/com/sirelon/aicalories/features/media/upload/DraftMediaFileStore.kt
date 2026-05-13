package com.sirelon.sellsnap.features.media.upload

import com.mohamedrejeb.calf.io.KmpFile
import kotlinx.serialization.Serializable

@Serializable
data class DraftPhoto(
    val id: String,
    val path: String,
    val displayName: String?,
    val uploadedId: String? = null,
    val uploadedPath: String? = null,
)

data class PersistedDraftPhoto(
    val file: KmpFile,
    val photo: DraftPhoto,
)

interface DraftMediaFileStore {
    suspend fun persist(file: KmpFile): PersistedDraftPhoto?
    fun restore(photo: DraftPhoto): KmpFile?
    fun stablePath(file: KmpFile): String?
    suspend fun delete(photos: List<DraftPhoto>)
}

expect fun createDraftMediaFileStore(): DraftMediaFileStore
