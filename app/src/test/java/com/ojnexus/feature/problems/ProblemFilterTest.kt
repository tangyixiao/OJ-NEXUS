package com.ojnexus.feature.problems

import com.ojnexus.core.model.JudgeId
import com.ojnexus.core.model.Problem
import com.ojnexus.core.model.ProblemKey
import com.ojnexus.core.model.ProblemStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ProblemFilterTest {

    private fun problem(
        id: Long,
        judge: JudgeId = JudgeId.CODEFORCES,
        externalId: String = "$id",
        title: String = "Problem $id",
        difficulty: Int? = 1500,
        attemptCount: Int = 0,
        solved: Boolean = false,
        favorite: Boolean = false,
        inReview: Boolean = false,
        tags: List<String> = emptyList(),
        updatedAt: Long = id,
    ) = Problem(
        id = id,
        key = ProblemKey(judge, externalId),
        title = title,
        difficulty = difficulty,
        createdAt = 0,
        updatedAt = updatedAt,
        firstSolvedAt = null,
        lastAttemptAt = null,
        attemptCount = attemptCount,
        solved = solved,
        favorite = favorite,
        sourceUrl = null,
        tags = tags,
        inReview = inReview,
    )

    private val library = listOf(
        problem(1, title = "Alpha Tree", difficulty = 1900, attemptCount = 3, solved = true, tags = listOf("trees")),
        problem(2, title = "Beta Query", judge = JudgeId.LUOGU, externalId = "P1000", difficulty = null, attemptCount = 1, tags = listOf("dp")),
        problem(3, title = "Gamma Dp", difficulty = 2200, favorite = true, inReview = true, updatedAt = 99),
        problem(4, title = "Delta Easy", difficulty = 800, solved = true),
    )

    @Test
    fun `no filter keeps everything`() {
        assertEquals(4, library.applyFilterSort(ProblemFilter(), ProblemSort.UPDATED).size)
    }

    @Test
    fun `query matches title case-insensitively`() {
        val result = library.applyFilterSort(ProblemFilter(query = "alpha"), ProblemSort.UPDATED)
        assertEquals(listOf(1L), result.map { it.id })
    }

    @Test
    fun `query matches external id`() {
        val result = library.applyFilterSort(ProblemFilter(query = "p1000"), ProblemSort.UPDATED)
        assertEquals(listOf(2L), result.map { it.id })
    }

    @Test
    fun `status filter uses derived status`() {
        val solved = library.applyFilterSort(ProblemFilter(status = ProblemStatus.SOLVED), ProblemSort.UPDATED)
        assertEquals(setOf(1L, 4L), solved.map { it.id }.toSet())

        val review = library.applyFilterSort(ProblemFilter(status = ProblemStatus.REVIEW), ProblemSort.UPDATED)
        assertEquals(listOf(3L), review.map { it.id })

        val attempted = library.applyFilterSort(ProblemFilter(status = ProblemStatus.ATTEMPTED), ProblemSort.UPDATED)
        assertEquals(listOf(2L), attempted.map { it.id })
    }

    @Test
    fun `judge filter`() {
        val result = library.applyFilterSort(ProblemFilter(judge = JudgeId.LUOGU), ProblemSort.UPDATED)
        assertEquals(listOf(2L), result.map { it.id })
    }

    @Test
    fun `favorite and tag filters`() {
        val favorite = library.applyFilterSort(ProblemFilter(favoriteOnly = true), ProblemSort.UPDATED)
        assertEquals(listOf(3L), favorite.map { it.id })

        val tagged = library.applyFilterSort(ProblemFilter(tag = "trees"), ProblemSort.UPDATED)
        assertEquals(listOf(1L), tagged.map { it.id })
    }

    @Test
    fun `criteria are AND-combined`() {
        val result = library.applyFilterSort(
            ProblemFilter(status = ProblemStatus.SOLVED, judge = JudgeId.LUOGU),
            ProblemSort.UPDATED,
        )
        assertEquals(0, result.size)
    }

    @Test
    fun `difficulty sort places unknown last ascending`() {
        val result = library.applyFilterSort(ProblemFilter(), ProblemSort.DIFFICULTY)
        assertEquals(listOf(4L, 1L, 3L, 2L), result.map { it.id })
    }

    @Test
    fun `title sort is case-insensitive alphabetical`() {
        val result = library.applyFilterSort(ProblemFilter(), ProblemSort.TITLE)
        // Alpha Tree, Beta Query, Delta Easy, Gamma Dp
        assertEquals(listOf(1L, 2L, 4L, 3L), result.map { it.id })
    }

    @Test
    fun `attempts and updated sorts`() {
        assertEquals(listOf(1L, 2L, 3L, 4L), library.applyFilterSort(ProblemFilter(), ProblemSort.ATTEMPTS).map { it.id })
        assertEquals(listOf(3L, 4L, 2L, 1L), library.applyFilterSort(ProblemFilter(), ProblemSort.UPDATED).map { it.id })
    }

    @Test
    fun `difficulty range is inclusive and drops unknown ratings`() {
        // CLOSED range 1500..2200 keeps problems 1 (1900) and 3 (2200); UPDATED desc: 3 leads.
        val closed = library.applyFilterSort(
            ProblemFilter(difficultyMin = 1500, difficultyMax = 2200),
            ProblemSort.UPDATED,
        )
        assertEquals(listOf(3L, 1L), closed.map { it.id })

        // UPDATED sort is descending (3:99, 4:4, 1:1). Unknown difficulty (problem 2) is excluded
        // by any range.
        val openTop = library.applyFilterSort(ProblemFilter(difficultyMin = 800), ProblemSort.UPDATED)
        assertEquals(listOf(3L, 4L, 1L), openTop.map { it.id })

        val openBottom = library.applyFilterSort(ProblemFilter(difficultyMax = 1900), ProblemSort.UPDATED)
        assertEquals(listOf(4L, 1L), openBottom.map { it.id })

        val exact = library.applyFilterSort(ProblemFilter(difficultyMin = 800, difficultyMax = 800), ProblemSort.UPDATED)
        assertEquals(listOf(4L), exact.map { it.id })
    }

    @Test
    fun `difficulty range combines with judge`() {
        val result = library.applyFilterSort(
            ProblemFilter(judge = JudgeId.CODEFORCES, difficultyMin = 800, difficultyMax = 1500),
            ProblemSort.UPDATED,
        )
        assertEquals(listOf(4L), result.map { it.id })
    }
}
