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

    @Test
    fun masksGoogleApiKeysInUrlsAndBare() {
        val key = "AIzaSyA1b2C3d4E5f6G7h8I9j0K1l2M3n4O5p6Q"
        assertEquals(39, key.length)
        assertEquals("GET https://x/v1beta/models/m:generateContent?key=[redacted]", redact("GET https://x/v1beta/models/m:generateContent?key=$key"))
        assertEquals("key [redacted] leaked", redact("key $key leaked"))
    }

    @Test
    fun masksGoogleAuthKeysThatStartWithAq() {
        val key = "AQ.Ab8RN6Iexample_key-0123456789abcdef"
        assertEquals("rejected [redacted] here", redact("rejected $key here"))
    }

    @Test
    fun masksTheApiKeyHeaderWhateverTheKeyLooksLike() {
        assertEquals("x-goog-api-key: [redacted]", redact("x-goog-api-key: some-odd-key-value"))
        assertEquals("X-Goog-Api-Key:[redacted], other", redact("X-Goog-Api-Key:abc123, other"))
    }

    @Test
    fun leavesTextThatOnlyLooksLikeAGoogleKeyAlone() {
        val text = "AIza too short, and AQ.x is short too"
        assertEquals(text, redact(text))
    }
}
