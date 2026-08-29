package com.ojnexus.judge.codeforces

import com.ojnexus.core.model.Verdict
import com.ojnexus.judge.codeforces.mapper.CfMappers
import com.ojnexus.judge.codeforces.mapper.CfMappers.externalId
import com.ojnexus.judge.codeforces.mapper.CfMappers.mergeProblemset
import com.ojnexus.judge.codeforces.mapper.CfMappers.normalizeAvatar
import com.ojnexus.judge.codeforces.mapper.ContestPhase
import com.ojnexus.judge.codeforces.api.dto.CfProblemDto
import com.ojnexus.judge.codeforces.api.dto.CfProblemStatisticsDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CodeforcesMappersTest {

    // --- External problem id ---

    @Test
    fun `external id concatenates contest id and index`() {
        assertEquals("2134C", externalId(2134, "C"))
        assertEquals("1029E", externalId(1029, "E"))
    }

    @Test
    fun `problemset-only problems without contest id have no external id`() {
        val dto = CfProblemDto(contestId = null, index = "A", name = "Marathon")
        assertNull(externalId(dto))
    }

    // --- Verdict mapping (official Codeforces verdict set) ---

    @Test
    fun `official verdicts map to unified verdicts`() {
        val cases = mapOf(
            "OK" to Verdict.AC,
            "WRONG_ANSWER" to Verdict.WA,
            "TIME_LIMIT_EXCEEDED" to Verdict.TLE,
            "MEMORY_LIMIT_EXCEEDED" to Verdict.MLE,
            "RUNTIME_ERROR" to Verdict.RE,
            "COMPILATION_ERROR" to Verdict.CE,
            "PRESENTATION_ERROR" to Verdict.PE,
        )
        cases.forEach { (raw, expected) -> assertEquals(expected, CfMappers.submissionVerdict(raw)) }
    }

    @Test
    fun `non-standard verdicts degrade to OTHER with raw preserved at the call site`() {
        listOf(
            "IDLENESS_LIMIT_EXCEEDED",
            "SECURITY_VIOLATED",
            "CRASHED",
            "INPUT_PREPARATION_CRASHED",
            "CHALLENGED",
            "SKIPPED",
            "TESTING",
            "REJECTED",
            "SOME_FUTURE_VERDICT",
        ).forEach { raw -> assertEquals(Verdict.OTHER, CfMappers.submissionVerdict(raw)) }
        // null verdict (submission still being judged) maps to OTHER.
        assertEquals(Verdict.OTHER, CfMappers.submissionVerdict(null))
    }

    // --- Problemset merge ---

    @Test
    fun `problemset merges statistics by contestId and index, not by list position`() {
        val problems = listOf(
            CfProblemDto(contestId = 1, index = "A", name = "One A"),
            CfProblemDto(contestId = 1, index = "B", name = "One B"),
            CfProblemDto(contestId = 2, index = "A", name = "Two A"),
        )
        // Deliberately mis-ordered vs the problems list.
        val statistics = listOf(
            CfProblemStatisticsDto(contestId = 2, index = "A", solvedCount = 30),
            CfProblemStatisticsDto(contestId = 1, index = "B", solvedCount = 20),
            CfProblemStatisticsDto(contestId = 1, index = "A", solvedCount = 10),
        )
        val merged = mergeProblemset(problems, statistics)
        assertEquals(3, merged.size)
        assertEquals(10, merged.first { it.first.name == "One A" }.second?.solvedCount)
        assertEquals(20, merged.first { it.first.name == "One B" }.second?.solvedCount)
        assertEquals(30, merged.first { it.first.name == "Two A" }.second?.solvedCount)
    }

    @Test
    fun `problemset allows missing statistics and drops non-representable problems`() {
        val problems = listOf(
            CfProblemDto(contestId = 1, index = "A", name = "With stats"),
            CfProblemDto(contestId = 1, index = "B", name = "Without stats"),
            CfProblemDto(contestId = null, index = "A", name = "No contest"),
        )
        val merged = mergeProblemset(problems, emptyList())
        assertEquals(2, merged.size)
        assertNull(merged.first { it.first.name == "Without stats" }.second)
    }

    // --- Avatar normalization ---

    @Test
    fun `avatar urls are normalized to https`() {
        assertEquals("https://userpic.codeforces.org/x.png", normalizeAvatar("//userpic.codeforces.org/x.png"))
        assertEquals("https://a.b/c.png", normalizeAvatar("http://a.b/c.png"))
        assertEquals("https://a.b/c.png", normalizeAvatar("https://a.b/c.png"))
    }

    @Test
    fun `unsafe or empty avatar urls are rejected`() {
        assertNull(normalizeAvatar(null))
        assertNull(normalizeAvatar(""))
        assertNull(normalizeAvatar("file:///etc/passwd"))
        assertNull(normalizeAvatar("javascript:alert(1)"))
        assertNull(normalizeAvatar("data:image/png;base64,xxx"))
    }

    // --- Contest phase ---

    @Test
    fun `contest phases derive from real time`() {
        val now = 1_000_000L
        assertEquals(ContestPhase.UPCOMING, ContestPhase.of("BEFORE", now + 100, 100, now))
        assertEquals(ContestPhase.LIVE, ContestPhase.of("CODING", now - 50, 100, now))
        assertEquals(ContestPhase.ENDED, ContestPhase.of("FINISHED", now - 200, 100, now))
        // Boundary: exactly at start is LIVE; exactly at end is ENDED.
        assertEquals(ContestPhase.LIVE, ContestPhase.of("CODING", now, 100, now))
        assertEquals(ContestPhase.ENDED, ContestPhase.of("FINISHED", now - 100, 100, now))
    }

    @Test
    fun `unknown phases and missing start times degrade safely`() {
        assertEquals(ContestPhase.UPCOMING, ContestPhase.of("SOME_FUTURE_PHASE", 2_000_000L, 100, 1_000_000L))
        assertEquals(ContestPhase.ENDED, ContestPhase.of("BEFORE", null, 100, 1_000_000L))
    }
}
