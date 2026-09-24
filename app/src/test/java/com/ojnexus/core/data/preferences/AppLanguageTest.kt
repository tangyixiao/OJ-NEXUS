package com.ojnexus.core.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {

    @Test
    fun `language modes convert to and from application locale tags`() {
        assertEquals("", AppLanguage.SYSTEM.localeTag)
        assertEquals("en", AppLanguage.ENGLISH.localeTag)
        assertEquals("zh-CN", AppLanguage.SIMPLIFIED_CHINESE.localeTag)

        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromLocaleTags(""))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromLocaleTags("en"))
        assertEquals(AppLanguage.SIMPLIFIED_CHINESE, AppLanguage.fromLocaleTags("zh-CN"))
    }

    @Test
    fun `unknown application locale falls back to system mode`() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromLocaleTags("ja"))
    }
}
