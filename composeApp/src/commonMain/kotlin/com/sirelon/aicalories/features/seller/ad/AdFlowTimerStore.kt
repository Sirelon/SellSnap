package com.sirelon.sellsnap.features.seller.ad

import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource

class AdFlowTimerStore {

    private var flowStartMark: TimeMark? = null
    private var generationElapsed: Duration = Duration.ZERO

    fun markFlowStartedIfNeeded() {
        if (flowStartMark != null) return

        flowStartMark = TimeSource.Monotonic.markNow()
        generationElapsed = Duration.ZERO
    }

    fun markGenerationCompleted(): Long {
        markFlowStartedIfNeeded()
        generationElapsed = flowStartMark?.elapsedNow() ?: Duration.ZERO
        return generationElapsed.inWholeMilliseconds
    }

    fun generationElapsedMs(): Long = generationElapsed.inWholeMilliseconds

    fun totalElapsedMs(): Long = flowStartMark?.elapsedNow()?.inWholeMilliseconds ?: 0L

    fun clear() {
        flowStartMark = null
        generationElapsed = Duration.ZERO
    }
}
