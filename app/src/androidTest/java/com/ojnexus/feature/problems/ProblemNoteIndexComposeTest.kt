package com.ojnexus.feature.problems

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ojnexus.core.designsystem.NexusTheme
import com.ojnexus.core.model.JudgeId
import com.ojnexus.core.model.NoteField
import com.ojnexus.core.model.ProblemKey
import com.ojnexus.core.model.ProblemNoteEntry
import com.ojnexus.core.model.ProblemNotes
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device-level checks for the local note index. The surface renders from a plain state object,
 * so no ViewModel, database or network is involved.
 *
 * Two detail matters for these assertions:
 * - the chip rows scroll horizontally, so a chip interaction scrolls to the chip first;
 * - judge names and status words are also chip/pulse labels, so those are counted instead of
 *   asserted as a single node. Row identity is asserted through unique text (title, id, preview).
 */
@RunWith(AndroidJUnit4::class)
class ProblemNoteIndexComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun entry(
        problemId: Long,
        externalId: String,
        title: String,
        judge: JudgeId = JudgeId.CODEFORCES,
        solved: Boolean = false,
        inReview: Boolean = false,
        attemptCount: Int = 0,
        keyInsight: String = "",
        implementation: String = "",
        complexity: String = "",
        general: String = "",
    ) = ProblemNoteEntry(
        problemId = problemId,
        key = ProblemKey(judge = judge, externalId = externalId),
        title = title,
        difficulty = 1400,
        attemptCount = attemptCount,
        solved = solved,
        inReview = inReview,
        notes = ProblemNotes(
            problemId = problemId,
            keyInsight = keyInsight,
            implementationNotes = implementation,
            complexity = complexity,
            general = general,
            updatedAt = 0,
        ),
    )

    private val indexed = listOf(
        entry(
            problemId = 1L,
            externalId = "1A",
            title = "Theatre Square",
            keyInsight = "binary search on answer",
            complexity = "O(n log C)",
        ),
        entry(
            problemId = 2L,
            externalId = "abc300_f",
            title = "More Holidays",
            judge = JudgeId.ATCODER,
            solved = true,
            inReview = true,
            attemptCount = 4,
            implementation = "prefix sums with wraparound",
        ),
        entry(
            problemId = 3L,
            externalId = "P1001",
            title = "A+B Problem",
            judge = JudgeId.LUOGU,
            attemptCount = 1,
            general = "warm up",
        ),
    )

    private fun state(
        filter: ProblemNoteFilter = ProblemNoteFilter(),
        entries: List<ProblemNoteEntry> = indexed,
    ): NoteIndexUiState = NoteIndexUiState(
        entries = entries,
        visibleEntries = entries.applyNoteFilter(filter),
        filter = filter,
        summary = summarizeNoteIndex(entries, entries.applyNoteFilter(filter)),
    )

    private fun render(
        state: NoteIndexUiState,
        onFieldChange: (NoteField) -> Unit = {},
        onOpenProblem: (Long) -> Unit = {},
    ) {
        composeRule.setContent {
            NexusTheme {
                NoteIndexContent(
                    state = state,
                    onQueryChange = {},
                    onFieldChange = onFieldChange,
                    onJudgeChange = {},
                    onToggleUnsolved = {},
                    onClearFilters = {},
                    onOpenProblem = onOpenProblem,
                    onOpenLibrary = {},
                    onOpenRemote = {},
                )
            }
        }
    }

    @Test
    fun pulse_reportsIndexedVisibleUnsolvedAndReviewedCounts() {
        render(state())

        composeRule.onNodeWithText("NOTE INDEX PULSE").assertIsDisplayed()
        composeRule.onNodeWithText("INDEXED").assertIsDisplayed()
        composeRule.onNodeWithText("VISIBLE").assertIsDisplayed()
        // Indexed (3) and visible (3) read the same value; two notes are unsolved, one is in review.
        composeRule.onAllNodesWithText("3").assertCountEquals(2)
        composeRule.onAllNodesWithText("2").assertCountEquals(1)
        composeRule.onAllNodesWithText("1").assertCountEquals(1)
    }

    @Test
    fun row_showsProblemIdentityAndTheScopedNotePreview() {
        render(state(filter = ProblemNoteFilter(field = NoteField.KEY_INSIGHT)))

        composeRule.onNodeWithText("1A").assertIsDisplayed()
        composeRule.onNodeWithText("Theatre Square").assertIsDisplayed()
        composeRule.onNodeWithText("INSIGHT · binary search on answer").assertIsDisplayed()
    }

    @Test
    fun row_reportsStatusAsText() {
        render(state())

        // One pulse label plus one row tag each.
        composeRule.onAllNodesWithText("UNSOLVED").assertCountEquals(2)
        composeRule.onAllNodesWithText("REVIEW").assertCountEquals(2)
        composeRule.onNodeWithText("ATTEMPTED").assertIsDisplayed()
        composeRule.onNodeWithText("More Holidays").assertIsDisplayed()
    }

    @Test
    fun row_previewsTheWidestNoteFieldWhenNoScopeIsChosen() {
        render(state())

        composeRule.onNodeWithText("ALL · binary search on answer").assertIsDisplayed()
        composeRule.onNodeWithText("ALL · prefix sums with wraparound").assertIsDisplayed()
        composeRule.onNodeWithText("ALL · warm up").assertIsDisplayed()
    }

    @Test
    fun row_fallsBackWhenTheScopedFieldHasNoText() {
        render(state(filter = ProblemNoteFilter(field = NoteField.GENERAL)))

        composeRule.onAllNodesWithText("NO TEXT IN THIS FIELD").assertCountEquals(2)
        composeRule.onNodeWithText("GENERAL · warm up").assertIsDisplayed()
    }

    @Test
    fun row_tapOpensTheIndexedProblem() {
        var opened: Long? = null
        render(state(), onOpenProblem = { opened = it })

        composeRule.onNodeWithText("More Holidays").performClick()

        composeRule.runOnIdle { assertEquals(2L, opened) }
    }

    @Test
    fun fieldChip_reportsTheRequestedScope() {
        var requested: NoteField? = null
        render(state(), onFieldChange = { requested = it })

        composeRule.onNodeWithText("INSIGHT").performScrollTo().performClick()

        composeRule.runOnIdle { assertEquals(NoteField.KEY_INSIGHT, requested) }
    }

    @Test
    fun judgeChips_coverExactlyTheJudgesPresentInTheIndex() {
        render(state())

        // Each judge appears twice: once as a filter chip and once on its row. The chip row
        // scrolls horizontally, so the count is the assertion — not a viewport-relative one.
        composeRule.onAllNodesWithText("ATCODER").assertCountEquals(2)
        composeRule.onAllNodesWithText("LUOGU").assertCountEquals(2)
        composeRule.onAllNodesWithText("CODEFORCES").assertCountEquals(2)
        composeRule.onAllNodesWithText("LOCAL").assertCountEquals(0)
    }

    @Test
    fun clearFilters_isOfferedOnceAFilterIsActive() {
        render(state(filter = ProblemNoteFilter(query = "binary")))

        composeRule.onNodeWithText("CLEAR FILTERS").assertIsDisplayed()
    }

    @Test
    fun clearFilters_isNotOfferedForTheDefaultView() {
        render(state())

        composeRule.onAllNodesWithText("CLEAR FILTERS").assertCountEquals(0)
    }

    @Test
    fun emptyIndex_guidesTheUserToTheDetailScreen() {
        render(state(entries = emptyList()))

        composeRule.onNodeWithText("NO NOTES INDEXED").assertIsDisplayed()
        composeRule.onNodeWithText("SAVE NOTES FROM A PROBLEM DETAIL SCREEN").assertIsDisplayed()
    }

    @Test
    fun filteredToNothing_showsNoMatchInsteadOfTheEmptyLibraryState() {
        render(state(filter = ProblemNoteFilter(query = "nothing matches this")))

        composeRule.onNodeWithText("NO NOTES MATCH THE FILTER").assertIsDisplayed()
        composeRule.onAllNodesWithText("NO NOTES INDEXED").assertCountEquals(0)
    }
}
