package com.sirelon.sellsnap.features.auth.data

import com.sirelon.sellsnap.features.seller.auth.data.response.OlxTokenResponse
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class OlxTokenResponseTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    @Test
    fun `token endpoint body maps to domain tokens with the given issue time`() {
        val body = """{"access_token":"a","refresh_token":"r","expires_in":86400,"token_type":"bearer","scope":"v2 read write"}"""

        val tokens = json.decodeFromString<OlxTokenResponse>(body).toDomain(issuedAtEpochSeconds = 100)

        assertEquals("a", tokens.accessToken)
        assertEquals("r", tokens.refreshToken)
        assertEquals(86400, tokens.expiresInSeconds)
        assertEquals("bearer", tokens.tokenType)
        assertEquals("v2 read write", tokens.scope)
        assertEquals(100, tokens.issuedAtEpochSeconds)
    }

    @Test
    fun `body without refresh token, type and scope still maps`() {
        val body = """{"access_token":"a","expires_in":60}"""

        val tokens = json.decodeFromString<OlxTokenResponse>(body).toDomain(issuedAtEpochSeconds = 0)

        assertNull(tokens.refreshToken)
        assertEquals("", tokens.tokenType)
        assertEquals("", tokens.scope)
    }

    @Test
    fun `body without access token is rejected`() {
        val response = json.decodeFromString<OlxTokenResponse>("""{"expires_in":60}""")

        assertFailsWith<IllegalArgumentException> { response.toDomain(issuedAtEpochSeconds = 0) }
    }
}
