package com.ojnexus.feature.problems

import com.ojnexus.core.model.Attempt
import com.ojnexus.core.model.JudgeId
import com.ojnexus.core.model.Problem
import com.ojnexus.core.model.ProblemKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryWindowTest {

    private fun problem(index: Int, difficulty: Int? = index): Problem = Problem(
        id = index.toLong() + 1,
        key = ProblemKey(JudgeId.CODEFORCES, "P$index"),
        title = "Problem $index",
        difficulty = difficulty,
        createdAt = 0L,
        updatedAt = index.toLong(),
        firstSolvedAt = null,
        lastAttemptAt = null,
        attemptCount = index,
        solved = index % 3 == 0,
        favorite = false,
        sourceUrl = null,
        tags = emptyList(),
    )

    @Test
    fun `window math respects initial and step constants`() {
        assertEquals(60, LIBRARY_WINDOW_INITIAL)
        assertEquals(60, LIBRARY_WINDOW_STEP)
    }

    @Test
    fun `windowList caps at count and never goes negative`() {
        val list = (0 until 200).map { it }
        assertEquals(60, windowList(list, LIBRARY_WINDOW_INITIAL).size)
        assertEquals(120, windowList(list, 120).size)
        assertEquals(list.size, windowList(list, 500).size)
        assertTrue(windowList(list, -5).isEmpty())
    }

    @Test
    fun `hasMoreLibrary flips at the boundary`() {
        assertTrue(hasMoreLibrary(fullSize = 200, visibleCount = 60))
        assertFalse(hasMoreLibrary(fullSize = 200, visibleCount = 200))
        assertFalse(hasMoreLibrary(fullSize = 60, visibleCount = 60))
    }

    @Test
    fun `ten thousand row filter sort window pass completes within budget`() {
        val library = (0 until 10_000).map { problem(it) }
        val filter = ProblemFilter(query = "problem", difficultyMin = 100)
        val startedAt = System.nanoTime()

        val visible = library.applyFilterSort(filter, ProblemSort.DIFFICULTY)
        val window = windowList(visible, LIBRARY_WINDOW_INITIAL)
        val summary = summarizeProblemLibrary(library, visible)

        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000

        assertEquals(LIBRARY_WINDOW_INITIAL, window.size)
        assertTrue(summary.total == 10_000)
        assertTrue(summary.visible == visible.size)
        assertTrue("10k pass took ${elapsedMs}ms", elapsedMs < 250)
    }

    @Test
    fun `window grows without duplicating rows`() {
        val list = (0 until 150).map { it }
        val first = windowList(list, LIBRARY_WINDOW_INITIAL)
        val grown = windowList(list, LIBRARY_WINDOW_INITIAL + LIBRARY_WINDOW_STEP)
        assertEquals(first, grown.take(first.size))
        assertEquals(120, grown.size)
    }
}
