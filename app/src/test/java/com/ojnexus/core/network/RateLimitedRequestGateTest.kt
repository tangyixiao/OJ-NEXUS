package com.ojnexus.core.network

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class GateClock : MonotonicClock {
    var now = 0L
    override fun nowMs(): Long = now
}

private class AdvancingDelay(
    private val clock: GateClock,
) : DelayProvider {
    val delays = mutableListOf<Long>()

    override suspend fun delayMs(ms: Long) {
        delays += ms
        clock.now += ms
    }
}

class RateLimitedRequestGateTest {

    @Test
    fun `two gate instances do not share timing state`() = runBlocking {
        val clock = GateClock()
        val delays = AdvancingDelay(clock)
        val codeforces = RateLimitedRequestGate(2_100, clock, delays)
        val atCoder = RateLimitedRequestGate(1_100, clock, delays)

        codeforces.execute { "cf" }
        atCoder.execute { "atcoder" }

        assertTrue(delays.delays.isEmpty())
    }

    @Test
    fun `each instance enforces its own configured interval`() = runBlocking {
        val clock = GateClock()
        val delays = AdvancingDelay(clock)
        val codeforces = RateLimitedRequestGate(2_100, clock, delays)
        val atCoder = RateLimitedRequestGate(1_100, clock, delays)

        codeforces.execute { 1 }
        codeforces.execute { 2 }
        atCoder.execute { 3 }
        atCoder.execute { 4 }

        assertEquals(listOf(2_100L, 1_100L), delays.delays)
    }
}
