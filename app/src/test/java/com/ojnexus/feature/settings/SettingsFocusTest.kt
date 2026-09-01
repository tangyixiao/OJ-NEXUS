package com.ojnexus.feature.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsFocusTest {
    @Test
    fun `OpenApp focus scrolls when coordinates are available`() {
        assertTrue(
            shouldScrollToFocusedSettingsSection(
                focusOpenApp = true,
                focusLuogu = false,
                viewportTop = 100,
                targetRootY = 900,
            ),
        )
    }

    @Test
    fun `Luogu focus scrolls when coordinates are available`() {
        assertTrue(
            shouldScrollToFocusedSettingsSection(
                focusOpenApp = false,
                focusLuogu = true,
                viewportTop = 100,
                targetRootY = 900,
            ),
        )
    }

    @Test
    fun `ordinary settings does not scroll`() {
        assertFalse(
            shouldScrollToFocusedSettingsSection(
                focusOpenApp = false,
                focusLuogu = false,
                viewportTop = 100,
                targetRootY = 900,
            ),
        )
    }

    @Test
    fun `missing coordinates do not scroll`() {
        assertFalse(
            shouldScrollToFocusedSettingsSection(
                focusOpenApp = true,
                focusLuogu = false,
                viewportTop = null,
                targetRootY = 900,
            ),
        )
        assertFalse(
            shouldScrollToFocusedSettingsSection(
                focusOpenApp = false,
                focusLuogu = true,
                viewportTop = 100,
                targetRootY = null,
            ),
        )
    }
}
