package com.ojnexus.core.network

/**
 * Monotonic time source for interval bookkeeping. Wall-clock time can jump (user or NTP
 * adjustment) and must never be used for rate-limit spacing; [android.os.SystemClock.elapsedRealtime]
 * is the Android source of truth. Abstracted so tests can advance time without sleeping.
 */
interface MonotonicClock {
    fun nowMs(): Long
}

class SystemMonotonicClock : MonotonicClock {
    override fun nowMs(): Long = android.os.SystemClock.elapsedRealtime()
}

/**
 * Suspension-based delay abstraction. The request gate uses it to wait out rate-limit
 * intervals; tests inject an immediate implementation and assert on bookkeeping instead.
 */
interface DelayProvider {
    suspend fun delayMs(ms: Long)
}

class CoroutineDelayProvider : DelayProvider {
    override suspend fun delayMs(ms: Long) {
        kotlinx.coroutines.delay(ms)
    }
}
