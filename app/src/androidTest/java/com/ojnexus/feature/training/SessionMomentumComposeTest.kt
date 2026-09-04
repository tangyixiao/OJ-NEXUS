package com.ojnexus.feature.training

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ojnexus.core.designsystem.NexusTheme
import com.ojnexus.core.model.SessionProblem
import com.ojnexus.core.model.SessionState
import com.ojnexus.core.model.TrainingSession
import com.ojnexus.core.model.TrainingType
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionMomentumComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun pendingRailShowsNextProblemAndOpensIt() {
        var openedProblemId: Long? = null
        val next = sessionProblem(1L, title = "Next")
        val selected = sessionProblem(2L, title = "Selected")

        composeRule.setContent {
            NexusTheme(reduceMotion = true) {
                SessionMomentumRail(
                    session = session(),
                    problems = listOf(next, selected),
                    selectedProblemId = selected.problemId,
                    elapsedFlow = MutableStateFlow(3 * 60_000L),
                    onOpenNext = { openedProblemId = it },
                )
            }
        }

        composeRule.onNodeWithText("SESSION MOMENTUM").assertIsDisplayed()
        composeRule.onNodeWithText("NOW").assertIsDisplayed()
        composeRule.onNodeWithText("NEXT").assertIsDisplayed()
        composeRule.onNodeWithText("LEFT").assertIsDisplayed()
        composeRule.onNodeWithText("2 PENDING").assertIsDisplayed()
        composeRule
            .onNode(
                hasContentDescription("Open next problem CODEFORCES 1A") and hasClickAction(),
            )
            .performClick()

        composeRule.runOnIdle { assertEquals(1L, openedProblemId) }
    }

    @Test
    fun completeRailShowsCompletionWithoutNextAction() {
        composeRule.setContent {
            NexusTheme(reduceMotion = true) {
                SessionMomentumRail(
                    session = session(),
                    problems = listOf(
                        sessionProblem(1L, solved = true),
                        sessionProblem(2L, solved = true),
                    ),
                    selectedProblemId = 1L,
                    elapsedFlow = MutableStateFlow(0L),
                    onOpenNext = {},
                )
            }
        }

        composeRule.onNodeWithText("ALL PROBLEMS RESOLVED").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("OPEN NEXT").fetchSemanticsNodes().isEmpty())
    }

    private fun session() = TrainingSession(
        id = 1L,
        type = TrainingType.PRACTICE,
        state = SessionState.RUNNING,
        startedAt = 1L,
        pausedAt = null,
        totalPausedMs = 0L,
        finishedAt = null,
        targetDurationMin = 25,
        targetTag = null,
        note = null,
    )

    private fun sessionProblem(
        id: Long,
        title: String = "Problem $id",
        solved: Boolean = false,
    ) = SessionProblem(
        problemId = id,
        title = title,
        difficulty = null,
        solved = solved,
        attempts = if (solved) 1 else 0,
        judge = "codeforces",
        externalId = "${id}A",
    )
}
