package com.ojnexus.judge.codeforces

import com.ojnexus.core.network.DelayProvider
import com.ojnexus.core.network.MonotonicClock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    private val minimumIntervalMs: Long = 2_100,
    private val clock: MonotonicClock,
    private val delayProvider: DelayProvider,
) {

    private val mutex = Mutex()
    private var lastRequestStartMs: Long? = null

    suspend fun <T> execute(block: suspend () -> T): T = mutex.withLock {
        val now = clock.nowMs()
        val last = lastRequestStartMs
        if (last != null) {
            val earliestAllowed = last + minimumIntervalMs
            if (now < earliestAllowed) {
                delayProvider.delayMs(earliestAllowed - now)
            }
        }
        lastRequestStartMs = clock.nowMs()
        block()
    }
}
