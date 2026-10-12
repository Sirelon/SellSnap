package com.sirelon.sellsnap.spend

import com.sirelon.sellsnap.llm.LlmUsage
import com.sirelon.sellsnap.model.UsageCounters
import com.sirelon.sellsnap.store.SocialStore
import java.time.Clock
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Counts model calls and tokens per model per UTC day in `social_spend/{yyyymmdd}` and tells the
 * drafting code when a model nears its free-tier daily call limit. [dailyCallLimit] applies to each
 * model on its own. Nothing here is money.
 */
class UsageTracker(
    private val store: SocialStore,
    private val clock: Clock,
    val dailyCallLimit: Int,
) {
    /** Adds one call and its tokens to today's counters of [usage]'s model. */
    suspend fun record(usage: LlmUsage) {
        store.addUsage(
            day = today(),
            model = usage.model,
            delta = UsageCounters(
                calls = 1,
                promptTokens = usage.promptTokens,
                outputTokens = usage.outputTokens,
                thoughtsTokens = usage.thoughtsTokens,
            ),
        )
    }

    suspend fun callsToday(model: String): Long = store.usageOf(today())[model]?.calls ?: 0

    /** Every model's calls today, added up. */
    suspend fun callsToday(): Long = store.usageOf(today()).values.sumOf { it.calls }

    /** False once [model] has used 90% of the daily call limit, so the batch stops before the API answers 429. */
    suspend fun canCall(model: String): Boolean = callsToday(model) * 10 < dailyCallLimit.toLong() * 9

    private fun today(): String = clock.instant().atOffset(ZoneOffset.UTC).format(DAY)

    private companion object {
        val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
    }
}
