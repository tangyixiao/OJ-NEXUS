package com.ojnexus.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ojnexus.MainActivity
import com.ojnexus.OjNexusApplication
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProblemLibraryTrainingHandoffComposeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun seedLibraryWithoutActiveSession() {
        val application = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .applicationContext as OjNexusApplication
        runBlocking {
            application.container.database.clearAllTables()
            application.container.demoSeeder.insertDemoData()
        }
    }

    @Test
    fun libraryView_opensEditableTrainingForm_andCancelKeepsSessionEmpty() {
        composeRule.onNodeWithContentDescription("OPEN PROBLEM LIBRARY").performClick()
        composeRule.waitUntil(20_000) {
            composeRule.onAllNodesWithText("BUILD FROM VIEW").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("BUILD FROM VIEW").performClick()
        composeRule.waitUntil(20_000) {
            composeRule.onAllNodesWithText("NEW SESSION").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("LIBRARY VIEW").assertIsDisplayed()
        composeRule.onNodeWithText("PROBLEMS · 8 SELECTED").assertIsDisplayed()

        composeRule.onNodeWithText("CANCEL").performClick()
        composeRule.waitUntil(20_000) {
            composeRule.onAllNodesWithText("NO ACTIVE SESSION").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("NO ACTIVE SESSION").assertIsDisplayed()
    }
}
