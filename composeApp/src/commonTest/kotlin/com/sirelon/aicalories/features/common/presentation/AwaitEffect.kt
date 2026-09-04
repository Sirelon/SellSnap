package com.sirelon.sellsnap.features.common.presentation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first

/**
 * Awaits the next [T] effect from a [BaseViewModel].
 *
 * Draining the test scheduler cannot see these. Any effect whose text comes from
 * compose-resources `getString` is posted only after that resolves on its own dispatcher, so
 * `advanceUntilIdle()` and `runCurrent()` both return first and the assertion reads an empty
 * list. The effects channel is buffered, so awaiting cannot miss one already sent.
 *
 * Deliberately no timeout: `withTimeout` inside `runTest` runs on the virtual clock and fires the
 * instant the scheduler idles, which is exactly the moment this needs to keep waiting. `runTest`'s
 * own real-time watchdog is the failure mode if the effect never arrives.
 *
 * This trap was rediscovered five times before the helper existed. Use it rather than
 * drain-then-assert. For state rather than an effect, await the state the same way:
 * `viewModel.state.first { it.thing != null }`.
 *
 * Usage: `viewModel.effects.awaitEffect<Effect.ShowMessage>()`.
 */
suspend inline fun <reified T : Any> Flow<Any>.awaitEffect(): T = filterIsInstance<T>().first()
