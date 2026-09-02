package com.sirelon.sellsnap.features.media

import com.mohamedrejeb.calf.io.KmpFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Hands photos received via the OS share sheet (Android ACTION_SEND/SEND_MULTIPLE, iOS share
 * extension) from the platform entry point to the ad-generation flow. Mirrors
 * OlxAuthCallbackBridge's role for OLX auth deep links.
 */
object SharedImagesBridge {
    private val pendingImages = MutableStateFlow<List<KmpFile>?>(null)

    val pending: StateFlow<List<KmpFile>?> = pendingImages.asStateFlow()

    fun publish(files: List<KmpFile>) {
        if (files.isEmpty()) return
        pendingImages.value = files
    }

    fun consume(): List<KmpFile>? {
        val files = pendingImages.value
        pendingImages.value = null
        return files
    }
}
