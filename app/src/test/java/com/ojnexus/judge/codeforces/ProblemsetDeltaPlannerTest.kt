package com.ojnexus.judge.codeforces

import com.ojnexus.judge.codeforces.api.dto.CfProblemDto
import com.ojnexus.judge.codeforces.api.dto.CfProblemStatisticsDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure offline decision matrix + fingerprint stability for the Phase 91 delta sync. */
class ProblemsetDeltaPlannerTest {

    private fun problem(
        contestId: Long,
        index: String,
        rating: Int? = 1500,
    ) = CfProblemDto(contestId = contestId, index = index, name = "P$contestId$index", rating = rating)

    private fun merged(
        vararg rows: Triple<Long, String, Int?>,
    ): List<Pair<CfProblemDto, CfProblemStatisticsDto?>> = rows.map { (contestId, index, rating) ->
        val stat = rating?.let { CfProblemStatisticsDto(contestId = contestId, index = index, solvedCount = it) }
        problem(contestId, index, null) to stat
    }

    // --- decide matrix ---

    @Test
    fun `force always writes`() {
        assertEquals(
            ProblemsetSyncDecision.WRITE,
            ProblemsetDeltaPlanner.decide(
                force = true, lastSyncedAt = 1000L, now = 1500L, freshnessMs = 10_000L,
                liveCount = 5, fetchedCount = 5,
            ),
        )
    }

    @Test
    fun `fresh stamp skips the network entirely`() {
        assertEquals(
            ProblemsetSyncDecision.SKIP_FRESH,
            ProblemsetDeltaPlanner.decide(
                force = false, lastSyncedAt = 1000L, now = 1500L, freshnessMs = 10_000L,
                liveCount = 5, fetchedCount = 5,
            ),
        )
    }

    @Test
    fun `stale stamp with no live rows writes`() {
        assertEquals(
            ProblemsetSyncDecision.WRITE,
            ProblemsetDeltaPlanner.decide(
                force = false, lastSyncedAt = 1000L, now = 99_000L, freshnessMs = 10_000L,
                liveCount = 0, fetchedCount = 5,
            ),
        )
    }

    @Test
    fun `stale stamp with equal counts is the unchanged candidate`() {
        assertEquals(
            ProblemsetSyncDecision.SKIP_UNCHANGED,
            ProblemsetDeltaPlanner.decide(
                force = false, lastSyncedAt = 1000L, now = 99_000L, freshnessMs = 10_000L,
                liveCount = 5, fetchedCount = 5,
            ),
        )
    }

    @Test
    fun `stale stamp with drifted counts writes`() {
        assertEquals(
            ProblemsetSyncDecision.WRITE,
            ProblemsetDeltaPlanner.decide(
                force = false, lastSyncedAt = 1000L, now = 99_000L, freshnessMs = 10_000L,
                liveCount = 4, fetchedCount = 5,
            ),
        )
    }

    // --- fingerprint ---

    @Test
    fun `fingerprint is stable across shuffled input and identical rows`() {
        val a = merged(
            Triple(1L, "A", 100),
            Triple(2L, "B", 200),
            Triple(3L, "C", null),
        )
        val b = merged(
            Triple(3L, "C", null),
            Triple(2L, "B", 200),
            Triple(1L, "A", 100),
        )
        assertEquals(ProblemsetDeltaPlanner.fingerprint(a), ProblemsetDeltaPlanner.fingerprint(b))
        assertTrue(ProblemsetDeltaPlanner.fingerprint(a).isNotEmpty())
    }

    @Test
    fun `fingerprint changes when solved count changes`() {
        val a = merged(Triple(1L, "A", 100))
        val b = merged(Triple(1L, "A", 101))
        assertNotEquals(ProblemsetDeltaPlanner.fingerprint(a), ProblemsetDeltaPlanner.fingerprint(b))
    }

    @Test
    fun `fingerprint changes when rating or identity changes`() {
        val base = listOf(
            problem(1L, "A", 1500) to CfProblemStatisticsDto(1L, "A", 10),
        )
        val rated = listOf(
            problem(1L, "A", 1600) to CfProblemStatisticsDto(1L, "A", 10),
        )
        val otherProblem = listOf(
            problem(1L, "B", 1500) to CfProblemStatisticsDto(1L, "B", 10),
        )
        assertNotEquals(ProblemsetDeltaPlanner.fingerprint(base), ProblemsetDeltaPlanner.fingerprint(rated))
        assertNotEquals(ProblemsetDeltaPlanner.fingerprint(base), ProblemsetDeltaPlanner.fingerprint(otherProblem))
    }

    @Test
    fun `problems without contest id are dropped from the fingerprint`() {
        val withGym = listOf(
            problem(1L, "A", 1500) to null,
            CfProblemDto(contestId = null, index = "X", name = "gym-only") to null,
        )
        val clean = listOf(problem(1L, "A", 1500) to null)
        assertEquals(ProblemsetDeltaPlanner.fingerprint(withGym), ProblemsetDeltaPlanner.fingerprint(clean))
    }

    // --- live proxy ---

    @Test
    fun `live proxy matches only on equal non-zero count and equal solved sum`() {
        assertTrue(
            ProblemsetDeltaPlanner.liveMatches(
                fetchedCount = 10, fetchedSolvedSum = 500L, liveCount = 10, liveSolvedSum = 500L,
            ),
        )
        assertEquals(
            false,
            ProblemsetDeltaPlanner.liveMatches(
                fetchedCount = 10, fetchedSolvedSum = 500L, liveCount = 10, liveSolvedSum = 501L,
            ),
        )
        assertEquals(
            false,
            ProblemsetDeltaPlanner.liveMatches(
                fetchedCount = 10, fetchedSolvedSum = 500L, liveCount = 9, liveSolvedSum = 500L,
            ),
        )
        // Never treat an empty live table as unchanged.
        assertEquals(
            false,
            ProblemsetDeltaPlanner.liveMatches(
                fetchedCount = 0, fetchedSolvedSum = 0L, liveCount = 0, liveSolvedSum = 0L,
            ),
        )
    }
}
