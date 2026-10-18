package com.ojnexus.core.startup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupTracerTest {

    private var now = 0L

    private fun tracer() = StartupTracer { now }

    @Test
    fun `span duration measures begin to end`() {
        val tracer = tracer()
        tracer.begin("app-create")
        now = 120
        tracer.end("app-create")
        assertEquals(120L, tracer.spans.single().durationMs())
        assertEquals(120L, tracer.totalMs())
    }

    @Test
    fun `open span reports null duration and total`() {
        val tracer = tracer()
        tracer.begin("app-create")
        now = 500
        assertNull(tracer.spans.single().durationMs())
        assertNull(tracer.totalMs())
    }

    @Test
    fun `re-ending keeps the first end and unknown end is a no-op`() {
        val tracer = tracer()
        tracer.begin("a")
        now = 10
        tracer.end("a")
        now = 99
        tracer.end("a")
        tracer.end("never-begun")
        assertEquals(10L, tracer.spans.single().endedAtUptimeMs)
        assertEquals(1, tracer.spans.size)
    }

    @Test
    fun `negative duration clamps to zero`() {
        val tracer = tracer()
        now = 50
        tracer.begin("late")
        now = 40
        tracer.end("late")
        assertEquals(0L, tracer.spans.single().durationMs())
    }

    @Test
    fun `total spans the earliest begin to the latest end`() {
        val tracer = tracer()
        now = 100
        tracer.begin("app-create")
        now = 150
        tracer.end("app-create")
        tracer.begin("first-frame")
        now = 1300
        tracer.end("first-frame")
        assertEquals(1200L, tracer.totalMs())
    }

    @Test
    fun `budget verdict boundaries`() {
        assertEquals(StartupBudget.WITHIN, StartupBudget.verdict(1500))
        assertEquals(StartupBudget.OVER, StartupBudget.verdict(1501))
        assertEquals(StartupBudget.OVER, StartupBudget.verdict(null))
        assertTrue(StartupBudget.COLD_START_BUDGET_MS == 1500L)
    }
}
