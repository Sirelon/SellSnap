package com.sirelon.sellsnap.audio

actual fun createAudioRecorder(): AudioRecorder = NoOpAudioRecorder()

private class NoOpAudioRecorder : AudioRecorder {
    override fun startRecording() = Unit
    override fun stopRecording(): ByteArray? = null
}
