package com.ojnexus.feature.dashboard

import com.ojnexus.core.data.sync.SyncPhase
import com.ojnexus.core.database.entity.ContestEntity
import com.ojnexus.core.database.entity.JudgeAccountEntity
import com.ojnexus.core.database.entity.SyncStateEntity
import com.ojnexus.core.model.JudgeId
import com.ojnexus.core.model.ReviewQueueItem
import com.ojnexus.core.model.ReviewResult
import com.ojnexus.core.model.TaskType
import com.ojnexus.core.model.TrainingTask
import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardCommandSurfaceTest {
    @Test
    fun `now uses the first incomplete task and next uses due review`() {
        val surface = deriveDashboardCommandSurface(
            state(
                todayTasks = listOf(
                    task(1L, completed = true, title = "Done"),
                    task(2L, problemTitle = "P1001", title = "A+B"),
                ),
                nextReview = review("Review P"),
            ),
        )

        assertEquals(DashboardSurfaceValue.Data("P1001"), surface.now.value)
        assertEquals(DashboardSurfaceTarget.TRAINING, surface.now.target)
        assertEquals(DashboardSurfaceValue.Data("Review P"), surface.next.value)
        assertEquals(DashboardSurfaceTarget.REVIEW, surface.next.target)
        assertEquals(
            DashboardSurfaceValue.Message(DashboardSurfaceMessage.LOCAL_READY),
            surface.signal.value,
        )
    }

    @Test
    fun `next falls back to the next contest when no review is due`() {
        val surface = deriveDashboardCommandSurface(
            state(
                nextContest = ContestEntity(
                    judge = JudgeId.CODEFORCES.id,
                    externalContestId = "1",
                    name = "ROUND 1",
                    phase = "BEFORE",
                    durationSeconds = 7_200L,
                    startTimeSeconds = 100L,
                    updatedAt = 1L,
                ),
            ),
        )

        assertEquals(DashboardSurfaceValue.Data("ROUND 1"), surface.next.value)
        assertEquals(DashboardSurfaceTarget.CONTESTS, surface.next.target)
    }

    @Test
    fun `sync error becomes the highest priority signal`() {
        val surface = deriveDashboardCommandSurface(
            state(
                judgeConnections = listOf(
                    JudgeDashboardConnection(
                        judge = JudgeId.CODEFORCES,
                        account = account(),
                        syncState = SyncStateEntity(
                            judge = JudgeId.CODEFORCES.id,
                            state = SyncPhase.ERROR.name,
                        ),
                    ),
                ),
            ),
        )

        assertEquals(
            DashboardSurfaceValue.Message(DashboardSurfaceMessage.SYNC_ATTENTION),
            surface.signal.value,
        )
        assertEquals(DashboardSurfaceTarget.SETTINGS, surface.signal.target)
    }

    @Test
    fun `empty dashboard keeps visible placeholders and routes signal to settings`() {
        val surface = deriveDashboardCommandSurface(state())

        assertEquals(
            DashboardSurfaceValue.Message(DashboardSurfaceMessage.NO_ACTIVE_COMMAND),
            surface.now.value,
        )
        assertEquals(DashboardSurfaceTarget.TRAINING, surface.now.target)
        assertEquals(
            DashboardSurfaceValue.Message(DashboardSurfaceMessage.NO_NEXT_COMMAND),
            surface.next.value,
        )
        assertEquals(DashboardSurfaceTarget.NONE, surface.next.target)
        assertEquals(DashboardSurfaceTarget.SETTINGS, surface.signal.target)
    }

    private fun state(
        todayTasks: List<TrainingTask> = emptyList(),
        nextReview: ReviewQueueItem? = null,
        nextContest: ContestEntity? = null,
        judgeConnections: List<JudgeDashboardConnection> = emptyList(),
    ) = DashboardUiState(
        todayTasks = todayTasks,
        week = WeekSummary(0, 0, 0L),
        currentStreak = 0,
        longestStreak = 0,
        nextReview = nextReview,
        recent = emptyList(),
        loadWeek = emptyList(),
        summary = DashboardSummary(0, judgeConnections.size, 0, null),
        judgeConnections = judgeConnections,
        nextContest = nextContest,
        nowSeconds = 0L,
    )

    private fun task(
        id: Long,
        completed: Boolean = false,
        problemTitle: String? = null,
        title: String? = null,
    ) = TrainingTask(
        id = id,
        dateEpochDay = 1L,
        type = TaskType.SOLVE,
        problemId = id,
        problemTitle = problemTitle,
        title = title,
        completed = completed,
        priority = 1,
        sortOrder = id.toInt(),
        createdAt = 1L,
    )

    private fun review(title: String) = ReviewQueueItem(
        problemId = 1L,
        problemTitle = title,
        judge = JudgeId.LOCAL,
        difficulty = null,
        stage = 0,
        dueAt = 1L,
        dueDayIndex = 1L,
        lastResult = ReviewResult.FAIL,
    )

    private fun account() = JudgeAccountEntity(
        id = 1L,
        judge = JudgeId.CODEFORCES.id,
        handle = "raw",
        canonicalHandle = "user",
        connectedAt = 1L,
        updatedAt = 1L,
    )
}
