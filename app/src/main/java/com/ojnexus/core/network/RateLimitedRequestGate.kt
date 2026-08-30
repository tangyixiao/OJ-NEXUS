package com.ojnexus.core.network

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Reusable request-start spacing mechanism. Each data source owns a separate instance,
 * mutex, and timestamp so unrelated judges never block each other.
 */
class RateLimitedRequestGate(
    private val minimumIntervalMs: Long,
    private val clock: MonotonicClock,
    private val delayProvider: DelayProvider,
) {
    private val mutex = Mutex()
    private var lastRequestStartMs: Long? = null

    init {
        require(minimumIntervalMs >= 0) { "minimum interval must be non-negative" }
    }

    suspend fun <T> execute(block: suspend () -> T): T = mutex.withLock {
        val last = lastRequestStartMs
        if (last != null) {
            val waitMs = last + minimumIntervalMs - clock.nowMs()
            if (waitMs > 0) delayProvider.delayMs(waitMs)
        }
        lastRequestStartMs = clock.nowMs()
        block()
    }
}
