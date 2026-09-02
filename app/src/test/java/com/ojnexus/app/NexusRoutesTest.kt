package com.ojnexus.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NexusRoutesTest {
    @Test
    fun `settings routes keep stable ids`() {
        assertEquals("settings", NexusRoutes.SETTINGS)
        assertEquals("settings/openapp", NexusRoutes.SETTINGS_OPENAPP)
        assertEquals("settings/luogu", NexusRoutes.SETTINGS_LUOGU)
    }

    @Test
    fun `workspace route encodes pid and title context`() {
        val route = NexusRoutes.workspace("B/4132", "[信息与未来] 简单")

        assertTrue(route.startsWith("workspace/B%2F4132?title="))
        assertTrue("[信息与未来] 简单" !in route)
        assertTrue("%5B" in route)
    }

    @Test
    fun `workspace route omits blank title`() {
        assertEquals("workspace/B%2F4132", NexusRoutes.workspace("B/4132", "  "))
    }

    @Test
    fun `workspace route encodes sample context`() {
        val route = NexusRoutes.workspace("P1001", "A+B", "1 2\n", "3\n")

        assertTrue(route.startsWith("workspace/P1001?title="))
        assertTrue("sampleInput=" in route)
        assertTrue("sampleOutput=" in route)
        assertTrue("1 2" !in route)
    }

    @Test
    fun `workspace route omits blank sample context`() {
        assertEquals(
            "workspace/P1001?title=A%2BB",
            NexusRoutes.workspace("P1001", "A+B", " ", ""),
        )
    }
}
