package com.sirelon.sellsnap.features.media.upload

import com.mohamedrejeb.calf.io.KmpFile
import com.mohamedrejeb.calf.io.getName
import com.mohamedrejeb.calf.io.getPath
import com.mohamedrejeb.calf.io.readByteArray
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlin.uuid.Uuid

class MediaUploadRepository(
    private val uploader: PhotoUploader,
) {

    suspend fun publicUrl(path: String): String = uploader.publicUrl(path)

    fun uploadFile(file: KmpFile): Flow<PhotoUploadStatus> = flow {
        emitAll(
            uploader.uploadFile(
                path = file.getName()
                    ?: file.getPath()
                    ?: Uuid.random().toString(),
                byteArray = file.readByteArray(),
            ),
        )
    }
}
