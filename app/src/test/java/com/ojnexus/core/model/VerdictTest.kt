package com.ojnexus.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class VerdictTest {

    @Test
    fun `maps codeforces raw verdicts`() {
        assertEquals(Verdict.AC, Verdict.fromRaw("OK"))
        assertEquals(Verdict.WA, Verdict.fromRaw("WRONG_ANSWER"))
        assertEquals(Verdict.TLE, Verdict.fromRaw("TIME_LIMIT_EXCEEDED"))
        assertEquals(Verdict.MLE, Verdict.fromRaw("MEMORY_LIMIT_EXCEEDED"))
        assertEquals(Verdict.RE, Verdict.fromRaw("RUNTIME_ERROR"))
        assertEquals(Verdict.CE, Verdict.fromRaw("COMPILATION_ERROR"))
        assertEquals(Verdict.PE, Verdict.fromRaw("PRESENTATION_ERROR"))
    }

    @Test
    fun `maps common short forms`() {
        assertEquals(Verdict.AC, Verdict.fromRaw("ac"))
        assertEquals(Verdict.WA, Verdict.fromRaw("wa"))
        assertEquals(Verdict.TLE, Verdict.fromRaw("TLE"))
    }

    @Test
    fun `maps atcoder style verdicts`() {
        assertEquals(Verdict.AC, Verdict.fromRaw("Accepted"))
        assertEquals(Verdict.WA, Verdict.fromRaw("Wrong Answer"))
        assertEquals(Verdict.TLE, Verdict.fromRaw("Time Limit Exceeded"))
        assertEquals(Verdict.RE, Verdict.fromRaw("Runtime Error"))
    }

    @Test
    fun `unknown blank and null degrade to OTHER without throwing`() {
        assertEquals(Verdict.OTHER, Verdict.fromRaw(null))
        assertEquals(Verdict.OTHER, Verdict.fromRaw(""))
        assertEquals(Verdict.OTHER, Verdict.fromRaw("   "))
        assertEquals(Verdict.OTHER, Verdict.fromRaw("SKIPPED"))
        assertEquals(Verdict.OTHER, Verdict.fromRaw("JUDGEMENT_FAILED"))
    }

    @Test
    fun `surrounding whitespace is tolerated`() {
        assertEquals(Verdict.AC, Verdict.fromRaw("  OK  "))
    }

    @Test
    fun `only AC counts as accepted`() {
        Verdict.entries
            .filter { it != Verdict.AC }
            .forEach { assertEquals(false, it.isAccepted) }
        assertEquals(true, Verdict.AC.isAccepted)
    }
}
