package com.ojnexus.feature.training

import com.ojnexus.core.domain.TrainingReason
import com.ojnexus.core.model.JudgeId
import com.ojnexus.core.model.ReviewQueueItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusSprintLadderTest {

    @Test
    fun `any band keeps the ladder order across bands`() {
        val plan = buildFocusSprintLadder(
            buckets = ReviewBuckets(overdue = listOf(review(id = 20, dueDay = 9, dueAt = 40, difficulty = 1500))),
            recommendations = listOf(
                recommendation(id = 10, priority = 80, difficulty = 1200),
                recommendation(id = 30, priority = 40, difficulty = 1900),
            ),
            targetDifficulty = 1500,
            band = FocusSprintLadderBand.ANY,
            todayEpochDay = 10L,
        )

        // EASIER first, then FIT (the due review), then STRETCH: the band is the primary key.
        assertEquals(listOf(10L, 20L, 30L), plan.ids)
        assertEquals(1, plan.dueCount)
        assertEquals(2, plan.targetCount)
        assertEquals(FocusSprintLadderBand.ANY, plan.band)
        assertEquals(1500, plan.targetDifficulty)
    }

    @Test
    fun `fit band keeps only in band candidates in ascending difficulty`() {
        val plan = buildFocusSprintLadder(
            buckets = ReviewBuckets(),
            recommendations = listOf(
                recommendation(id = 1, priority = 90, difficulty = 900),
                recommendation(id = 2, priority = 80, difficulty = 1400),
                recommendation(id = 3, priority = 70, difficulty = 1650),
                recommendation(id = 4, priority = 60, difficulty = 2400),
            ),
            targetDifficulty = 1500,
            band = FocusSprintLadderBand.FIT,
            todayEpochDay = 10L,
        )

        assertEquals(listOf(2L, 3L), plan.ids)
        assertEquals(2, plan.targetCount)
    }

    @Test
    fun `easier and stretch split around the target with the two hundred steps`() {
        val recommendations = listOf(
            recommendation(id = 1, priority = 90, difficulty = 1299),
            recommendation(id = 2, priority = 80, difficulty = 1300),
            recommendation(id = 3, priority = 70, difficulty = 1700),
            recommendation(id = 4, priority = 60, difficulty = 1701),
        )

        val easier = buildFocusSprintLadder(
            ReviewBuckets(),
            recommendations,
            targetDifficulty = 1500,
            band = FocusSprintLadderBand.EASIER,
            todayEpochDay = 10L,
        )
        val stretch = buildFocusSprintLadder(
            ReviewBuckets(),
            recommendations,
            targetDifficulty = 1500,
            band = FocusSprintLadderBand.STRETCH,
            todayEpochDay = 10L,
        )

        // 1299 < 1300 → EASIER; 1300 = target-200 → EASIER (inclusive); 1700 = target+200 → FIT; 1701 > → STRETCH.
        assertEquals(listOf(1L, 2L), easier.ids)
        assertTrue(easier.items.all { it.band == FocusSprintLadderBand.EASIER })
        assertEquals(listOf(4L), stretch.ids)
        assertEquals(FocusSprintLadderBand.STRETCH, stretch.items.single().band)
    }

    @Test
    fun `missing rating never matches a concrete band and trails as unrated`() {
        val plan = buildFocusSprintLadder(
            buckets = ReviewBuckets(),
            recommendations = listOf(
                recommendation(id = 1, priority = 90, difficulty = 1500),
                recommendation(id = 2, priority = 80, difficulty = null),
            ),
            targetDifficulty = 1500,
            band = FocusSprintLadderBand.FIT,
            todayEpochDay = 10L,
        )

        assertEquals(listOf(1L, 2L), plan.ids)
        assertEquals(1, plan.unratedCount)
        assertNull(plan.items.last().difficulty)
        assertEquals(FocusSprintLadderBand.ANY, plan.items.last().band)
    }

    @Test
    fun `missing target with any band keeps everything`() {
        val recommendations = listOf(
            recommendation(id = 1, priority = 90, difficulty = 900),
            recommendation(id = 2, priority = 80, difficulty = 1500),
            recommendation(id = 3, priority = 70, difficulty = null),
        )

        val plan = buildFocusSprintLadder(
            ReviewBuckets(),
            recommendations,
            targetDifficulty = null,
            band = FocusSprintLadderBand.ANY,
            todayEpochDay = 10L,
        )

        assertEquals(listOf(1L, 2L, 3L), plan.ids)
        assertTrue(plan.items.all { it.band == FocusSprintLadderBand.ANY })
        assertEquals(1, plan.unratedCount)
    }

    @Test
    fun `reason codes follow a fixed precedence`() {
        val reasons = setOf(
            TrainingReason.DIFFICULTY_FIT,
            TrainingReason.UNSOLVED,
            TrainingReason.LOW_MASTERY,
        )
        val plan = buildFocusSprintLadder(
            ReviewBuckets(),
            listOf(recommendation(id = 1, priority = 10, difficulty = 1500, reasons = reasons)),
            targetDifficulty = 1500,
            band = FocusSprintLadderBand.ANY,
            todayEpochDay = 10L,
        )

        assertEquals(FocusSprintReasonCode.WEAK_AREA, plan.items.single().reason)
        assertNull(plan.items.single().reviewGapDays)
    }

    @Test
    fun `due rows report review due and the day gap`() {
        val plan = buildFocusSprintLadder(
            buckets = ReviewBuckets(
                dueToday = listOf(review(id = 7, dueDay = 12, dueAt = 5, difficulty = 1500)),
            ),
            recommendations = emptyList(),
            targetDifficulty = 1500,
            band = FocusSprintLadderBand.ANY,
            todayEpochDay = 10L,
        )

        val item = plan.items.single()
        assertEquals(FocusSprintReasonCode.REVIEW_DUE, item.reason)
        assertEquals(2, item.reviewGapDays)
        assertEquals(1, plan.dueCount)
    }

    @Test
    fun `unrated rows carry the unrated reason even when the reasons set is rich`() {
        val plan = buildFocusSprintLadder(
            buckets = ReviewBuckets(),
            listOf(
                recommendation(
                    id = 1,
                    priority = 10,
                    difficulty = null,
                    reasons = setOf(TrainingReason.LOW_MASTERY, TrainingReason.UNSOLVED),
                ),
            ),
            targetDifficulty = 1500,
            band = FocusSprintLadderBand.ANY,
            todayEpochDay = 10L,
        )

        val item = plan.items.single()
        assertNull(item.difficulty)
        assertEquals(FocusSprintReasonCode.UNRATED, item.reason)
    }

    @Test
    fun `limit is respected and due rows win over duplicate recommendations`() {
        val plan = buildFocusSprintLadder(
            buckets = ReviewBuckets(
                overdue = listOf(
                    review(id = 20, dueDay = 8, dueAt = 10, difficulty = 1400),
                    review(id = 30, dueDay = 9, dueAt = 20, difficulty = 1300),
                ),
            ),
            recommendations = listOf(
                recommendation(id = 20, priority = 100, difficulty = 1400),
                recommendation(id = 10, priority = 80, difficulty = 1500),
                recommendation(id = 40, priority = 70, difficulty = 1600),
            ),
            targetDifficulty = 1500,
            band = FocusSprintLadderBand.ANY,
            todayEpochDay = 10L,
            limit = 3,
        )

        assertEquals(listOf(30L, 20L, 10L), plan.ids)
        assertEquals(2, plan.dueCount)
        assertEquals(1, plan.targetCount)
        assertTrue(plan.items.none { it.item.problemId == 40L })
    }

    @Test
    fun `non positive limit produces an empty plan`() {
        val plan = buildFocusSprintLadder(
            ReviewBuckets(),
            listOf(recommendation(id = 1, priority = 10, difficulty = 1500)),
            targetDifficulty = 1500,
            band = FocusSprintLadderBand.ANY,
            todayEpochDay = 10L,
            limit = 0,
        )

        assertTrue(plan.items.isEmpty())
        assertTrue(plan.ids.isEmpty())
    }

    private fun review(id: Long, dueDay: Long, dueAt: Long, difficulty: Int?) = ReviewQueueItem(
        problemId = id,
        problemTitle = "Problem $id",
        judge = JudgeId.CODEFORCES,
        difficulty = difficulty,
        stage = 0,
        dueAt = dueAt,
        dueDayIndex = dueDay,
        lastResult = null,
    )

    private fun recommendation(
        id: Long,
        priority: Int,
        difficulty: Int?,
        reasons: Set<TrainingReason> = setOf(TrainingReason.UNSOLVED),
    ) = TrainingRecommendation(
        problemId = id,
        judge = "CODEFORCES",
        externalId = "${id}A",
        title = "Target $id",
        priority = priority,
        reasons = reasons,
        difficulty = difficulty,
    )
}
