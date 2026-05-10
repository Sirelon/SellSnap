package com.sirelon.sellsnap.audio

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

actual fun createLiveAudioRecorder(): LiveAudioRecorder = NoOpLiveAudioRecorder()

private class NoOpLiveAudioRecorder : LiveAudioRecorder {
    override fun startStreaming(): Flow<ByteArray> = emptyFlow()
    override fun stopStreaming() = Unit
}
