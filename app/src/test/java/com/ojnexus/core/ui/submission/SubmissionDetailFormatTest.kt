package com.ojnexus.core.ui.submission

import com.ojnexus.core.model.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubmissionDetailFormatTest {

    @Test
    fun `time formats seconds and millis honestly`() {
        assertEquals("1.50 s", formatSubmissionTime(1500))
        assertEquals("0 ms", formatSubmissionTime(0))
        assertEquals("1.00 s", formatSubmissionTime(1000))
    }

    @Test
    fun `memory formats kib and mib honestly`() {
        assertEquals("512 KiB", formatSubmissionMemory(512))
        assertEquals("1.0 MiB", formatSubmissionMemory(1024))
        assertEquals("2.5 MiB", formatSubmissionMemory(2560))
        assertEquals("0 KiB", formatSubmissionMemory(0))
    }
}

class AttemptDetailMappingTest {

    @Test
    fun `manual attempt maps to LOCAL judge without links`() {
        val attempt = com.ojnexus.core.model.Attempt(
            id = 1L,
            problemId = 2L,
            timestamp = 1_700_000_000_000L,
            verdict = Verdict.AC,
            rawVerdict = "OK",
            durationMinutes = 25,
            language = "C++",
            note = null,
        )
        val detail = com.ojnexus.feature.problems.toSubmissionDetailModel(attempt, null)

        assertEquals("local", detail.judgeId)
        assertEquals(Verdict.AC, detail.verdict)
        assertEquals("OK", detail.rawVerdict)
        assertEquals("C++", detail.language)
        assertEquals(1_700_000_000_000L, detail.timestampEpochMs)
        assertEquals(null, detail.submissionUrl)
        assertEquals(null, detail.problemUrl)
    }

    @Test
    fun `remote attempt carries the precomputed submission url`() {
        val attempt = com.ojnexus.core.model.Attempt(
            id = 3L,
            problemId = 4L,
            timestamp = 1_700_000_000_000L,
            verdict = Verdict.WA,
            rawVerdict = "WRONG_ANSWER",
            durationMinutes = null,
            language = "Kotlin",
            note = null,
            sourceJudge = "codeforces",
            externalSubmissionId = "42",
            contestId = "1873",
        )
        val detail = com.ojnexus.feature.problems.toSubmissionDetailModel(
            attempt,
            "https://codeforces.com/contest/1873/submission/42",
        )

        assertEquals("codeforces", detail.judgeId)
        assertEquals(Verdict.WA, detail.verdict)
        assertEquals("https://codeforces.com/contest/1873/submission/42", detail.submissionUrl)
    }
}

class SubmissionDetailMappingTest {

    @Test
    fun `sheet visibility follows terminal statuses only`() {
        assertTrue(
            com.ojnexus.feature.submissions.submissionDetailSheetVisible("READY"),
        )
        assertTrue(
            com.ojnexus.feature.submissions.submissionDetailSheetVisible("FAILED"),
        )
        assertEquals(
            false,
            com.ojnexus.feature.submissions.submissionDetailSheetVisible("PENDING"),
        )
    }
}
