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
}
