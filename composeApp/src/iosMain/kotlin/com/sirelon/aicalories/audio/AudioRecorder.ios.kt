package com.sirelon.sellsnap.audio

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.AVFAudio.AVAudioRecorder
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryRecord
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVNumberOfChannelsKey
import platform.AVFAudio.AVSampleRateKey
import platform.AudioToolbox.kAudioFormatMPEG4AAC
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.posix.memcpy

actual fun createAudioRecorder(): AudioRecorder = IosAudioRecorder()

private class IosAudioRecorder : AudioRecorder {

    private var recorder: AVAudioRecorder? = null
    private var outputUrl: NSURL? = null

    override fun startRecording() {
        stopRecording()

        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryRecord, error = null)
        session.setActive(true, error = null)

        val fileName = "whisper_${NSDate().timeIntervalSince1970}.m4a"
        val url = NSURL.fileURLWithPath(NSTemporaryDirectory() + fileName)
        outputUrl = url

        val settings = mapOf<Any?, Any?>(
            AVFormatIDKey to NSNumber(unsignedInt = kAudioFormatMPEG4AAC),
            AVSampleRateKey to NSNumber(float = 44_100f),
            AVNumberOfChannelsKey to NSNumber(int = 1),
        )
        recorder = AVAudioRecorder(url = url, settings = settings, error = null)
        recorder?.record()
    }

    override fun stopRecording(): ByteArray? {
        recorder?.stop()
        recorder = null
        AVAudioSession.sharedInstance().setActive(false, error = null)

        val url = outputUrl ?: return null
        outputUrl = null

        return try {
            NSData.dataWithContentsOfURL(url)
                ?.toByteArray()
                .also { NSFileManager.defaultManager.removeItemAtURL(url, error = null) }
        } catch (_: Exception) {
            null
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val byteCount = length.toInt()
    if (byteCount == 0) return ByteArray(0)

    return ByteArray(byteCount).also { array ->
        array.usePinned { pinned ->
            memcpy(pinned.addressOf(0), bytes, length)
        }
    }
}
