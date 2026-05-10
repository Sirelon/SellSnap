package com.sirelon.aicalories.audio

interface AudioRecorder {
    fun startRecording()
    fun stopRecording(): ByteArray?
}

expect fun createAudioRecorder(): AudioRecorder
