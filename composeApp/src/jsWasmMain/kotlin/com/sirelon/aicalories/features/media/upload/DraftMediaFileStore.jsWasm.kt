package com.sirelon.sellsnap.features.media.upload

import com.mohamedrejeb.calf.io.KmpFile
import com.mohamedrejeb.calf.io.getName
import com.mohamedrejeb.calf.io.getPath
import kotlin.uuid.Uuid

actual fun createDraftMediaFileStore(): DraftMediaFileStore = JsWasmDraftMediaFileStore()

private class JsWasmDraftMediaFileStore : DraftMediaFileStore {
    override suspend fun persist(file: KmpFile): PersistedDraftPhoto? {
        val photo = DraftPhoto(
            id = Uuid.random().toString(),
            path = stablePath(file) ?: return null,
            displayName = file.getName(),
        )
        return PersistedDraftPhoto(file = file, photo = photo)
    }

    override fun restore(photo: DraftPhoto): KmpFile? = null

    override fun stablePath(file: KmpFile): String? = file.getPath()

    override suspend fun delete(photos: List<DraftPhoto>) = Unit
}
