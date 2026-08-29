package com.ojnexus.judge.codeforces

/**
 * Central sync configuration — the single source for page sizes, overlap windows and
 * freshness thresholds. ViewModels and the coordinator must read these instead of
 * scattering time arithmetic.
 */
object SyncPolicy {

    /**
     * user.status page size. The official API allows large counts; 1000 balances request
     * budget (>= 2 s per page) against per-transaction write size.
     */
    const val SUBMISSION_PAGE_SIZE = 1000

    /** Small recent batch re-fetched on every incremental sync so rejudged verdicts land. */
    const val OVERLAP_SUBMISSION_COUNT = 0 // overlap is achieved via the boundary page (see planner)

    // Freshness windows (ms). Manual sync ignores freshness and forces a real refresh.
    const val PROFILE_FRESH_MS: Long = 30 * 60 * 1000
    const val RATING_FRESH_MS: Long = 30 * 60 * 1000
    const val SUBMISSIONS_FRESH_MS: Long = 15 * 60 * 1000
    const val CONTESTS_FRESH_MS: Long = 60 * 60 * 1000
    const val PROBLEMSET_FRESH_MS: Long = 24 * 60 * 60 * 1000

    // Background sync cadence.
    const val PERIODIC_SYNC_MINUTES: Long = 6 * 60

    // WorkManager retry backoff for transient failures.
    const val WORK_BACKOFF_SECONDS: Long = 10 * 60

    /** Official limit: at most one request every 2 s; the gate uses a safety margin. */
    const val REQUEST_INTERVAL_MS: Long = 2_100
}
