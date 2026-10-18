package com.ojnexus.feature.training

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionDebriefExportTest {

    private fun row(
        judge: String = "CODEFORCES",
        externalId: String = "1234A",
        title: String = "TITLE",
        difficulty: Int? = 1600,
        attempts: Int = 2,
        lane: SessionDebriefLane = SessionDebriefLane.SOLVED,
        verdictLabel: String? = "AC",
        reviewLabel: String? = "IN REVIEW",
    ) = SessionDebriefExportRow(
        judge = judge,
        externalId = externalId,
        title = title,
        difficulty = difficulty,
        attempts = attempts,
        lane = lane,
        verdictLabel = verdictLabel,
        reviewLabel = reviewLabel,
    )

    @Test
    fun `full session renders every section and one table row per problem`() {
        val markdown = buildSessionDebriefMarkdown(fullExport())
        val lines = markdown.lines()

        assertTrue(lines.contains("# SESSION DEBRIEF"))
        assertTrue(lines.contains("- TYPE: CONTEST"))
        assertTrue(lines.contains("- STATE: FINISHED"))
        assertTrue(lines.contains("- STARTED: 2026-10-18 21:00"))
        assertTrue(lines.contains("- FINISHED: 2026-10-18 22:00"))
        assertTrue(lines.contains("- DURATION: DURATION 1H 00M"))
        assertTrue(lines.contains("- TARGET: TAG\\|PIPE"))
        assertTrue(lines.contains("- NOTE: A\\|NOTE"))
        assertTrue(lines.contains("## SUMMARY"))
        assertTrue(lines.contains("- SOLVED: 1"))
        assertTrue(lines.contains("- ATTEMPTS: 3"))
        assertTrue(lines.contains("- IN REVIEW: 2"))
        assertTrue(lines.contains("## PROBLEMS"))
        assertTrue(lines.contains("| JUDGE | ID | TITLE | RATING | ATTEMPTS | STATUS |"))
        assertTrue(lines.contains("| --- | --- | --- | --- | --- | --- |"))
        assertEquals(2, lines.count { it.startsWith("| CODEFORCES ") || it.startsWith("| ATCODER ") })
        // Escaped pipes stay inside the cell instead of breaking the column count.
        assertTrue(lines.contains("| CODEFORCES | 1234A | TITLE | 1600 | 2 | SOLVED / AC / IN REVIEW |"))
        assertTrue(lines.contains("| ATCODER | 100 | ABC |  | 1 | ATTENTION / WA |"))
        assertFalse(markdown.contains("- TARGET: TAG|PIPE"))
    }

    @Test
    fun `partial session omits optional lines instead of rendering them empty`() {
        val markdown = buildSessionDebriefMarkdown(
            fullExport().copy(
                finishedAtLabel = null,
                durationLabel = null,
                targetLabel = null,
                noteLabel = null,
                rows = listOf(row().copy(difficulty = null, verdictLabel = null, reviewLabel = null)),
            ),
        )
        val lines = markdown.lines()

        assertFalse(lines.any { it.startsWith("- FINISHED:") })
        assertFalse(lines.any { it.startsWith("- DURATION:") })
        assertFalse(lines.any { it.startsWith("- TARGET:") })
        assertFalse(lines.any { it.startsWith("- NOTE:") })
        assertTrue(lines.contains("| CODEFORCES | 1234A | TITLE |  | 2 | SOLVED |"))
    }

    @Test
    fun `empty session renders the no problems line and no table`() {
        val markdown = buildSessionDebriefMarkdown(
            fullExport().copy(solvedCount = 0, attemptCount = 0, reviewCount = 0, rows = emptyList()),
        )

        assertTrue(markdown.lines().contains("NO PROBLEMS LOGGED IN THIS SESSION."))
        assertFalse(markdown.contains("| JUDGE | ID |"))
        assertFalse(markdown.contains("| --- |"))
        assertTrue(markdown.lines().contains("- SOLVED: 0"))
    }

    @Test
    fun `newlines in text values never break the document structure`() {
        val markdown = buildSessionDebriefMarkdown(
            fullExport().copy(
                title = "LINE ONE\nLINE TWO",
                rows = listOf(row(title = "A\nB")),
            ),
        )
        val lines = markdown.lines()

        assertTrue(lines.contains("# LINE ONE LINE TWO"))
        assertTrue(
            lines.contains("| CODEFORCES | 1234A | A B | 1600 | 2 | SOLVED / AC / IN REVIEW |"),
        )
    }

    @Test
    fun `the document ends with exactly one newline`() {
        val markdown = buildSessionDebriefMarkdown(fullExport())

        assertTrue(markdown.endsWith("\n"))
        assertFalse(markdown.endsWith("\n\n"))
    }

    private fun fullExport() = SessionDebriefExport(
        title = "SESSION DEBRIEF",
        typeLabel = "CONTEST",
        stateLabel = "FINISHED",
        startedAtLabel = "2026-10-18 21:00",
        finishedAtLabel = "2026-10-18 22:00",
        durationLabel = "DURATION 1H 00M",
        targetLabel = "TAG|PIPE",
        noteLabel = "A|NOTE",
        solvedCount = 1,
        attemptCount = 3,
        reviewCount = 2,
        rows = listOf(
            row(),
            row(
                judge = "ATCODER",
                externalId = "100",
                title = "ABC",
                difficulty = null,
                attempts = 1,
                lane = SessionDebriefLane.ATTENTION,
                verdictLabel = "WA",
                reviewLabel = null,
            ),
        ),
    )
}
