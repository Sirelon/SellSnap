package com.sirelon.sellsnap.features.media.upload

import com.mohamedrejeb.calf.io.KmpFile
import com.mohamedrejeb.calf.io.getName
import com.mohamedrejeb.calf.io.getPath
import com.mohamedrejeb.calf.io.readByteArray
import com.sirelon.sellsnap.supabase.SupabaseClient
import io.github.jan.supabase.storage.UploadStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlin.uuid.Uuid

class MediaUploadRepository(
    private val client: SupabaseClient,
) {

    fun publicUrl(path: String): String = client.publicUrl(path)

    fun uploadFile(file: KmpFile): Flow<UploadStatus> = flow {
        emitAll(
            client.uploadFile(
                path = file.getName()
                    ?: file.getPath()
                    ?: Uuid.random().toString(),
                byteArray = file.readByteArray(),
            ),
        )
    }
}
