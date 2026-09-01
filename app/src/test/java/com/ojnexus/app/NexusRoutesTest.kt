package com.ojnexus.app

import org.junit.Assert.assertEquals
import org.junit.Test

class NexusRoutesTest {
    @Test
    fun `settings routes keep stable ids`() {
        assertEquals("settings", NexusRoutes.SETTINGS)
        assertEquals("settings/openapp", NexusRoutes.SETTINGS_OPENAPP)
        assertEquals("settings/luogu", NexusRoutes.SETTINGS_LUOGU)
    }
}
