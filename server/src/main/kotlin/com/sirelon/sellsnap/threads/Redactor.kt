package com.sirelon.sellsnap.threads

private const val MASK = "[redacted]"

private val QUERY_SECRET = Regex("(access_token|input_token|client_secret)(=|%3[Dd])[^&\\s\"'<>]+")
private val JSON_SECRET = Regex("(\"(?:access_token|input_token|client_secret)\"\\s*:\\s*\")[^\"]*(\")")
private val BOT_TOKEN_IN_URL = Regex("bot\\d+:[A-Za-z0-9_-]+")
private val BARE_BOT_TOKEN = Regex("\\b\\d{6,}:[A-Za-z0-9_-]{30,}")
private val GOOGLE_API_KEY = Regex("AIza[0-9A-Za-z_-]{35}")
private val GOOGLE_AUTH_KEY = Regex("AQ\\.[A-Za-z0-9_-]{20,}")
private val GOOGLE_KEY_HEADER = Regex("(?i)(x-goog-api-key\\s*:\\s*)[^\\s\"'<>,]+")

/**
 * Masks Threads tokens, the app secret, Telegram bot tokens and Google API keys in [text]. Every log line and every
 * error reply that could carry a request detail passes through here.
 */
fun redact(text: String): String = text
    .replace(QUERY_SECRET) { "${it.groupValues[1]}${it.groupValues[2]}$MASK" }
    .replace(JSON_SECRET) { "${it.groupValues[1]}$MASK${it.groupValues[2]}" }
    .replace(BOT_TOKEN_IN_URL, "bot$MASK")
    .replace(BARE_BOT_TOKEN, MASK)
    .replace(GOOGLE_KEY_HEADER) { "${it.groupValues[1]}$MASK" }
    .replace(GOOGLE_API_KEY, MASK)
    .replace(GOOGLE_AUTH_KEY, MASK)
