package com.ojnexus.feature.settings

import com.ojnexus.core.database.dao.SyncOperationWithModules
import com.ojnexus.core.database.entity.SyncOperationEntity
import com.ojnexus.core.database.entity.SyncOperationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JudgeHealthTest {

    private fun operation(
        id: Long,
        startedAt: Long,
        finishedAt: Long?,
        status: String = SyncOperationStatus.SUCCESS.name,
        errorType: String? = null,
    ) = SyncOperationEntity(
        id = id,
        judge = "codeforces",
        accountId = 1L,
        dataGeneration = "gen-1",
        startedAt = startedAt,
        finishedAt = finishedAt,
        status = status,
        lastErrorType = errorType,
    )

    private fun row(operation: SyncOperationEntity) = SyncOperationWithModules(
        operation = operation,
        modules = emptyList(),
    )

    @Test
    fun `no finished run reports an honest null snapshot`() {
        val snapshot = deriveJudgeHealth(
            listOf(row(operation(1, startedAt = 100, finishedAt = null))),
        )

        assertNull(snapshot.lastRunStatus)
        assertNull(snapshot.lastRunErrorType)
        assertNull(snapshot.lastRunLatencyMs)
    }

    @Test
    fun `latest finished run wins over older ones and running rows`() {
        val snapshot = deriveJudgeHealth(
            listOf(
                row(operation(1, startedAt = 1_000, finishedAt = 2_000)),
                row(
                    operation(
                        2,
                        startedAt = 5_000,
                        finishedAt = 6_500,
                        status = SyncOperationStatus.PARTIAL.name,
                        errorType = "Timeout",
                    ),
                ),
                row(operation(3, startedAt = 9_000, finishedAt = null)),
            ),
        )

        assertEquals(SyncOperationStatus.PARTIAL.name, snapshot.lastRunStatus)
        assertEquals("Timeout", snapshot.lastRunErrorType)
        assertEquals(1_500L, snapshot.lastRunLatencyMs)
    }

    @Test
    fun `status label pairs the failure type only when present`() {
        val success = JudgeHealthSnapshot("SUCCESS", null, 800)
        val failed = JudgeHealthSnapshot("ERROR", "NetworkUnavailable", null)

        assertEquals("SUCCESS", judgeHealthStatusLabel(success))
        assertEquals("ERROR/NetworkUnavailable", judgeHealthStatusLabel(failed))
        assertNull(judgeHealthStatusLabel(JudgeHealthSnapshot(null, null, null)))
    }

    @Test
    fun `negative latency never happens — clamp to null`() {
        val snapshot = deriveJudgeHealth(
            listOf(row(operation(1, startedAt = 2_000, finishedAt = 1_000))),
        )

        assertNull(snapshot.lastRunLatencyMs)
    }

    @Test
    fun `latency renders honestly across unit boundaries`() {
        assertEquals("850 ms", formatJudgeHealthLatency(850))
        assertEquals("1.5 s", formatJudgeHealthLatency(1_500))
        assertEquals("61.0 s", formatJudgeHealthLatency(61_000))
        assertEquals("?", formatJudgeHealthLatency(-1))
    }
}
