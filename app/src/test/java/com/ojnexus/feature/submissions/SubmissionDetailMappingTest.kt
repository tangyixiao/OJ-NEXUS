package com.ojnexus.feature.submissions

import com.ojnexus.core.database.entity.SubmissionJobEntity
import com.ojnexus.core.model.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubmissionDetailMappingTest {

    private fun job(
        judgeStatus: Int? = 12,
        score: Int? = 100,
        pid: String? = "P1001",
        title: String? = "A+B Problem",
        kind: String = com.ojnexus.judge.luogu.open.SubmissionJobKind.PROBLEM.name,
        compileSuccess: Boolean? = true,
        compileMessage: String? = null,
        output: String? = null,
        exitCode: Int? = null,
        executionTimeMs: Int? = 15,
        memoryKiB: Int? = 2048,
        lastErrorType: String? = null,
        judge: String = "luogu",
    ) = SubmissionJobEntity(
        id = 1L,
        judge = judge,
        requestId = "req-1",
        kind = kind,
        pid = pid,
        title = title,
        language = "C++14",
        status = "READY",
        judgeStatus = judgeStatus,
        score = score,
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_100_000L,
        compileSuccess = compileSuccess,
        compileMessage = compileMessage,
        output = output,
        exitCode = exitCode,
        executionTimeMs = executionTimeMs,
        memoryKiB = memoryKiB,
        lastErrorType = lastErrorType,
    )

    @Test
    fun `ready luogu job maps to unified model with verdict and metrics`() {
        val detail = job().toSubmissionDetailModel()

        assertEquals("luogu", detail.judgeId)
        assertEquals(Verdict.AC, detail.verdict)
        assertEquals("STATUS 12", detail.rawVerdict)
        assertEquals("C++14", detail.language)
        assertEquals(1_700_000_000_000L, detail.timestampEpochMs)
        assertEquals(15L, detail.executionTimeMs)
        assertEquals(2048L, detail.memoryKiB)
        assertEquals(100, detail.score)
        assertEquals("P1001", detail.problemId)
        assertEquals("A+B Problem", detail.problemTitle)
        assertEquals("https://www.luogu.com.cn/problem/P1001", detail.problemUrl)
        assertNull(detail.submissionUrl)
    }

    @Test
    fun `terminal judge statuses map to unified verdicts`() {
        assertEquals(Verdict.WA, job(judgeStatus = 6).toSubmissionDetailModel().verdict)
        assertEquals(Verdict.TLE, job(judgeStatus = 5).toSubmissionDetailModel().verdict)
        assertEquals(Verdict.CE, job(judgeStatus = 2).toSubmissionDetailModel().verdict)
        assertEquals(Verdict.MLE, job(judgeStatus = 4).toSubmissionDetailModel().verdict)
        assertEquals(Verdict.RE, job(judgeStatus = 7).toSubmissionDetailModel().verdict)
    }

    @Test
    fun `pending job has no verdict yet`() {
        val detail = job(judgeStatus = null, score = null).toSubmissionDetailModel()
        assertNull(detail.verdict)
    }

    @Test
    fun `extra rows capture compile output and error details`() {
        val detail = job(
            compileSuccess = false,
            compileMessage = "error: expected ';' before 'return'",
            output = "partial stdout",
            exitCode = 1,
            lastErrorType = "RUN_FAILED",
        ).toSubmissionDetailModel()

        val labels = detail.extraRows.map { it.label }
        assertTrue("COMPILE" in labels)
        assertTrue("COMPILE MESSAGE" in labels)
        assertTrue("OUTPUT" in labels)
        assertTrue("EXIT CODE" in labels)
        assertTrue("ERROR" in labels)
        assertEquals("FAILED", detail.extraRows.first { it.label == "COMPILE" }.value)
    }

    @Test
    fun `run kind job is tagged and pid link omitted when pid blank`() {
        val detail = job(
            kind = com.ojnexus.judge.luogu.open.SubmissionJobKind.RUN.name,
            pid = null,
            title = null,
        ).toSubmissionDetailModel()

        assertTrue(detail.extraRows.any { it.label == "KIND" && it.value == "CUSTOM RUN" })
        assertNull(detail.problemUrl)
        assertNull(detail.problemId)
    }

    @Test
    fun `sheet visibility follows terminal statuses only`() {
        assertTrue(submissionDetailSheetVisible("READY"))
        assertTrue(submissionDetailSheetVisible("FAILED"))
        assertEquals(false, submissionDetailSheetVisible("PENDING"))
    }
}
