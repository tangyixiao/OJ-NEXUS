package com.ojnexus.app

import com.ojnexus.core.model.JudgeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaletteQueryTest {

    @Test
    fun `judge aliases normalize and preserve multi word payload`() {
        assertEquals(
            PaletteQuery.SearchProblems(JudgeId.CODEFORCES, "1029e"),
            parsePaletteQuery("  CF   1029E  "),
        )
        assertEquals(
            PaletteQuery.SearchProblems(JudgeId.CODEFORCES, "1029e"),
            parsePaletteQuery("codeforces 1029e"),
        )
        assertEquals(
            PaletteQuery.SearchProblems(JudgeId.ATCODER, "abc 242g"),
            parsePaletteQuery("ac abc 242g"),
        )
        assertEquals(
            PaletteQuery.SearchProblems(JudgeId.LUOGU, "p4551"),
            parsePaletteQuery("luogu p4551"),
        )
    }

    @Test
    fun `search prefix creates judge less local search`() {
        assertEquals(
            PaletteQuery.SearchProblems(null, "segment tree"),
            parsePaletteQuery("search segment tree"),
        )
        assertEquals(
            PaletteQuery.SearchProblems(null, "segment tree"),
            parsePaletteQuery("  search\t segment   tree  "),
        )
    }

    @Test
    fun `blank or unknown command is not a direct query`() {
        assertNull(parsePaletteQuery(""))
        assertNull(parsePaletteQuery("search"))
        assertNull(parsePaletteQuery("unknown 1029e"))
    }

    @Test
    fun `review due starts the review run`() {
        assertEquals(PaletteQuery.ReviewDue, parsePaletteQuery("review due"))
        assertEquals(PaletteQuery.ReviewDue, parsePaletteQuery("  REVIEW   DUE  "))
        assertNull(parsePaletteQuery("review tomorrow"))
        assertNull(parsePaletteQuery("review"))
    }

    @Test
    fun `sprint takes a bounded target difficulty`() {
        assertEquals(PaletteQuery.SprintPlan(1800), parsePaletteQuery("sprint 1800"))
        assertEquals(PaletteQuery.SprintPlan(1800), parsePaletteQuery("  SPRINT\t1800 "))
        assertNull(parsePaletteQuery("sprint abc"))
        assertNull(parsePaletteQuery("sprint 99"))
        assertNull(parsePaletteQuery("sprint 3501"))
        assertNull(parsePaletteQuery("sprint"))
    }

    @Test
    fun `at prefixed judge aliases reach the same commands`() {
        assertEquals(
            PaletteQuery.SearchProblems(JudgeId.CODEFORCES, "1029e"),
            parsePaletteQuery("@cf 1029e"),
        )
        assertEquals(PaletteQuery.ReviewDue, parsePaletteQuery("@review due"))
        assertEquals(
            PaletteQuery.UnsolvedRange(JudgeId.ATCODER, null, null),
            parsePaletteQuery("@ac unsolved"),
        )
    }

    @Test
    fun `unsolved parses judge and optional difficulty range`() {
        assertEquals(
            PaletteQuery.UnsolvedRange(JudgeId.CODEFORCES, 1600, 1900),
            parsePaletteQuery("@cf unsolved 1600-1900"),
        )
        assertEquals(
            PaletteQuery.UnsolvedRange(JudgeId.CODEFORCES, 1600, 1900),
            parsePaletteQuery("cf unsolved 1600-1900"),
        )
        assertEquals(
            PaletteQuery.UnsolvedRange(null, 1600, 1900),
            parsePaletteQuery("unsolved 1600-1900"),
        )
        assertEquals(
            PaletteQuery.UnsolvedRange(JudgeId.CODEFORCES, null, null),
            parsePaletteQuery("cf unsolved"),
        )
        // Open ranges.
        assertEquals(
            PaletteQuery.UnsolvedRange(null, 1600, null),
            parsePaletteQuery("unsolved 1600-"),
        )
        assertEquals(
            PaletteQuery.UnsolvedRange(null, null, 1900),
            parsePaletteQuery("unsolved -1900"),
        )
        // Exact difficulty.
        assertEquals(
            PaletteQuery.UnsolvedRange(null, 1800, 1800),
            parsePaletteQuery("unsolved 1800"),
        )
    }

    @Test
    fun `unsolved rejects malformed payloads`() {
        assertNull(parsePaletteQuery("unsolved abc"))
        assertNull(parsePaletteQuery("unsolved 1600 1900"))
        assertNull(parsePaletteQuery("unsolved --"))
        assertNull(parsePaletteQuery("unsolved -"))
        assertNull(parsePaletteQuery("cf unsolved with words"))
    }

    @Test
    fun `difficulty range parser accepts exact closed and open forms`() {
        assertEquals(1600 to 1900, parseDifficultyRange("1600-1900"))
        assertEquals(1800 to 1800, parseDifficultyRange("1800"))
        assertEquals(1600 to null, parseDifficultyRange("1600-"))
        assertEquals(null to 1900, parseDifficultyRange("-1900"))
        assertNull(parseDifficultyRange("-"))
        assertNull(parseDifficultyRange("abc"))
        assertNull(parseDifficultyRange("1600-1900-2000"))
    }
}
