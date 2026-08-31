package com.ojnexus.judge.luogu.open

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LuoguOpenResultPollingTest {

    @Test
    fun `matching signal causes an immediate authoritative fetch`() = runBlocking {
        val responses = ArrayDeque<LuoguOpenResult>(
            listOf(
                LuoguOpenResult.Pending,
                readyResult(),
            ),
        )
        var fetchCount = 0
        val delays = mutableListOf<Long>()

        val result = pollLuoguOpenResult(
            requestId = "req-signal",
            fetch = {
                fetchCount += 1
                responses.removeFirst()
            },
            delayForResult = { delays += it },
            awaitResultSignal = { _, _ -> true },
        )

        assertTrue(result is LuoguOpenResult.Ready)
        assertEquals(2, fetchCount)
        assertEquals(emptyList<Long>(), delays)
    }

    @Test
    fun `false signal keeps bounded HTTP polling as fallback`() = runBlocking {
        val responses = ArrayDeque<LuoguOpenResult>(
            listOf(
                LuoguOpenResult.Pending,
                LuoguOpenResult.Pending,
                readyResult(),
            ),
        )
        var fetchCount = 0
        val delays = mutableListOf<Long>()

        val result = pollLuoguOpenResult(
            requestId = "req-fallback",
            fetch = {
                fetchCount += 1
                responses.removeFirst()
            },
            delayForResult = { delays += it },
            awaitResultSignal = { _, _ -> false },
        )

        assertTrue(result is LuoguOpenResult.Ready)
        assertEquals(3, fetchCount)
        assertEquals(listOf(1_000L, 1_000L), delays)
    }

    private fun readyResult() = LuoguOpenResult.Ready(
        LuoguOpenEvaluation(
            requestId = "req-ready",
            trackId = null,
            type = "judge",
            compileSuccess = true,
            compileMessage = null,
            status = 12,
            score = 100,
            timeMs = 1,
            memoryKiB = 1,
            output = null,
            exitCode = null,
        ),
    )
}
