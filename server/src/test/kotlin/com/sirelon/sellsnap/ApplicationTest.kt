package com.sirelon.sellsnap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApplicationTest {
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
    fun postLinkTemplateDefaultsToTheRedirect() {
        assertEquals("https://agent.example.run.app/go/{code}", Config.fromEnv(validEnv).postLinkTemplate)
    }

    @Test
    fun postLinkTemplateIsReadFromEnvAndKeepsItsPlaceholders() {
        val website = "https://sirelon.github.io/SellSnap/?lang={lang}&p={code}"
        assertEquals(website, Config.fromEnv(validEnv + ("POST_LINK_TEMPLATE" to website)).postLinkTemplate)
        assertEquals(
            "https://agent.example.run.app/r/{code}?l={lang}",
            Config.fromEnv(validEnv + ("POST_LINK_TEMPLATE" to "{PUBLIC_BASE_URL}/r/{code}?l={lang}")).postLinkTemplate,
        )
    }

    @Test
    fun postLinkTemplateWithoutACodeIsRejected() {
        val error = assertFailsWith<IllegalStateException> {
            Config.fromEnv(validEnv + ("POST_LINK_TEMPLATE" to "https://sirelon.github.io/SellSnap/"))
        }
        assertTrue("POST_LINK_TEMPLATE" in error.message.orEmpty())
    }

    @Test
    fun configFailsOnMissingOwnerId() {
        val error = assertFailsWith<IllegalStateException> { Config.fromEnv(validEnv - "OWNER_TELEGRAM_ID") }
        assertTrue("OWNER_TELEGRAM_ID" in error.message.orEmpty())
    }

    @Test
    fun configRequiresTheGeminiKeyAndDefaultsModelsAndLimit() {
        val error = assertFailsWith<IllegalStateException> { Config.fromEnv(validEnv - "GEMINI_API_KEY") }
        assertTrue("GEMINI_API_KEY" in error.message.orEmpty())

        val config = Config.fromEnv(validEnv)
        assertEquals("gemini-3.8-flash", config.draftModel)
        assertEquals("gemini-3.5-flash-lite", config.checkModel)
        assertEquals(200, config.dailyCallLimit)
    }

    @Test
    fun configReadsModelsAndLimitFromEnv() {
        val config = Config.fromEnv(
            validEnv + mapOf("DRAFT_MODEL" to "gemini-x", "CHECK_MODEL" to "gemini-y", "DAILY_CALL_LIMIT" to "50"),
        )
        assertEquals("gemini-x", config.draftModel)
        assertEquals("gemini-y", config.checkModel)
        assertEquals(50, config.dailyCallLimit)
        assertFailsWith<IllegalStateException> { Config.fromEnv(validEnv + ("DAILY_CALL_LIMIT" to "0")) }
        assertFailsWith<IllegalStateException> { Config.fromEnv(validEnv + ("DAILY_CALL_LIMIT" to "many")) }
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
        "GEMINI_API_KEY" to "secret-value",
    )
}
