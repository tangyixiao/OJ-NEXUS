package com.ojnexus.feature.training

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ojnexus.core.designsystem.NexusTheme
import com.ojnexus.core.model.SessionProblem
import com.ojnexus.core.model.Verdict
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionCommandDeckComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun quickActions_showIdentityAndLogEveryVerdict() {
        val logged = mutableListOf<Verdict>()
        val problem = SessionProblem(
            problemId = 1029L,
            title = "Tree with Small Distances",
            difficulty = 1900,
            solved = false,
            attempts = 3,
            judge = "codeforces",
            externalId = "1029E",
        )

        composeRule.setContent {
            NexusTheme(reduceMotion = true) {
                SessionQuickActions(problem) { logged += it }
            }
        }

        composeRule.onNodeWithText("LOG RESULT").assertIsDisplayed()
        composeRule.onNodeWithText("SELECTED · CODEFORCES 1029E").assertIsDisplayed()
        listOf("AC", "WA", "TLE", "MLE", "RE", "CE", "PE", "OTHER").forEach {
            composeRule.onNodeWithText(it).assertIsDisplayed()
        }

        composeRule
            .onNode(hasContentDescription("Log WA for CODEFORCES 1029E") and hasClickAction())
            .performClick()

        composeRule.runOnIdle { assertEquals(listOf(Verdict.WA), logged) }
    }
}
