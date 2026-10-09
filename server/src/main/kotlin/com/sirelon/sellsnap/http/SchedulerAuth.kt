package com.sirelon.sellsnap.http

import com.google.api.client.json.webtoken.JsonWebToken
import com.google.auth.oauth2.TokenVerifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Verifies the Cloud Scheduler OIDC token on `/tick` and `/batch`. */
interface OidcVerifier {
    /** True when [bearerToken] is a valid token from the scheduler service account for this service. */
    suspend fun verify(bearerToken: String): Boolean
}

/** The claim rules for a Cloud Scheduler OIDC token, apart from signature and audience. */
object SchedulerClaims {
    /** Google writes both spellings of the issuer into ID tokens. */
    val ISSUERS: Set<String> = setOf("https://accounts.google.com", "accounts.google.com")

    fun accepts(issuer: String?, email: String?, emailVerified: Any?, schedulerSaEmail: String): Boolean =
        issuer in ISSUERS &&
            email != null && email == schedulerSaEmail &&
            (emailVerified == true || emailVerified == "true")
}

/**
 * Checks signature, expiry and audience with Google's [TokenVerifier], then the claims of
 * [SchedulerClaims]. The verifier gets no issuer, because it accepts only one spelling of it.
 * Verification fetches and caches Google's public keys over blocking I/O, so it runs on
 * [Dispatchers.IO]. [audience] is the service base URL, byte-identical to the Scheduler job's
 * `--oidc-token-audience`.
 */
class GoogleOidcVerifier(
    audience: String,
    private val schedulerSaEmail: String,
    private val verifyToken: (String) -> JsonWebToken.Payload = TokenVerifier.newBuilder()
        .setAudience(audience)
        .build()
        .let { verifier -> { token: String -> verifier.verify(token).payload } },
) : OidcVerifier {
    override suspend fun verify(bearerToken: String): Boolean {
        val payload = try {
            withContext(Dispatchers.IO) { verifyToken(bearerToken) }
        } catch (_: TokenVerifier.VerificationException) {
            return false
        }
        return SchedulerClaims.accepts(
            issuer = payload.issuer,
            email = payload["email"] as? String,
            emailVerified = payload["email_verified"],
            schedulerSaEmail = schedulerSaEmail,
        )
    }
}

/** The token of an `Authorization: Bearer <token>` header, or null. */
fun bearerToken(header: String?): String? {
    val value = header?.trim() ?: return null
    if (!value.startsWith("Bearer ", ignoreCase = true)) return null
    return value.substring("Bearer ".length).trim().takeIf { it.isNotEmpty() }
}
