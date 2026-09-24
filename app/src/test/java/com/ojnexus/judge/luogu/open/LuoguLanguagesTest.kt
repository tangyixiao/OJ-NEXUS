package com.ojnexus.judge.luogu.open

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LuoguLanguagesTest {
    @Test
    fun `language options have unique official identifiers and keep C++14 as default`() {
        val ids = LuoguLanguages.options.map { it.id }

        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.contains(LuoguLanguages.DEFAULT_ID))
        assertEquals("C++14", LuoguLanguages.options.first { it.id == LuoguLanguages.DEFAULT_ID }.label)
    }
}
