package com.ojnexus.feature.submissions

import com.ojnexus.core.database.entity.SubmissionJobEntity
import com.ojnexus.core.model.JudgeId
import com.ojnexus.judge.luogu.open.SubmissionJobKind
import com.ojnexus.judge.luogu.open.SubmissionJobStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SubmissionControlTowerTest {
    private fun job(id: Long, status: String) = SubmissionJobEntity(
        id = id,
        judge = JudgeId.LUOGU.id,
        requestId = "request-$id",
        kind = SubmissionJobKind.RUN.name,
        pid = null,
        title = null,
        language = "cpp",
        status = status,
        judgeStatus = null,
        score = null,
        createdAt = id,
        updatedAt = id,
        lastErrorType = null,
        compileSuccess = null,
        compileMessage = null,
        output = null,
        exitCode = null,
        executionTimeMs = null,
        memoryKiB = null,
    )

    @Test
    fun summaryCountsKnownAndUnknownStatuses() {
        val summary = summarizeSubmissionCenter(
            listOf(
                job(1, SubmissionJobStatus.PENDING.name),
                job(2, SubmissionJobStatus.PENDING.name),
                job(3, SubmissionJobStatus.READY.name),
                job(4, SubmissionJobStatus.FAILED.name),
                job(5, "FUTURE_STATUS"),
            ),
        )

        assertEquals(
            SubmissionCenterSummary(total = 5, pending = 2, ready = 1, failed = 1, other = 1),
            summary,
        )
    }

    @Test
    fun summaryAcceptsEmptyJobs() {
        assertEquals(
            SubmissionCenterSummary(total = 0, pending = 0, ready = 0, failed = 0, other = 0),
            summarizeSubmissionCenter(emptyList()),
        )
    }

    @Test
    fun filtersPreserveSourceOrderAndKeepUnknownInAllOnly() {
        val pending = job(1, SubmissionJobStatus.PENDING.name)
        val failed = job(2, SubmissionJobStatus.FAILED.name)
        val unknown = job(3, "FUTURE_STATUS")
        val ready = job(4, SubmissionJobStatus.READY.name)
        val source = listOf(pending, failed, unknown, ready)

        assertEquals(source, filterSubmissionJobs(source, SubmissionStatusFilter.ALL))
        assertEquals(listOf(pending), filterSubmissionJobs(source, SubmissionStatusFilter.PENDING))
        assertEquals(listOf(ready), filterSubmissionJobs(source, SubmissionStatusFilter.READY))
        assertEquals(listOf(failed), filterSubmissionJobs(source, SubmissionStatusFilter.FAILED))
        assertTrue(filterSubmissionJobs(source, SubmissionStatusFilter.PENDING).none { it.status == "FUTURE_STATUS" })
        assertNotSame(source, filterSubmissionJobs(source, SubmissionStatusFilter.ALL))
        assertEquals(listOf(pending, failed, unknown, ready), source)
    }

    @Test
    fun pendingRequestIdsRemoveBlanksAndDuplicatesInSourceOrder() {
        val first = job(1, SubmissionJobStatus.PENDING.name).copy(requestId = "req-a")
        val duplicate = job(2, SubmissionJobStatus.PENDING.name).copy(requestId = " req-a ")
        val blank = job(3, SubmissionJobStatus.PENDING.name).copy(requestId = " ")
        val ready = job(4, SubmissionJobStatus.READY.name).copy(requestId = "req-ready")

        assertEquals(
            listOf("req-a"),
            pendingSubmissionRequestIds(listOf(first, duplicate, blank, ready)),
        )
    }

    @Test
    fun failedRequestIdsKeepOnlyFailedRowsInSourceOrder() {
        val failedA = job(1, SubmissionJobStatus.FAILED.name).copy(requestId = "req-a")
        val ready = job(2, SubmissionJobStatus.READY.name).copy(requestId = "req-ready")
        val failedB = job(3, SubmissionJobStatus.FAILED.name).copy(requestId = "req-b")

        assertEquals(
            listOf("req-a", "req-b"),
            failedSubmissionRequestIds(listOf(failedA, ready, failedB)),
        )
    }
}
