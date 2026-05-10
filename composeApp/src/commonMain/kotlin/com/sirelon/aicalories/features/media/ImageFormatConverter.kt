package com.sirelon.sellsnap.features.media

import com.mohamedrejeb.calf.io.KmpFile

interface ImageFormatConverter {
    suspend fun convert(
        file: KmpFile,
    ): KmpFile
}

class PassthroughImageFormatConverter : ImageFormatConverter {
    override suspend fun convert(
        file: KmpFile,
    ): KmpFile = file
}

expect fun imageFormatConverter(): ImageFormatConverter
