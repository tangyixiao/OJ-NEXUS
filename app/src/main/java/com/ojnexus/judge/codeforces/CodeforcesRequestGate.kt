package com.ojnexus.judge.codeforces

import com.ojnexus.core.network.DelayProvider
import com.ojnexus.core.network.MonotonicClock
import com.ojnexus.core.network.RateLimitedRequestGate

/**
 * Process-wide Codeforces request gate.
 *
 * The official public API allows **at most one request every 2 seconds**. Every Codeforces
 * network call in the app goes through this gate — repositories never call the transport
 * directly and never implement their own delays.
 *
 * Implementation: a mutex serializes callers; after acquiring it, each caller waits until
 * [minimumIntervalMs] has elapsed since the previous request STARTED (not finished), measured
 * with a monotonic clock (wall-clock jumps cannot violate the limit). The wait is injected
 * so tests never actually sleep.
 */
class CodeforcesRequestGate(
    minimumIntervalMs: Long = 2_100,
    clock: MonotonicClock,
    delayProvider: DelayProvider,
) {
    private val delegate = RateLimitedRequestGate(minimumIntervalMs, clock, delayProvider)

    suspend fun <T> execute(block: suspend () -> T): T = delegate.execute(block)
}
