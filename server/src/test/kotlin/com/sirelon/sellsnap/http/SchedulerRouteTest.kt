package com.sirelon.sellsnap.http

import com.google.api.client.json.webtoken.JsonWebToken
import com.google.auth.oauth2.TokenVerifier
import com.sirelon.sellsnap.AgentFixture
import com.sirelon.sellsnap.fakes.DraftingLlm
import com.sirelon.sellsnap.fakes.testAccount
import com.sirelon.sellsnap.model.BatchAccount
import com.sirelon.sellsnap.model.SocialBatch
import com.sirelon.sellsnap.module
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.test.runTest
import java.time.Instant
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

    private val sunday = Instant.parse("2026-10-18T06:00:00Z")

    private fun batchFixture(llm: DraftingLlm = DraftingLlm()) = AgentFixture(sunday, llm = llm.client).also {
        it.memory.put(it.publishable(testAccount(id = "ua", codePrefix = "uk", language = "uk")))
        it.memory.put(it.publishable(testAccount(id = "pl", codePrefix = "pl", language = "pl", timezone = "Europe/Warsaw")))
    }

    @Test
    fun batchWithNothingToDraftAnswers200() = testApplication {
        val fx = AgentFixture(sunday)
        application { module(fx.graph) }

        val response = client.postScheduler("/batch")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("batch 2026-W43 done", response.bodyAsText())
    }

    @Test
    fun batchDraftsEveryAccountAndAnswers200ThenAlreadyDone() = testApplication {
        val fx = batchFixture()
        application { module(fx.graph) }

        val first = client.postScheduler("/batch")
        assertEquals(HttpStatusCode.OK, first.status)
        val summary = first.bodyAsText()
        assertTrue(summary.startsWith("batch 2026-W43 done "), summary)
        assertTrue("ua=done(written=12,failed=0)" in summary && "pl=done(written=12,failed=0)" in summary, summary)
        assertEquals(24, fx.memory.allPosts.size)

        val second = client.postScheduler("/batch")
        assertEquals(HttpStatusCode.OK, second.status)
        assertEquals("batch 2026-W43 already done", second.bodyAsText())
        assertEquals(24, fx.memory.allPosts.size)
    }

    @Test
    fun batchWithAnAccountsBodyDraftsOnlyThoseAccounts() = testApplication {
        val fx = batchFixture()
        application { module(fx.graph) }

        val response = client.postScheduler("/batch", body = """{"accounts":["ua"]}""")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(fx.memory.allPosts.values.all { it.accountId == "ua" })
        assertEquals(12, fx.memory.allPosts.size)
    }

    @Test
    fun batchAnswers409WhileAnotherRunHoldsTheLease() = testApplication {
        val fx = batchFixture()
        fx.memory.put(
            SocialBatch("2026-W43", runId = "other", leaseUntil = sunday.plusSeconds(100), startedAt = sunday, accounts = mapOf("ua" to BatchAccount(), "pl" to BatchAccount())),
        )
        application { module(fx.graph) }

        val response = client.postScheduler("/batch")

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals("batch 2026-W43 running", response.bodyAsText())
        assertTrue(fx.memory.allPosts.isEmpty())
    }

    @Test
    fun batchAnswers503WhenTheModelIsBusySoSchedulerRetries() = testApplication {
        val llm = DraftingLlm().also { it.failOnCall += 1 }
        val fx = batchFixture(llm)
        application { module(fx.graph) }

        val response = client.postScheduler("/batch", body = """{"accounts":["ua"]}""")

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertTrue(response.bodyAsText().startsWith("batch 2026-W43 deferred ua=deferred"))

        // The retry finishes the account.
        val retry = client.postScheduler("/batch", body = """{"accounts":["ua"]}""")
        assertEquals(HttpStatusCode.OK, retry.status)
    }

    @Test
    fun batchAnswers400ForABodyThatIsNotAnAccountList() = testApplication {
        val fx = batchFixture()
        application { module(fx.graph) }

        for (body in listOf("{", """{"accounts":"ua"}""", """{"accounts":[]}""", """{"accounts":[1]}""", """["ua"]""")) {
            assertEquals(HttpStatusCode.BadRequest, client.postScheduler("/batch", body = body).status, body)
        }
        assertTrue(fx.memory.allPosts.isEmpty())
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
