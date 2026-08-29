package com.ojnexus.judge.codeforces

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deterministic pagination decisions for the incremental submission sync
 * (user.status returns pages newest-first; ids descend within a page).
 */
class SubmissionSyncPlannerTest {

    private val pageSize = 1000

    @Test
    fun `empty page stops the sync`() {
        assertFalse(SubmissionSyncPlanner.shouldContinueAfterPage(0, pageSize, null, null))
        assertFalse(SubmissionSyncPlanner.shouldContinueAfterPage(0, pageSize, null, 500L))
    }

    @Test
    fun `fresh account pages through until a short page`() {
        // Full page of brand-new submissions, none seen before -> continue.
        assertTrue(
            SubmissionSyncPlanner.shouldContinueAfterPage(
                pageItems = pageSize, pageSize = pageSize,
                pageSmallestSubmissionId = 5_000L, latestKnownSubmissionId = null,
            ),
        )
        // Short page: end of history.
        assertFalse(
            SubmissionSyncPlanner.shouldContinueAfterPage(
                pageItems = 12, pageSize = pageSize,
                pageSmallestSubmissionId = 100L, latestKnownSubmissionId = null,
            ),
        )
    }

    @Test
    fun `boundary page stops the sync after being upserted`() {
        // Full page whose oldest id is already known: the boundary has been crossed.
        // The page itself was upserted BEFORE this decision (overlap window for rejudge).
        assertFalse(
            SubmissionSyncPlanner.shouldContinueAfterPage(
                pageItems = pageSize, pageSize = pageSize,
                pageSmallestSubmissionId = 900L, latestKnownSubmissionId = 1_000L,
            ),
        )
    }

    @Test
    fun `full page entirely above the boundary keeps going`() {
        assertTrue(
            SubmissionSyncPlanner.shouldContinueAfterPage(
                pageItems = pageSize, pageSize = pageSize,
                pageSmallestSubmissionId = 2_500L, latestKnownSubmissionId = 1_000L,
            ),
        )
    }

    @Test
    fun `no submissions ever seen with a full page continues`() {
        assertTrue(
            SubmissionSyncPlanner.shouldContinueAfterPage(
                pageItems = pageSize, pageSize = pageSize,
                pageSmallestSubmissionId = 7L, latestKnownSubmissionId = null,
            ),
        )
    }
}
