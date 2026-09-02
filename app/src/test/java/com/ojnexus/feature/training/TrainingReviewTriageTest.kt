package com.ojnexus.feature.training

import com.ojnexus.core.model.JudgeId
import com.ojnexus.core.model.ReviewQueueItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Test

class TrainingReviewTriageTest {
    private fun review(id: Long, day: Long, dueAt: Long = day): ReviewQueueItem =
        ReviewQueueItem(id, "P$id", JudgeId.CODEFORCES, 800, 0, dueAt, day, null)

    @Test
    fun `summary counts every bucket and selects earliest due problem`() {
        val buckets = ReviewBuckets(
            overdue = listOf(review(2L, 9L, 30L), review(1L, 8L, 40L)),
            dueToday = listOf(review(3L, 10L, 20L)),
            upcoming = listOf(review(4L, 11L)),
        )

        assertEquals(ReviewQueueSummary(2, 1, 1, 4, 1L), reviewQueueSummary(buckets))
    }

    @Test
    fun `summary breaks same-day ties by due time then problem id`() {
        val buckets = ReviewBuckets(
            dueToday = listOf(review(9L, 10L, 30L), review(7L, 10L, 20L), review(8L, 10L, 20L)),
        )

        assertEquals(7L, reviewQueueSummary(buckets).nextDueProblemId)
    }

    @Test
    fun `filters keep only the requested review buckets without mutating source`() {
        val buckets = ReviewBuckets(
            overdue = listOf(review(1L, 8L)),
            dueToday = listOf(review(2L, 10L)),
            upcoming = listOf(review(3L, 11L)),
        )

        val dueNow = filterReviewBuckets(buckets, ReviewQueueFilter.DUE_NOW)
        val upcoming = filterReviewBuckets(buckets, ReviewQueueFilter.UPCOMING)

        assertEquals(listOf(1L), dueNow.overdue.map { it.problemId })
        assertEquals(listOf(2L), dueNow.dueToday.map { it.problemId })
        assertEquals(emptyList<Long>(), dueNow.upcoming.map { it.problemId })
        assertEquals(emptyList<Long>(), upcoming.overdue.map { it.problemId })
        assertEquals(listOf(3L), upcoming.upcoming.map { it.problemId })
        assertNotSame(buckets, dueNow)
        assertNull(reviewQueueSummary(ReviewBuckets()).nextDueProblemId)
    }
}
