package com.sirelon.sellsnap.http

import com.google.api.client.json.webtoken.JsonWebToken
import com.google.auth.oauth2.TokenVerifier
import com.sirelon.sellsnap.AgentFixture
import com.sirelon.sellsnap.module
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SchedulerRouteTest {
    @Test
    fun tickAndBatchRefuseARequestWithoutAValidToken() = testApplication {
        val fx = AgentFixture()
        application { module(fx.graph) }
        for (path in listOf("/tick", "/batch")) {
            assertEquals(HttpStatusCode.Unauthorized, client.postScheduler(path, token = null).status, path)
            assertEquals(HttpStatusCode.Unauthorized, client.postScheduler(path, token = "wrong").status, path)
            val basic = client.post(path) { header(HttpHeaders.Authorization, "Basic scheduler-token") }
            assertEquals(HttpStatusCode.Unauthorized, basic.status, path)
        }
    }

    @Test
    fun tickWithAValidTokenRunsAndSummarises() = testApplication {
        val fx = AgentFixture()
        fx.memory.put(com.sirelon.sellsnap.fakes.testAccount())
        application { module(fx.graph) }
        val response = client.postScheduler("/tick")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().startsWith("tick accounts=1"))
    }

    @Test
    fun batchWithAValidTokenAnswers501() = testApplication {
        val fx = AgentFixture()
        application { module(fx.graph) }
        assertEquals(HttpStatusCode.NotImplemented, client.postScheduler("/batch").status)
    }
}

class OidcClaimsTest {
    private val sa = "scheduler@sellsnap.iam.gserviceaccount.com"

    @Test
    fun bothIssuerSpellingsAreAccepted() {
        assertTrue(SchedulerClaims.accepts("https://accounts.google.com", sa, true, sa))
        assertTrue(SchedulerClaims.accepts("accounts.google.com", sa, true, sa))
    }

    @Test
    fun otherIssuersEmailsAndUnverifiedEmailsAreRefused() {
        assertFalse(SchedulerClaims.accepts("https://evil.example", sa, true, sa))
        assertFalse(SchedulerClaims.accepts(null, sa, true, sa))
        assertFalse(SchedulerClaims.accepts("accounts.google.com", "other@x.iam.gserviceaccount.com", true, sa))
        assertFalse(SchedulerClaims.accepts("accounts.google.com", null, true, sa))
        assertFalse(SchedulerClaims.accepts("accounts.google.com", sa, false, sa))
        assertFalse(SchedulerClaims.accepts("accounts.google.com", sa, null, sa))
    }

    private fun payload(issuer: String, email: String, verified: Any?) = JsonWebToken.Payload().also {
        it.issuer = issuer
        it["email"] = email
        if (verified != null) it["email_verified"] = verified
    }

    @Test
    fun theVerifierChecksClaimsOfAVerifiedToken() = runTest {
        val good = GoogleOidcVerifier("https://agent.test", sa) { payload("accounts.google.com", sa, true) }
        assertTrue(good.verify("t"))
        val otherEmail = GoogleOidcVerifier("https://agent.test", sa) { payload("accounts.google.com", "x@y.z", true) }
        assertFalse(otherEmail.verify("t"))
        val unverified = GoogleOidcVerifier("https://agent.test", sa) { payload("https://accounts.google.com", sa, false) }
        assertFalse(unverified.verify("t"))
    }

    @Test
    fun aTokenTheLibraryRejectsIsRefused() = runTest {
        val verifier = GoogleOidcVerifier("https://agent.test", sa) { throw TokenVerifier.VerificationException("bad signature") }
        assertFalse(verifier.verify("t"))
    }

    @Test
    fun bearerHeaderParsing() {
        assertEquals("abc", bearerToken("Bearer abc"))
        assertEquals("abc", bearerToken("bearer  abc "))
        assertEquals(null, bearerToken("Basic abc"))
        assertEquals(null, bearerToken("Bearer "))
        assertEquals(null, bearerToken(null))
    }
}
