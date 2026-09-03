package com.ojnexus.feature.problems

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ojnexus.core.designsystem.NexusTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProblemLibraryTrainingComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun actionRail_showsCountAndInvokesCallback() {
        var clicked = false
        composeRule.setContent {
            NexusTheme {
                LibraryTrainingActionRail(problemCount = 3) { clicked = true }
            }
        }

        composeRule.onNodeWithText("BUILD FROM VIEW").assertIsDisplayed()
        composeRule.onNodeWithText("3 PROBLEMS READY FOR TRAINING").assertIsDisplayed()
        composeRule
            .onNode(
                hasContentDescription("Build a training session from 3 visible problems") and
                    hasClickAction(),
            )
            .performClick()

        composeRule.runOnIdle { assertTrue(clicked) }
    }

    @Test
    fun actionRail_isHiddenForEmptyView() {
        composeRule.setContent {
            NexusTheme {
                LibraryTrainingActionRail(problemCount = 0) { }
            }
        }

        composeRule.onAllNodesWithText("BUILD FROM VIEW").assertCountEquals(0)
    }
}
