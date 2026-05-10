package com.sirelon.sellsnap.audio

import kotlinx.coroutines.flow.Flow

interface LiveAudioRecorder {
    fun startStreaming(): Flow<ByteArray>
    fun stopStreaming()
}

expect fun createLiveAudioRecorder(): LiveAudioRecorder
