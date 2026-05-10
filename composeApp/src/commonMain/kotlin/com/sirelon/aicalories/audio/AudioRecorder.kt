package com.sirelon.sellsnap.audio

interface AudioRecorder {
    fun startRecording()
    fun stopRecording(): ByteArray?
}

expect fun createAudioRecorder(): AudioRecorder
