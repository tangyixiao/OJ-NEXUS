package com.ojnexus.feature.problems

import com.ojnexus.core.model.JudgeId
import com.ojnexus.core.model.Problem
import com.ojnexus.core.model.ProblemKey
import org.junit.Assert.assertEquals
import org.junit.Test

class ProblemLibraryTrainingTest {

    @Test
    fun `empty library view produces no training IDs`() {
        assertEquals(emptyList<Long>(), buildTrainingProblemIds(emptyList()))
    }

    @Test
    fun `visible problems keep first seen unique IDs`() {
        val visible = listOf(problem(42), problem(7), problem(42), problem(19))

        assertEquals(listOf(42L, 7L, 19L), buildTrainingProblemIds(visible))
    }

    private fun problem(id: Long) = Problem(
        id = id,
        key = ProblemKey(JudgeId.CODEFORCES, id.toString()),
        title = "Problem $id",
        difficulty = 1200,
        createdAt = 0L,
        updatedAt = id,
        firstSolvedAt = null,
        lastAttemptAt = null,
        attemptCount = 0,
        solved = false,
        favorite = false,
        sourceUrl = null,
        tags = emptyList(),
        inReview = false,
    )
}
