package com.sirelon.sellsnap.fakes

import com.sirelon.sellsnap.http.OidcVerifier

/** Accepts exactly the tokens in [validTokens]. */
class FakeOidcVerifier(var validTokens: Set<String> = setOf(VALID_TOKEN)) : OidcVerifier {
    override suspend fun verify(bearerToken: String): Boolean = bearerToken in validTokens

    companion object {
        const val VALID_TOKEN = "scheduler-token"
    }
}
