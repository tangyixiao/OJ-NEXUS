package com.ojnexus.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JudgeIdTest {

    @Test
    fun `ids are stable lowercase slugs`() {
        assertEquals("codeforces", JudgeId.CODEFORCES.id)
        assertEquals("atcoder", JudgeId.ATCODER.id)
        assertEquals("luogu", JudgeId.LUOGU.id)
    }

    @Test
    fun `round trips through fromId`() {
        JudgeId.entries.forEach { judge ->
            assertEquals(judge, JudgeId.fromId(judge.id))
        }
    }

    @Test
    fun `fromId returns null for unknown judges instead of throwing`() {
        assertNull(JudgeId.fromId("kattis"))
        assertNull(JudgeId.fromId(""))
        assertNull(JudgeId.fromId("CODEFORCES"))
    }

    @Test
    fun `display names are uppercase codes`() {
        JudgeId.entries.forEach { judge ->
            assertEquals(judge.displayName, judge.displayName.uppercase())
        }
    }
}
