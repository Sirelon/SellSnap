package com.sirelon.sellsnap

import com.sirelon.sellsnap.content.DEFAULT_POST_LINK_TEMPLATE
import com.sirelon.sellsnap.content.expandLinkTemplate

/** Runtime settings read from environment variables. Secrets arrive through Cloud Run secret injection. */
class Config(
    val port: Int,
    val telegramBotToken: String,
    val threadsAppSecret: String,
    val telegramWebhookSecret: String,
    val ownerTelegramId: Long,
    val schedulerSaEmail: String,
    val oidcAudience: String,
    val publicBaseUrl: String,
    /** The post link template with `{PUBLIC_BASE_URL}` filled in; still holds `{code}` and `{lang}`. */
    val postLinkTemplate: String,
    val storageBucket: String,
    val playPackage: String,
    val appStoreId: String,
    val appleProviderToken: String?,
    val landingUrl: String,
    val geminiApiKey: String,
    /** The model that drafts posts. */
    val draftModel: String = DEFAULT_DRAFT_MODEL,
    /** The model that checks drafts. */
    val checkModel: String = DEFAULT_CHECK_MODEL,
    /** Calls per model per UTC day that the free tier allows; drafting stops at 90% of it. */
    val dailyCallLimit: Int = DEFAULT_DAILY_CALL_LIMIT,
) {
    /** Leaves out every secret field. */
    override fun toString(): String = "Config(port=$port, publicBaseUrl=$publicBaseUrl)"

    companion object {
        const val DEFAULT_PORT = 8080
        const val DEFAULT_DRAFT_MODEL = "gemini-3.8-flash"
        const val DEFAULT_CHECK_MODEL = "gemini-3.5-flash-lite"
        const val DEFAULT_DAILY_CALL_LIMIT = 200

        /** Fails naming every missing or malformed variable, never printing a value. */
        fun fromEnv(env: Map<String, String> = System.getenv()): Config {
            val problems = mutableListOf<String>()

            fun optional(name: String): String? = env[name]?.trim()?.takeIf { it.isNotEmpty() }

            fun required(name: String): String =
                optional(name) ?: "".also { problems += "$name is missing" }

            val port = optional("PORT")?.let { raw ->
                raw.toIntOrNull() ?: DEFAULT_PORT.also { problems += "PORT is not a number" }
            } ?: DEFAULT_PORT
            val ownerId = optional("OWNER_TELEGRAM_ID")?.let { raw ->
                raw.toLongOrNull() ?: 0L.also { problems += "OWNER_TELEGRAM_ID is not a number" }
            } ?: 0L.also { problems += "OWNER_TELEGRAM_ID is missing" }

            val dailyCallLimit = optional("DAILY_CALL_LIMIT")?.let { raw ->
                raw.toIntOrNull()?.takeIf { it > 0 }
                    ?: DEFAULT_DAILY_CALL_LIMIT.also { problems += "DAILY_CALL_LIMIT is not a positive number" }
            } ?: DEFAULT_DAILY_CALL_LIMIT

            val publicBaseUrl = required("PUBLIC_BASE_URL")
            val linkTemplate = expandLinkTemplate(optional("POST_LINK_TEMPLATE") ?: DEFAULT_POST_LINK_TEMPLATE, publicBaseUrl)
            if ("{code}" !in linkTemplate) problems += "POST_LINK_TEMPLATE has no {code}"

            val config = Config(
                port = port,
                telegramBotToken = required("TELEGRAM_BOT_TOKEN"),
                threadsAppSecret = required("THREADS_APP_SECRET"),
                telegramWebhookSecret = required("TELEGRAM_WEBHOOK_SECRET"),
                ownerTelegramId = ownerId,
                schedulerSaEmail = required("SCHEDULER_SA_EMAIL"),
                oidcAudience = required("OIDC_AUDIENCE"),
                publicBaseUrl = publicBaseUrl,
                postLinkTemplate = linkTemplate,
                storageBucket = required("STORAGE_BUCKET"),
                playPackage = required("PLAY_PACKAGE"),
                appStoreId = required("APP_STORE_ID"),
                appleProviderToken = optional("APPLE_PROVIDER_TOKEN"),
                landingUrl = required("LANDING_URL"),
                geminiApiKey = required("GEMINI_API_KEY"),
                draftModel = optional("DRAFT_MODEL") ?: DEFAULT_DRAFT_MODEL,
                checkModel = optional("CHECK_MODEL") ?: DEFAULT_CHECK_MODEL,
                dailyCallLimit = dailyCallLimit,
            )
            check(problems.isEmpty()) { "Invalid configuration: ${problems.joinToString("; ")}" }
            return config
        }
    }
}
