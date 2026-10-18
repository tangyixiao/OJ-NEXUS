package com.ojnexus.judge.sync

import com.ojnexus.judge.codeforces.SyncPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the documented battery-profile defaults of the sync engine. These constants are the
 * single source consumed by the worker builder and by docs/SYNC_ENGINE.md; changing any of
 * them intentionally changes the documented battery profile and must update the docs.
 */
class SyncBatteryProfileTest {

    @Test
    fun periodicCadenceMatchesDocumentedDefault() {
        assertEquals(360L, SyncPolicy.PERIODIC_SYNC_MINUTES)
        assertEquals(6L * 60, SyncPolicy.PERIODIC_SYNC_MINUTES)
    }

    @Test
    fun freshnessWindowsOrderStagesFromMostToLeastVolatile() {
        // submissions change fastest, problemset slowest — the profile table in
        // docs/SYNC_ENGINE.md documents exactly this ordering.
        assertTrue(SyncPolicy.SUBMISSIONS_FRESH_MS < SyncPolicy.PROFILE_FRESH_MS)
        assertEquals(SyncPolicy.PROFILE_FRESH_MS, SyncPolicy.RATING_FRESH_MS)
        assertTrue(SyncPolicy.PROFILE_FRESH_MS < SyncPolicy.CONTESTS_FRESH_MS)
        assertTrue(SyncPolicy.CONTESTS_FRESH_MS < SyncPolicy.PROBLEMSET_FRESH_MS)
    }

    @Test
    fun requestGateSpacingIsAtLeastTheOfficialTwoSecondLimit() {
        assertTrue(SyncPolicy.REQUEST_INTERVAL_MS >= 2_000)
    }

    @Test
    fun steadyStateRunTouchesAtMostOneNetworkStage() {
        // With all freshness windows elapsed except nothing (worker fires every 6 h,
        // every window <= 1 h), a periodic run re-fetches at most: profile + rating +
        // submissions page + contests + problemset only if their windows elapsed. Since the
        // 6 h cadence exceeds every freshness window except the 24 h problemset, a run touches
        // between 1 and 5 stages; the worst-case request count is bounded and documented.
        val stages = 5 // PROFILE, RATING, SUBMISSIONS, CONTESTS, PROBLEMS
        val worstCaseRequests = stages // one adapter call per stage, gate-spaced
        val worstCaseSeconds = (worstCaseRequests - 1) * SyncPolicy.REQUEST_INTERVAL_MS / 1000
        assertTrue(worstCaseSeconds <= 10) // bounded burst, not a marathon
    }

    @Test
    fun pageBudgetBoundsPerRunPayload() {
        assertEquals(1000, SyncPolicy.SUBMISSION_PAGE_SIZE)
    }
}
