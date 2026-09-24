package com.ojnexus.judge.atcoder

object AtCoderSyncPolicy {
    const val REQUEST_INTERVAL_MS = 1_100L
    const val SUBMISSION_PAGE_SIZE = 500
    const val SUBMISSION_OVERLAP_SECONDS = 120L
    const val SUBMISSIONS_FRESH_MS = 15 * 60 * 1000L
    const val CONTESTS_FRESH_MS = 3 * 60 * 60 * 1000L
    const val PROBLEMS_FRESH_MS = 24 * 60 * 60 * 1000L
    const val PERIODIC_SYNC_MINUTES = 6 * 60L
}
