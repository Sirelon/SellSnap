package com.sirelon.sellsnap.model

/**
 * One model's counters for one UTC day, kept in `social_spend/{yyyymmdd}` under `models.{model}`.
 * Model calls and tokens only; the agent runs on a free tier and stores no money.
 */
data class UsageCounters(
    val calls: Long = 0,
    val promptTokens: Long = 0,
    val outputTokens: Long = 0,
    val thoughtsTokens: Long = 0,
)
