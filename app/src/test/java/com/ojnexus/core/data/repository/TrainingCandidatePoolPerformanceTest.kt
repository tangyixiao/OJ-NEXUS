package com.ojnexus.core.data.repository

import com.ojnexus.core.database.dao.TrainingCandidateRow
import kotlin.system.measureNanoTime
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingCandidatePoolPerformanceTest {
    @Test
    fun `fixed candidate pool merge stays below regression threshold`() {
        val source = (1L..600L).map { id ->
            TrainingCandidateRow(
                id = id,
                judge = "codeforces",
                externalId = id.toString(),
                title = "P$id",
                difficulty = 1_000 + id.toInt(),
                solved = false,
                attemptCount = 1,
                failureCount = (id % 3).toInt(),
                reviewDue = id % 5L == 0L,
                knowledgeAreas = if (id % 2L == 0L) "GRAPH" else null,
            )
        }
        repeat(10) { mergeTrainingCandidatePools(source, source, source, source, limit = 60) }

        val elapsedNanos = measureNanoTime {
            repeat(100) {
                val merged = mergeTrainingCandidatePools(source, source, source, source, limit = 60)
                assertTrue(merged.size <= 60)
            }
        }
        val elapsedMs = elapsedNanos / 1_000_000L
        println("TrainingCandidatePoolPerformanceTest: 600 rows x 4 buckets x 100 merges = ${elapsedMs}ms")
        assertTrue("candidate pool merge exceeded 500ms: ${elapsedMs}ms", elapsedMs < 500L)
    }
}
