package com.ojnexus.feature.dashboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ojnexus.core.data.sync.SyncPhase
import com.ojnexus.core.database.entity.JudgeAccountEntity
import com.ojnexus.core.database.entity.SyncStateEntity
import com.ojnexus.core.designsystem.NexusTheme
import com.ojnexus.core.model.JudgeId
import com.ojnexus.core.model.ReviewQueueItem
import com.ojnexus.core.model.ReviewResult
import com.ojnexus.core.model.TaskType
import com.ojnexus.core.model.TrainingTask
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashboardCommandSurfaceComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun commandSurfaceDisplaysAndRoutesThreeSignals() {
        val targets = mutableListOf<DashboardSurfaceTarget>()
        val surface = deriveDashboardCommandSurface(
            state(
                todayTasks = listOf(task()),
                nextReview = review(),
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

        composeRule.setContent {
            NexusTheme(reduceMotion = true) {
                DashboardCommandSurfaceSection(surface) { targets += it }
            }
        }

        composeRule.onNodeWithText("COMMAND SIGNAL").assertIsDisplayed()
        composeRule.onNodeWithText("P1001").assertIsDisplayed()
        composeRule.onNodeWithText("Review P").assertIsDisplayed()
        composeRule.onNodeWithText("SYNC ATTENTION").assertIsDisplayed()
        composeRule.onNode(
            hasContentDescription("Open NOW command: P1001") and hasClickAction(),
        ).performClick()
        composeRule.onNode(
            hasContentDescription("Open NEXT command: Review P") and hasClickAction(),
        ).performClick()
        composeRule.onNode(
            hasContentDescription("Open SIGNAL command: SYNC ATTENTION") and hasClickAction(),
        ).performClick()

        composeRule.runOnIdle {
            assertEquals(
                listOf(
                    DashboardSurfaceTarget.TRAINING,
                    DashboardSurfaceTarget.REVIEW,
                    DashboardSurfaceTarget.SETTINGS,
                ),
                targets,
            )
        }
    }

    @Test
    fun commandSurfaceKeepsActionTextVisibleAtNarrowLargeFont() {
        val actions = mutableListOf<DashboardAction>()
        val surface = deriveDashboardCommandSurface(
            state(todayTasks = listOf(task())),
        )

        composeRule.setContent {
            NexusTheme(reduceMotion = true) {
                CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                    Box(Modifier.width(360.dp)) {
                        DashboardCommandSurfaceSection(
                            surface = surface,
                            onAction = { actions += it },
                            onTarget = {},
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText("COMMAND SIGNAL").assertIsDisplayed()
        composeRule.onNodeWithText("P1001").assertIsDisplayed()
        composeRule.onNodeWithText("NO NEXT COMMAND").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(emptyList<DashboardAction>(), actions)
        }
    }

    private fun state(
        todayTasks: List<TrainingTask> = emptyList(),
        nextReview: ReviewQueueItem? = null,
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
        nextContest = null,
        nowSeconds = 0L,
    )

    private fun task() = TrainingTask(
        id = 1L,
        dateEpochDay = 1L,
        type = TaskType.SOLVE,
        problemId = 1L,
        problemTitle = "P1001",
        title = "A+B",
        completed = false,
        priority = 1,
        sortOrder = 1,
        createdAt = 1L,
    )

    private fun review() = ReviewQueueItem(
        problemId = 2L,
        problemTitle = "Review P",
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
