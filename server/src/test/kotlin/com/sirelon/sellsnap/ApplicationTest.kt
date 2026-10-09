package com.sirelon.sellsnap

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApplicationTest {
    @Test
    fun stubRoutesAnswerNotImplemented() = testApplication {
        application { module() }
        assertEquals(HttpStatusCode.NotImplemented, client.post("/tick").status)
        assertEquals(HttpStatusCode.NotImplemented, client.post("/batch").status)
        assertEquals(HttpStatusCode.NotImplemented, client.post("/telegram").status)
        assertEquals(HttpStatusCode.NotImplemented, client.get("/go/stt0001").status)
    }

    @Test
    fun configReadsEnvAndDefaultsPort() {
        val config = Config.fromEnv(validEnv)
        assertEquals(8080, config.port)
        assertEquals(42L, config.ownerTelegramId)
        assertNull(config.appleProviderToken)
        assertEquals(9090, Config.fromEnv(validEnv + ("PORT" to "9090")).port)
    }

    @Test
    fun configFailsFastNamingMissingVariablesOnly() {
        val error = assertFailsWith<IllegalStateException> {
            Config.fromEnv(validEnv - "OIDC_AUDIENCE" - "TELEGRAM_BOT_TOKEN")
        }
        val message = error.message.orEmpty()
        assertTrue("OIDC_AUDIENCE" in message && "TELEGRAM_BOT_TOKEN" in message)
        assertTrue("secret-value" !in message)
    }

    @Test
    fun configFailsOnMissingOwnerId() {
        val error = assertFailsWith<IllegalStateException> { Config.fromEnv(validEnv - "OWNER_TELEGRAM_ID") }
        assertTrue("OWNER_TELEGRAM_ID" in error.message.orEmpty())
    }

    @Test
    fun configToStringHidesSecrets() {
        assertTrue("secret-value" !in Config.fromEnv(validEnv).toString())
    }

    private val validEnv = mapOf(
        "TELEGRAM_BOT_TOKEN" to "secret-value",
        "THREADS_APP_SECRET" to "secret-value",
        "TELEGRAM_WEBHOOK_SECRET" to "secret-value",
        "OWNER_TELEGRAM_ID" to "42",
        "SCHEDULER_SA_EMAIL" to "scheduler@example.iam.gserviceaccount.com",
        "OIDC_AUDIENCE" to "https://agent.example.run.app",
        "PUBLIC_BASE_URL" to "https://agent.example.run.app",
        "STORAGE_BUCKET" to "bucket",
        "PLAY_PACKAGE" to "com.sirelon.sellsnap",
        "APP_STORE_ID" to "6776001373",
        "LANDING_URL" to "https://example.com",
    )
}
