package com.ojnexus.feature.training

import com.ojnexus.core.model.JudgeId
import com.ojnexus.core.model.ReviewQueueItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusSprintTest {

    @Test
    fun `due reviews come first and recommendation duplicates are removed`() {
        val dueLater = review(id = 30, dueDay = 10, dueAt = 20, title = "Later due")
        val dueSoon = review(id = 20, dueDay = 9, dueAt = 40, title = "Soon due")
        val plan = buildFocusSprintPlan(
            buckets = ReviewBuckets(overdue = listOf(dueLater, dueSoon)),
            recommendations = listOf(
                recommendation(id = 20, priority = 100, title = "Duplicate"),
                recommendation(id = 10, priority = 80, title = "Target one"),
            ),
        )

        assertEquals(listOf(20L, 30L, 10L), plan.ids)
        assertEquals(2, plan.dueCount)
        assertEquals(1, plan.targetCount)
        assertEquals(FocusSprintSource.DUE, plan.items[0].source)
        assertEquals(FocusSprintSource.TARGET, plan.items[2].source)
    }

    @Test
    fun `recommendations fill the sprint and respect the five item cap`() {
        val recommendations = (1L..7L).map { id ->
            recommendation(id = id, priority = id.toInt(), title = "Target $id")
        }

        val plan = buildFocusSprintPlan(ReviewBuckets(), recommendations)

        assertEquals(listOf(7L, 6L, 5L, 4L, 3L), plan.ids)
        assertEquals(0, plan.dueCount)
        assertEquals(5, plan.targetCount)
    }

    @Test
    fun `empty or non-positive limit produces an empty plan`() {
        val recommendation = recommendation(id = 1, priority = 10, title = "Target")

        assertTrue(buildFocusSprintPlan(ReviewBuckets(), emptyList()).items.isEmpty())
        assertTrue(buildFocusSprintPlan(ReviewBuckets(), listOf(recommendation), limit = 0).items.isEmpty())
    }

    private fun review(id: Long, dueDay: Long, dueAt: Long, title: String) = ReviewQueueItem(
        problemId = id,
        problemTitle = title,
        judge = JudgeId.CODEFORCES,
        difficulty = 800,
        stage = 0,
        dueAt = dueAt,
        dueDayIndex = dueDay,
        lastResult = null,
    )

    private fun recommendation(id: Long, priority: Int, title: String) = TrainingRecommendation(
        problemId = id,
        judge = "CODEFORCES",
        externalId = "${id}A",
        title = title,
        priority = priority,
        reasons = emptySet(),
    )
}
