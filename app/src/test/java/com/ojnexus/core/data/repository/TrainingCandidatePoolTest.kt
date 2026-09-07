package com.ojnexus.core.data.repository

import com.ojnexus.core.database.dao.TrainingCandidateRow
import org.junit.Assert.assertEquals
import org.junit.Test

class TrainingCandidatePoolTest {
    @Test
    fun `bucket merge keeps due and failure evidence before recent duplicates`() {
        val due = listOf(row(1L), row(2L))
        val recent = listOf(row(1L), row(9L))
        val failure = listOf(row(7L))
        val weak = listOf(row(8L))

        assertEquals(
            listOf(1L, 2L, 7L, 8L),
            mergeTrainingCandidatePools(due, recent, failure, weak, limit = 4).map { it.id },
        )
    }

    private fun row(id: Long) = TrainingCandidateRow(
        id = id,
        judge = "codeforces",
        externalId = id.toString(),
        title = "P$id",
        difficulty = null,
        solved = false,
        attemptCount = 0,
        failureCount = 0,
        reviewDue = id < 3,
    )
}
