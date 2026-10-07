package com.ojnexus.core.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ojnexus.app.NexusBottomBar
import com.ojnexus.app.NexusDestination
import com.ojnexus.core.designsystem.component.NexusSection
import com.ojnexus.core.designsystem.component.NexusTopBar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedLayoutComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sectionKeepsTrailingActionVisibleWithLongEnglishHeading() {
        assertSectionFits("SYNCHRONIZATION OPERATIONS HISTORY", "VIEW ALL")
    }

    @Test
    fun sectionKeepsTrailingActionVisibleWithLongChineseHeading() {
        assertSectionFits("同步操作历史与连接器状态", "查看全部")
    }

    @Test
    fun sectionWithoutTrailingContentKeepsFullHeadingVisible() {
        narrowLargeFont {
            NexusSection(label = "SYNCHRONIZATION OPERATIONS HISTORY") {}
        }
        composeRule.onNodeWithText("SYNCHRONIZATION OPERATIONS HISTORY")
            .assertIsDisplayed().assertTextFits()
    }

    @Test
    fun topBarKeepsTitleAndTrailingActionSeparateAtLargeFont() {
        narrowLargeFont {
            NexusTopBar("SYNCHRONIZATION OPERATIONS HISTORY") {
                Text("SYNC", style = NexusTheme.typography.sectionLabel)
            }
        }
        val title = composeRule.onNodeWithText("SYNCHRONIZATION OPERATIONS HISTORY")
        val action = composeRule.onNodeWithText("SYNC")
        title.assertIsDisplayed()
        action.assertIsDisplayed().assertTextFits()
        val layout = title.textLayout()
        assertEquals(1, layout.lineCount)
        assertTrue(
            "Screen title is clipped vertically: size=${layout.size}, bottom=${layout.getLineBottom(0)}",
            layout.getLineBottom(0) <= layout.size.height + 1f,
        )
        assertTrue(title.fetchSemanticsNode().boundsInRoot.right <= action.fetchSemanticsNode().boundsInRoot.left)
    }

    @Test
    fun bottomTabsKeepAllLabelsVisibleAndAllDestinationsReachableAtLargeFont() {
        val selected = mutableListOf<NexusDestination>()
        narrowLargeFont {
            NexusBottomBar(
                destinations = NexusDestination.entries,
                currentRoute = NexusDestination.DASHBOARD.route,
                onSelect = { selected += it },
                onOpenCommandPalette = {},
            )
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var labelTop: Float? = null
        for (destination in NexusDestination.entries) {
            val label = context.getString(destination.labelRes)
            val text = composeRule.onNodeWithText(label, useUnmergedTree = true)
                .assertIsDisplayed().assertTextFits()
            val top = text.fetchSemanticsNode().boundsInRoot.top
            labelTop?.let { assertEquals("Tab label rows must align", it, top, 1f) }
            labelTop = top
            composeRule.onNodeWithText(label).performClick()
        }
        composeRule.runOnIdle { assertEquals(NexusDestination.entries.toList(), selected) }
    }

    @Test
    fun commandRowHasFullWidthButtonTargetIncludingBothInsets() {
        var opened = 0
        narrowLargeFont {
            NexusBottomBar(
                destinations = NexusDestination.entries,
                currentRoute = NexusDestination.DASHBOARD.route,
                onSelect = {},
                onOpenCommandPalette = { opened++ },
            )
        }
        val command = composeRule.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button),
        )
        command.assertIsDisplayed().assertHeightIsAtLeast(48.dp).assertWidthIsEqualTo(320.dp)
        command.performTouchInput { click(Offset(1f, height / 2f)) }
        command.performTouchInput { click(Offset(width - 1f, height / 2f)) }
        composeRule.runOnIdle { assertEquals(2, opened) }
    }

    private fun assertSectionFits(heading: String, actionLabel: String) {
        var clicked = false
        narrowLargeFont {
            NexusSection(
                label = heading,
                trailing = {
                    Text(
                        actionLabel,
                        modifier = Modifier.clickable { clicked = true },
                        style = NexusTheme.typography.sectionLabel,
                    )
                },
            ) {}
        }
        val label = composeRule.onNodeWithText(heading)
        val action = composeRule.onNodeWithText(actionLabel)
        action.assertIsDisplayed().assertTextFits().performClick()
        label.assertIsDisplayed().assertTextFits()
        assertTrue(label.fetchSemanticsNode().boundsInRoot.right < action.fetchSemanticsNode().boundsInRoot.left)
        composeRule.runOnIdle { assertTrue(clicked) }
    }

    private fun narrowLargeFont(content: @Composable () -> Unit) {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2f)) {
                NexusTheme(reduceMotion = true, hapticsEnabled = false) {
                    Box(Modifier.width(320.dp)) { content() }
                }
            }
        }
    }

    private fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals("Expected one text layout", 1, layouts.size)
        return layouts.single()
    }

    private fun SemanticsNodeInteraction.assertTextFits(): SemanticsNodeInteraction {
        val layout = textLayout()
        // A paragraph can retain the available width even when Text measures to its content.
        // Compare rendered line extents, allowing one pixel for Int size rounding.
        assertFalse("Text lines are truncated: ${layout.layoutInput.text.text}", layout.multiParagraph.didExceedMaxLines)
        for (line in 0 until layout.lineCount) {
            assertTrue(
                "Text is clipped horizontally: ${layout.layoutInput.text.text}",
                layout.getLineLeft(line) >= -1f && layout.getLineRight(line) <= layout.size.width + 1f,
            )
            assertTrue(
                "Text is clipped vertically: ${layout.layoutInput.text.text}",
                layout.getLineBottom(line) <= layout.size.height + 1f,
            )
        }
        return this
    }
}
