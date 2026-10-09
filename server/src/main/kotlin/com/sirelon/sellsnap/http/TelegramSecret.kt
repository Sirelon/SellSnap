package com.sirelon.sellsnap.http

import java.security.MessageDigest

/** The webhook secret Telegram sends in `X-Telegram-Bot-Api-Secret-Token`. */
class TelegramSecret(expected: String) {
    private val expectedDigest = sha256(expected)

    /**
     * Constant-time check of the header value. Both sides are hashed first so that the comparison
     * time does not depend on the length of the guess either.
     */
    fun matches(header: String?): Boolean {
        if (header == null) return false
        return MessageDigest.isEqual(expectedDigest, sha256(header))
    }

    private fun sha256(value: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())

    companion object {
        const val HEADER = "X-Telegram-Bot-Api-Secret-Token"
    }
}
