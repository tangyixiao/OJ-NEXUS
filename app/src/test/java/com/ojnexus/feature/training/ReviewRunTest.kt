package com.ojnexus.feature.training

import com.ojnexus.core.model.JudgeId
import com.ojnexus.core.model.ReviewQueueItem
import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewRunTest {

    @Test
    fun `capture keeps due items in due-time then id order`() {
        val queue = listOf(
            review(id = 3L, day = 10L, dueAt = 40L),
            review(id = 1L, day = 9L, dueAt = 80L),
            review(id = 2L, day = 9L, dueAt = 40L),
            review(id = 4L, day = 11L, dueAt = 10L),
        )

        assertEquals(
            listOf(2L, 3L, 1L),
            captureReviewRunQueue(queue, todayEpochDay = 10L).map { it.problemId },
        )
    }

    @Test
    fun `progress is bounded and zero when no run exists`() {
        assertEquals(0f, reviewRunProgress(0, 0))
        assertEquals(0.5f, reviewRunProgress(4, 2))
        assertEquals(1f, reviewRunProgress(4, 9))
    }

    private fun review(id: Long, day: Long, dueAt: Long): ReviewQueueItem =
        ReviewQueueItem(id, "P$id", JudgeId.CODEFORCES, 800, 0, dueAt, day, null)
}
