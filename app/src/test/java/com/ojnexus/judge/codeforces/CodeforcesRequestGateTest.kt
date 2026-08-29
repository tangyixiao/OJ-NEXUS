package com.ojnexus.judge.codeforces

import com.ojnexus.core.network.DelayProvider
import com.ojnexus.core.network.MonotonicClock
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeMonotonicClock(startMs: Long = 0L) : MonotonicClock {
    private var now = startMs
    val advancedBy: Long get() = now
    override fun nowMs(): Long = now
    fun advanceBy(ms: Long) {
        now += ms
    }
}

private class FakeDelayProvider : DelayProvider {
    val delays = mutableListOf<Long>()
    override suspend fun delayMs(ms: Long) {
        delays += ms
    }
}

class CodeforcesRequestGateTest {

    @Test
    fun `first request passes immediately without delay`() = runBlocking {
        val clock = FakeMonotonicClock()
        val delays = FakeDelayProvider()
        val gate = CodeforcesRequestGate(minimumIntervalMs = 2_100, clock = clock, delayProvider = delays)

        val result = gate.execute { "ok" }

        assertEquals("ok", result)
        assertTrue(delays.delays.isEmpty())
    }

    @Test
    fun `second request inside the interval waits out the remaining time`() = runBlocking {
        val clock = FakeMonotonicClock()
        val delays = FakeDelayProvider()
        val gate = CodeforcesRequestGate(minimumIntervalMs = 2_100, clock = clock, delayProvider = delays)

        gate.execute { 1 }
        clock.advanceBy(500)
        gate.execute { 2 }

        // 2100 - 500 = 1600 ms remaining.
        assertEquals(listOf(1_600L), delays.delays)
    }

    @Test
    fun `request after the interval has fully elapsed does not wait`() = runBlocking {
        val clock = FakeMonotonicClock()
        val delays = FakeDelayProvider()
        val gate = CodeforcesRequestGate(minimumIntervalMs = 2_100, clock = clock, delayProvider = delays)

        gate.execute { 1 }
        clock.advanceBy(2_100)
        gate.execute { 2 }

        assertTrue(delays.delays.isEmpty())
    }

    @Test
    fun `concurrent requests are serialized with proper spacing`() = runBlocking {
        val clock = FakeMonotonicClock()
        val delays = FakeDelayProvider()
        val gate = CodeforcesRequestGate(minimumIntervalMs = 2_100, clock = clock, delayProvider = delays)

        val order = mutableListOf<Int>()
        (1..3).map { id ->
            async {
                gate.execute {
                    order += id
                    id
                }
            }
        }.awaitAll()

        assertEquals(listOf(1, 2, 3), order)
        // Requests 2 and 3 each waited one full interval under the fake clock.
        assertEquals(listOf(2_100L, 2_100L), delays.delays)
    }

    @Test
    fun `gate result value is returned untouched`() = runBlocking {
        val gate = CodeforcesRequestGate(2_100, FakeMonotonicClock(), FakeDelayProvider())
        assertEquals(42, gate.execute { 42 })
    }
}
