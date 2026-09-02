package com.ojnexus.feature.problems

import com.ojnexus.core.model.JudgeId
import com.ojnexus.core.model.Problem
import com.ojnexus.core.model.ProblemKey
import org.junit.Assert.assertEquals
import org.junit.Test

class ProblemLibrarySummaryTest {
    private fun problem(
        id: Long,
        solved: Boolean = false,
        attemptCount: Int = 0,
        inReview: Boolean = false,
        favorite: Boolean = false,
    ) = Problem(
        id = id,
        key = ProblemKey(JudgeId.CODEFORCES, "P$id"),
        title = "Problem $id",
        difficulty = 1200,
        createdAt = id,
        updatedAt = id,
        firstSolvedAt = null,
        lastAttemptAt = null,
        attemptCount = attemptCount,
        solved = solved,
        favorite = favorite,
        sourceUrl = null,
        tags = emptyList(),
        inReview = inReview,
    )

    @Test
    fun `summary counts complete library and current visible rows`() {
        val all = listOf(
            problem(1L, solved = true, favorite = true),
            problem(2L, attemptCount = 2),
            problem(3L, solved = true, inReview = true),
        )

        assertEquals(
            ProblemLibrarySummary(total = 3, visible = 2, solved = 2, review = 1, favorites = 1),
            summarizeProblemLibrary(all, all.take(2)),
        )
    }

    @Test
    fun `summary uses derived review status and accepts empty lists`() {
        val reviewedSolved = problem(7L, solved = true, inReview = true)

        assertEquals(
            ProblemLibrarySummary(total = 1, visible = 0, solved = 1, review = 1, favorites = 0),
            summarizeProblemLibrary(listOf(reviewedSolved), emptyList()),
        )
        assertEquals(
            ProblemLibrarySummary(total = 0, visible = 0, solved = 0, review = 0, favorites = 0),
            summarizeProblemLibrary(emptyList(), emptyList()),
        )
    }

    @Test
    fun `default view predicate is false for active filter or non-default sort`() {
        assertEquals(false, isProblemLibraryDefaultView(ProblemFilter(query = "tree"), ProblemSort.UPDATED))
        assertEquals(false, isProblemLibraryDefaultView(ProblemFilter(), ProblemSort.TITLE))
        assertEquals(true, isProblemLibraryDefaultView(ProblemFilter(), ProblemSort.UPDATED))
    }
}
