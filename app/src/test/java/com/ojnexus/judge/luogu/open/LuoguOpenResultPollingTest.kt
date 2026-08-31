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

    @Test
    fun `partial result continues polling until a terminal result arrives`() = runBlocking {
        val responses = ArrayDeque<LuoguOpenResult>(
            listOf(
                LuoguOpenResult.Pending,
                inProgressResult(status = 0),
                readyResult(),
            ),
        )
        var fetchCount = 0

        val result = pollLuoguOpenResult(
            requestId = "req-partial",
            fetch = {
                fetchCount += 1
                responses.removeFirst()
            },
            delayForResult = {},
            awaitResultSignal = { _, _ -> false },
        )

        assertTrue(result is LuoguOpenResult.Ready)
        assertEquals(3, fetchCount)
    }

    @Test
    fun `bounded polling returns latest partial result when still in progress`() = runBlocking {
        var fetchCount = 0

        val result = pollLuoguOpenResult(
            requestId = "req-partial",
            fetch = {
                fetchCount += 1
                inProgressResult(status = fetchCount)
            },
            delayForResult = {},
            awaitResultSignal = { _, _ -> false },
        )

        assertTrue(result is LuoguOpenResult.InProgress)
        assertEquals(8, fetchCount)
        assertEquals(8, (result as LuoguOpenResult.InProgress).evaluation.status)
    }

    private fun inProgressResult(status: Int) = LuoguOpenResult.InProgress(
        LuoguOpenEvaluation(
            requestId = "req-partial",
            trackId = null,
            type = "judge",
            compileSuccess = true,
            compileMessage = "compiled",
            status = status,
            score = null,
            timeMs = null,
            memoryKiB = null,
            output = null,
            exitCode = null,
        ),
    )

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
