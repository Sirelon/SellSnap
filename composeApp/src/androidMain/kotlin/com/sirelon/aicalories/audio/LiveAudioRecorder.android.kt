package com.sirelon.sellsnap.audio

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.isActive

private const val LIVE_SAMPLE_RATE = 24_000

actual fun createLiveAudioRecorder(): LiveAudioRecorder = AndroidLiveAudioRecorder()

private class AndroidLiveAudioRecorder : LiveAudioRecorder {

    @Volatile
    private var audioRecord: AudioRecord? = null

    @Volatile
    private var isStreaming: Boolean = false

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun startStreaming(): Flow<ByteArray> = flow {
        stopStreaming()

        val minBufferSize = AudioRecord.getMinBufferSize(
            LIVE_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val bufferSize = maxOf(minBufferSize, LIVE_SAMPLE_RATE / 2)

        val recorder = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(LIVE_SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(bufferSize)
            .build()

        audioRecord = recorder
        isStreaming = true
        recorder.startRecording()

        val buffer = ByteArray(bufferSize)
        while (currentCoroutineContext().isActive && isStreaming) {
            val read = recorder.read(buffer, 0, buffer.size)
            if (read > 0) {
                emit(buffer.copyOf(read))
            }
        }
    }.onCompletion {
        releaseRecorder()
    }.flowOn(Dispatchers.Default)

    override fun stopStreaming() {
        isStreaming = false
        runCatching { audioRecord?.stop() }
    }

    private fun releaseRecorder() {
        val recorder = audioRecord
        audioRecord = null
        isStreaming = false
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
    }
}
