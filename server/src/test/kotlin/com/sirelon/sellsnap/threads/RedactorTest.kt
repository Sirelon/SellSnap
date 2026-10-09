package com.sirelon.sellsnap.threads

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class RedactorTest {
    @Test
    fun masksQuerySecrets() {
        val line = "GET https://graph.threads.net/v1.0/debug_token?access_token=TH_abc123&input_token=TH_def456&x=1"
        val out = redact(line)
        assertEquals(
            "GET https://graph.threads.net/v1.0/debug_token?access_token=[redacted]&input_token=[redacted]&x=1",
            out,
        )
    }

    @Test
    fun masksClientSecretInAFormBody() {
        val out = redact("grant_type=th_exchange_token&client_secret=s3cr3t&access_token=tok")
        assertEquals("grant_type=th_exchange_token&client_secret=[redacted]&access_token=[redacted]", out)
    }

    @Test
    fun masksEncodedEqualsSign() {
        assertEquals("access_token%3D[redacted]", redact("access_token%3Dabc"))
    }

    @Test
    fun masksJsonFields() {
        val out = redact("""{"access_token":"TH_abc","token_type":"bearer","client_secret": "zzz"}""")
        assertEquals("""{"access_token":"[redacted]","token_type":"bearer","client_secret": "[redacted]"}""", out)
    }

    @Test
    fun masksTelegramBotTokensInUrlsAndBare() {
        val token = "123456789:AAE-abc_DEF0123456789abcdefghijklmnop"
        assertEquals("POST https://api.telegram.org/bot[redacted]/sendMessage", redact("POST https://api.telegram.org/bot$token/sendMessage"))
        assertEquals("token [redacted] leaked", redact("token $token leaked"))
    }

    @Test
    fun leavesOtherTextAlone() {
        val text = "Threads rejected the request (HTTP 400, code 190) at 12:30, ratio 3:4"
        assertEquals(text, redact(text))
        assertFalse(redact("no secrets here").contains("redacted"))
    }

    @Test
    fun masksEveryOccurrence() {
        val out = redact("access_token=a and again access_token=b")
        assertEquals("access_token=[redacted] and again access_token=[redacted]", out)
    }
}
