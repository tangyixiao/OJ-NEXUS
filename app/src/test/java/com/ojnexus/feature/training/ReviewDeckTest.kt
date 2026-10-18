package com.ojnexus.feature.training

import com.ojnexus.core.model.ReviewQueueItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewDeckTest {

    @Test
    fun `seed is stable for the same day and changes across days`() {
        assertEquals(reviewDeckSeed(20_000L), reviewDeckSeed(20_000L))
        assertFalse(reviewDeckSeed(20_000L) == reviewDeckSeed(20_001L))
        assertTrue(reviewDeckSeed(0L) >= 0L)
    }

    @Test
    fun `shuffle is deterministic for a fixed seed`() {
        val items = queue()
        val first = reviewDeckShuffled(items, reviewDeckSeed(10L))
        val second = reviewDeckShuffled(items, reviewDeckSeed(10L))
        assertEquals(first.map { it.problemId }, second.map { it.problemId })
    }

    @Test
    fun `shuffle is a permutation of the input`() {
        val items = queue()
        val shuffled = reviewDeckShuffled(items, reviewDeckSeed(10L))
        assertEquals(items.size, shuffled.size)
        assertEquals(items.map { it.problemId }.sorted(), shuffled.map { it.problemId }.sorted())
    }

    @Test
    fun `shuffle differs from the schedule order for a non trivial deck`() {
        val items = queue()
        val shuffled = reviewDeckShuffled(items, reviewDeckSeed(10L)).map { it.problemId }
        val scheduled = buildReviewDeck(items, ReviewDeckMode.SCHEDULE, 0L, ReviewCramFilter())
            .items.map { it.problemId }
        assertFalse(shuffled == scheduled)
    }

    @Test
    fun `schedule mode ignores the seed`() {
        val items = queue()
        val first = buildReviewDeck(items, ReviewDeckMode.SCHEDULE, 1L, ReviewCramFilter())
        val second = buildReviewDeck(items, ReviewDeckMode.SCHEDULE, 999L, ReviewCramFilter())
        assertEquals(
            first.items.map { it.problemId },
            second.items.map { it.problemId },
        )
    }

    @Test
    fun `schedule mode orders by due day then due time then id`() {
        val deck = buildReviewDeck(queue(), ReviewDeckMode.SCHEDULE, 0L, ReviewCramFilter())
        assertEquals(
            listOf(400L, 100L, 200L, 300L, 500L),
            deck.items.map { it.problemId },
        )
    }

    @Test
    fun `cram matches judge and a case insensitive tag`() {
        val filter = ReviewCramFilter(judge = "CODEFORCES", tag = "dp")
        assertEquals(
            listOf(100L),
            buildReviewDeck(queue(), ReviewDeckMode.CRAM, 7L, filter).items.map { it.problemId },
        )
    }

    @Test
    fun `cram with a blank tag keeps every problem of that judge`() {
        val filter = ReviewCramFilter(judge = "ATCODER")
        val ids = buildReviewDeck(queue(), ReviewDeckMode.CRAM, 7L, filter).items.map { it.problemId }
        assertEquals(setOf(200L, 300L), ids.toSet())
    }

    @Test
    fun `cram without a judge matches nothing`() {
        val filter = ReviewCramFilter(judge = null, tag = "dp")
        assertTrue(
            buildReviewDeck(queue(), ReviewDeckMode.CRAM, 7L, filter).items.isEmpty(),
        )
        assertFalse(filter.isUsable)
    }

    @Test
    fun `cram ignores the due day entirely`() {
        val filter = ReviewCramFilter(judge = "CODEFORCES", tag = "greedy")
        val deck = buildReviewDeck(queue(), ReviewDeckMode.CRAM, 7L, filter)
        assertEquals(listOf(400L), deck.items.map { it.problemId })
    }

    @Test
    fun `capture keeps only due items in the shared deck order`() {
        val scheduled = buildReviewDeck(queue(), ReviewDeckMode.SCHEDULE, 0L, ReviewCramFilter())
            .items.filter { it.dueDayIndex <= 10L }
            .map { it.problemId }
        val captured = captureReviewRunQueue(queue(), todayEpochDay = 10L).map { it.problemId }
        assertEquals(listOf(400L, 100L, 200L, 300L), captured)
        assertEquals(scheduled, captured)
    }

    private fun queue(): List<ReviewQueueItem> = listOf(
        item(100L, "CODEFORCES", dueAt = 40L, dueDayIndex = 9L, tags = listOf("dp")),
        item(200L, "ATCODER", dueAt = 40L, dueDayIndex = 9L, tags = listOf("DP")),
        item(300L, "ATCODER", dueAt = 40L, dueDayIndex = 9L, tags = listOf("math")),
        item(400L, "CODEFORCES", dueAt = 10L, dueDayIndex = 9L, tags = listOf("greedy")),
        item(500L, "LUOGU", dueAt = 10L, dueDayIndex = 11L, tags = listOf("dp")),
    )
    private fun item(
        id: Long,
        judge: String,
        dueAt: Long,
        dueDayIndex: Long,
        tags: List<String>,
    ): ReviewQueueItem = ReviewQueueItem(
        problemId = id,
        problemTitle = "Problem $id",
        judge = com.ojnexus.core.model.JudgeId.entries.first { it.displayName == judge },
        difficulty = 800,
        stage = 0,
        dueAt = dueAt,
        dueDayIndex = dueDayIndex,
        lastResult = null,
        tags = tags,
    )
}
