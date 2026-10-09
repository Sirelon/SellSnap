package com.sirelon.sellsnap.threads

private const val MASK = "[redacted]"

private val QUERY_SECRET = Regex("(access_token|input_token|client_secret)(=|%3[Dd])[^&\\s\"'<>]+")
private val JSON_SECRET = Regex("(\"(?:access_token|input_token|client_secret)\"\\s*:\\s*\")[^\"]*(\")")
private val BOT_TOKEN_IN_URL = Regex("bot\\d+:[A-Za-z0-9_-]+")
private val BARE_BOT_TOKEN = Regex("\\b\\d{6,}:[A-Za-z0-9_-]{30,}")

/**
 * Masks Threads tokens, the app secret and Telegram bot tokens in [text]. Every log line and every
 * error reply that could carry a request detail passes through here.
 */
fun redact(text: String): String = text
    .replace(QUERY_SECRET) { "${it.groupValues[1]}${it.groupValues[2]}$MASK" }
    .replace(JSON_SECRET) { "${it.groupValues[1]}$MASK${it.groupValues[2]}" }
    .replace(BOT_TOKEN_IN_URL, "bot$MASK")
    .replace(BARE_BOT_TOKEN, MASK)
