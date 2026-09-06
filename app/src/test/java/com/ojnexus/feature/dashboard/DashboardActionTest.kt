package com.ojnexus.feature.dashboard

import com.ojnexus.core.database.entity.ContestEntity
import com.ojnexus.core.database.entity.SubmissionJobEntity
import com.ojnexus.core.model.JudgeId
import com.ojnexus.core.model.SessionState
import com.ojnexus.core.model.TrainingSession
import com.ojnexus.core.model.TrainingType
import com.ojnexus.core.model.ReviewQueueItem
import com.ojnexus.core.model.ReviewResult
import com.ojnexus.core.model.TaskType
import com.ojnexus.core.model.TrainingTask
import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardActionTest {
    @Test
    fun `now prefers an active session over an incomplete task`() {
        val state = state(
            activeSession = session(42L, SessionState.PAUSED),
            todayTasks = listOf(task(7L, 101L)),
        )

        assertEquals(
            DashboardAction.ResumeSession(42L),
            deriveDashboardActions(state).now,
        )
    }

    @Test
    fun `now carries the first incomplete task and its problem id`() {
        val state = state(
            todayTasks = listOf(
                task(1L, 11L, completed = true),
                task(2L, 12L),
            ),
        )

        assertEquals(DashboardAction.OpenTask(2L, 12L), deriveDashboardActions(state).now)
    }

    @Test
    fun `next precedence is review then submission then contest`() {
        val submission = submission("req-1")
        val contest = contest("cf-9")

        assertEquals(
            DashboardAction.OpenReview(5L),
            deriveDashboardActions(
                state(
                    nextReview = review(5L),
                    actionableSubmission = submission,
                    nextContest = contest,
                ),
            ).next,
        )
        assertEquals(
            DashboardAction.OpenSubmission("req-1"),
            deriveDashboardActions(state(actionableSubmission = submission, nextContest = contest)).next,
        )
        assertEquals(
            DashboardAction.OpenContest(JudgeId.CODEFORCES.id, "cf-9"),
            deriveDashboardActions(state(nextContest = contest)).next,
        )
    }

    @Test
    fun `sync attention opens settings and quiet signal is a no action`() {
        val attention = state(syncAttention = true)
        assertEquals(DashboardAction.OpenSettings, deriveDashboardActions(attention).signal)
        assertEquals(DashboardAction.NoAction, deriveDashboardActions(state()).signal)
    }

    private fun state(
        activeSession: TrainingSession? = null,
        todayTasks: List<TrainingTask> = emptyList(),
        nextReview: ReviewQueueItem? = null,
        actionableSubmission: SubmissionJobEntity? = null,
        nextContest: ContestEntity? = null,
        syncAttention: Boolean = false,
    ) = DashboardUiState(
        todayTasks = todayTasks,
        week = WeekSummary(0, 0, 0L),
        currentStreak = 0,
        longestStreak = 0,
        nextReview = nextReview,
        recent = emptyList(),
        loadWeek = emptyList(),
        summary = DashboardSummary(0, 0, 0, null),
        nextContest = nextContest,
        nowSeconds = 0L,
        activeSession = activeSession,
        actionableSubmission = actionableSubmission,
        syncAttention = syncAttention,
    )

    private fun session(id: Long, state: SessionState) = TrainingSession(
        id = id,
        type = TrainingType.PRACTICE,
        state = state,
        startedAt = 1L,
        pausedAt = null,
        totalPausedMs = 0L,
        finishedAt = null,
        targetDurationMin = null,
        targetTag = null,
        note = null,
    )

    private fun task(id: Long, problemId: Long, completed: Boolean = false) = TrainingTask(
        id = id,
        dateEpochDay = 1L,
        type = TaskType.SOLVE,
        problemId = problemId,
        problemTitle = "P$problemId",
        title = null,
        completed = completed,
        priority = 1,
        sortOrder = id.toInt(),
        createdAt = 1L,
    )

    private fun review(problemId: Long) = ReviewQueueItem(
        problemId = problemId,
        problemTitle = "Review $problemId",
        judge = JudgeId.LOCAL,
        difficulty = null,
        stage = 0,
        dueAt = 1L,
        dueDayIndex = 1L,
        lastResult = ReviewResult.FAIL,
    )

    private fun submission(requestId: String) = SubmissionJobEntity(
        judge = JudgeId.LUOGU.id,
        requestId = requestId,
        kind = "PROBLEM",
        language = "cpp",
        status = "PENDING",
        createdAt = 1L,
        updatedAt = 1L,
    )

    private fun contest(id: String) = ContestEntity(
        judge = JudgeId.CODEFORCES.id,
        externalContestId = id,
        name = "Contest $id",
        phase = "BEFORE",
        durationSeconds = 7_200L,
        startTimeSeconds = 1_000L,
        updatedAt = 1L,
    )
}
