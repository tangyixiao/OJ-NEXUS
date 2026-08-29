package com.ojnexus.judge.codeforces

/**
 * Pure pagination decision for `user.status` sync. Submissions arrive newest-first, so a
 * full page's SMALLEST id tells us whether we have crossed the last-synced boundary.
 *
 * Rules:
 *  - empty page            → stop (history exhausted)
 *  - short page (< pageSize) → stop (end of history reached; the page is still upserted)
 *  - full page whose smallest id <= latestKnown → stop; this boundary page acts as the
 *    rejudge overlap window (it was upserted before this decision)
 *  - otherwise continue paging
 *
 * The upsert-before-decide order guarantees the overlap window is always refreshed, even
 * when the newest pages contain only already-known submissions.
 */
object SubmissionSyncPlanner {

    fun shouldContinueAfterPage(
        pageItems: Int,
        pageSize: Int,
        pageSmallestSubmissionId: Long?,
        latestKnownSubmissionId: Long?,
    ): Boolean = when {
        pageItems <= 0 -> false
        pageItems < pageSize -> false
        latestKnownSubmissionId != null &&
            pageSmallestSubmissionId != null &&
            pageSmallestSubmissionId <= latestKnownSubmissionId -> false
        else -> true
    }
}
