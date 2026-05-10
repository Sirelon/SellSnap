package com.sirelon.sellsnap.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

actual fun createAudioRecorder(): AudioRecorder = AndroidAudioRecorder()

private class AndroidAudioRecorder : AudioRecorder, KoinComponent {

    private val context: Context by inject()
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    override fun startRecording() {
        stopRecording()

        val audioDir = File(context.cacheDir, "audio").apply { mkdirs() }
        val file = File.createTempFile("whisper_", ".m4a", audioDir)
        outputFile = file

        recorder = createMediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
    }

    override fun stopRecording(): ByteArray? {
        val file = outputFile
        val activeRecorder = recorder
        recorder = null
        outputFile = null

        return try {
            activeRecorder?.stop()
            activeRecorder?.release()
            file?.takeIf { it.exists() }?.readBytes()
        } catch (_: Exception) {
            null
        } finally {
            runCatching { activeRecorder?.release() }
            runCatching { file?.delete() }
        }
    }

    private fun createMediaRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
}
