package com.sirelon.aicalories.features.auth.data

import com.sirelon.aicalories.features.seller.auth.data.OlxAuthCallbackBridge
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

class OlxAuthCallbackBridgeTest {

    @Test
    fun `callback published before collection is delivered once`() = runBlocking {
        OlxAuthCallbackBridge.resetForTesting()
        OlxAuthCallbackBridge.publishCallback("selolxai://olx-auth/callback?code=one")

        val callback = withTimeout(1_000) {
            OlxAuthCallbackBridge.callbacks.first()
        }

        assertEquals("selolxai://olx-auth/callback?code=one", callback)
    }

    @Test
    fun `duplicate callback url is not delivered again`() = runBlocking {
        val url = "selolxai://olx-auth/callback?code=one"
        OlxAuthCallbackBridge.resetForTesting()
        OlxAuthCallbackBridge.publishCallback(url)

        val firstCallback = withTimeout(1_000) {
            OlxAuthCallbackBridge.callbacks.first()
        }
        OlxAuthCallbackBridge.publishCallback(url)
        val duplicateCallback = withTimeoutOrNull(250) {
            OlxAuthCallbackBridge.callbacks.first()
        }

        assertEquals(url, firstCallback)
        assertNull(duplicateCallback)
    }
}
