package com.ojnexus.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ojnexus.core.designsystem.NexusTheme
import com.ojnexus.core.model.JudgeId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommandPaletteComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun directQuery_isRenderedAndInvokesSearchCallback() {
        var launched: PaletteQuery.SearchProblems? = null

        composeRule.setContent {
            NexusTheme {
                CommandPalette(
                    onDismiss = {},
                    onExecute = {},
                    onSearchProblems = { launched = it },
                )
            }
        }

        composeRule.onNode(hasSetTextAction()).performTextInput("cf 1029e")
        composeRule.onNodeWithText("DIRECT QUERY").assertIsDisplayed()

        composeRule
            .onNode(hasContentDescription("SEARCH CODEFORCES FOR 1029e") and hasClickAction())
            .performClick()

        composeRule.runOnIdle {
            assertEquals(
                PaletteQuery.SearchProblems(JudgeId.CODEFORCES, "1029e"),
                launched,
            )
        }
    }
}
