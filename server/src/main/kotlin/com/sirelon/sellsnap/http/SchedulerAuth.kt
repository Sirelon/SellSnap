package com.sirelon.sellsnap.http

/** Verifies the Cloud Scheduler OIDC token on `/tick` and `/batch`. */
interface OidcVerifier {
    /** True when [bearerToken] is a valid token from the scheduler service account for this service. */
    suspend fun verify(bearerToken: String): Boolean
}
