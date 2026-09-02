package com.ojnexus.feature.submissions

import com.ojnexus.core.database.entity.SubmissionJobEntity
import com.ojnexus.core.ui.Loadable
import com.ojnexus.judge.luogu.open.LuoguOpenApiError
import com.ojnexus.judge.luogu.open.LuoguOpenEvaluation
import com.ojnexus.judge.luogu.open.LuoguOpenResult
import com.ojnexus.judge.luogu.open.LuoguSubmissionCenter
import com.ojnexus.judge.luogu.open.LuoguResultWorkScheduler
import com.ojnexus.judge.luogu.open.SubmissionJobStatus
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.coroutines.coroutineContext

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SubmissionCenterViewModelTest {

    @Test
    fun `queue recovery records acknowledgement and sends only the request id`() = runBlocking {
        val jobs = MutableStateFlow(listOf(pendingJob("req-queue")))
        val scheduler = RecordingScheduler()
        val viewModel = SubmissionCenterViewModel(FakeSubmissionCenter(jobs), scheduler = scheduler)
        val collector = collectState(viewModel)
        awaitReady(viewModel)

        viewModel.queueRecovery("req-queue")

        withTimeout(1_000) {
            while (scheduler.requestIds.isEmpty()) {
                drainMainLooper()
                delay(1)
            }
        }
        val state = awaitReady(viewModel)
        assertEquals(listOf("req-queue"), scheduler.requestIds)
        assertEquals(setOf("req-queue"), state.queuedRequestIds)
        collector.cancel()
    }

    @Test
    fun `queue recovery keeps cached rows and exposes scheduler failure`() = runBlocking {
        val jobs = MutableStateFlow(listOf(pendingJob("req-queue-error")))
        val scheduler = RecordingScheduler(error = IllegalStateException("work unavailable"))
        val viewModel = SubmissionCenterViewModel(FakeSubmissionCenter(jobs), scheduler = scheduler)
        val collector = collectState(viewModel)
        awaitReady(viewModel)

        viewModel.queueRecovery("req-queue-error")

        withTimeout(1_000) {
            while (awaitReady(viewModel).actionError == null) {
                drainMainLooper()
                delay(1)
            }
        }
        val state = awaitReady(viewModel)
        assertEquals(listOf("req-queue-error"), state.jobs.map { it.requestId })
        assertEquals(null, state.queuedRequestIds.singleOrNull())
        assertEquals("req-queue-error", state.actionError?.requestId)
        collector.cancel()
    }

    @Test
    fun `state exposes ready rows from the local submission center`() = runBlocking {
        val jobs = MutableStateFlow(listOf(readyJob(requestId = "req-1")))
        val viewModel = SubmissionCenterViewModel(FakeSubmissionCenter(jobs))
        val collector = collectState(viewModel)

        val state = awaitReady(viewModel)

        assertEquals(listOf("req-1"), state.jobs.map { it.requestId })
        assertEquals(emptySet<String>(), state.busyRequestIds)
        assertNull(state.actionError)
        collector.cancel()
    }

    @Test
    fun `state keeps an empty ready list when no local rows exist`() = runBlocking {
        val jobs = MutableStateFlow(emptyList<SubmissionJobEntity>())
        val viewModel = SubmissionCenterViewModel(FakeSubmissionCenter(jobs))
        val collector = collectState(viewModel)

        val state = awaitReady(viewModel)

        assertEquals(emptyList<SubmissionJobEntity>(), state.jobs)
        assertEquals(emptySet<String>(), state.busyRequestIds)
        assertNull(state.actionError)
        collector.cancel()
    }

    @Test
    fun `state exposes recovery availability and queueFailed uses failed snapshot`() = runBlocking {
        val jobs = MutableStateFlow(
            listOf(
                pendingJob("req-pending"),
                pendingJob("req-failed").copy(status = SubmissionJobStatus.FAILED.name),
                pendingJob("req-failed-2").copy(status = SubmissionJobStatus.FAILED.name),
            ),
        )
        val scheduler = RecordingScheduler()
        val viewModel = SubmissionCenterViewModel(FakeSubmissionCenter(jobs), scheduler = scheduler)
        val collector = collectState(viewModel)
        awaitReady(viewModel)

        assertEquals(true, awaitReady(viewModel).recoveryAvailable)
        viewModel.queueFailed()

        withTimeout(1_000) {
            while (scheduler.requestIds.size < 2) {
                drainMainLooper()
                delay(1)
            }
        }
        assertEquals(listOf("req-failed", "req-failed-2"), scheduler.requestIds)
        collector.cancel()
    }

    @Test
    fun `checkPending checks each pending request once`() = runBlocking {
        val jobs = MutableStateFlow(
            listOf(
                pendingJob("req-a"),
                pendingJob("req-b"),
                readyJob(requestId = "req-ready"),
            ),
        )
        val center = FakeSubmissionCenter(
            jobs = jobs,
            refreshResults = ArrayDeque(
                listOf(
                    RefreshOutcome.Return(readyResult("req-a")),
                    RefreshOutcome.Return(readyResult("req-b")),
                ),
            ),
        )
        val viewModel = SubmissionCenterViewModel(center, delayForResult = {})
        val collector = collectState(viewModel)
        awaitReady(viewModel)

        viewModel.checkPending()

        withTimeout(1_000) {
            while (center.refreshCalls.size < 2) {
                drainMainLooper()
                delay(1)
            }
        }
        assertEquals(listOf("req-a", "req-b"), center.refreshCalls)
        collector.cancel()
    }

    @Test
    fun `checkResult suppresses duplicate refresh calls while the same request is busy`() = runBlocking {
        val gate = BlockingRefresh()
        val jobs = MutableStateFlow(listOf(readyJob(requestId = "req-busy")))
        val center = FakeSubmissionCenter(jobs, refresh = gate::refresh)
        val viewModel = SubmissionCenterViewModel(center)
        val collector = collectState(viewModel)
        awaitReady(viewModel)

        viewModel.checkResult("req-busy")
        gate.started.await()
        awaitBusyRequest(viewModel, "req-busy")

        viewModel.checkResult("req-busy")

        assertEquals(listOf("req-busy"), center.refreshCalls)
        gate.release.complete(Unit)
        awaitNotBusyRequest(viewModel, "req-busy")
        collector.cancel()
    }

    @Test
    fun `checkResult polls a pending request until it is ready`() = runBlocking {
        val jobs = MutableStateFlow(listOf(readyJob(requestId = "req-poll")))
        val center = FakeSubmissionCenter(
            jobs = jobs,
            refreshResults = ArrayDeque(
                listOf(
                    RefreshOutcome.Return(LuoguOpenResult.Pending),
                    RefreshOutcome.Return(LuoguOpenResult.Pending),
                    RefreshOutcome.Return(readyResult("req-poll")),
                ),
            ),
        )
        val viewModel = SubmissionCenterViewModel(center, delayForResult = {})
        val collector = collectState(viewModel)
        awaitReady(viewModel)

        viewModel.checkResult("req-poll")
        awaitNotBusyRequest(viewModel, "req-poll")

        assertEquals(listOf("req-poll", "req-poll", "req-poll"), center.refreshCalls)
        assertNull(awaitReady(viewModel).actionError)
        collector.cancel()
    }

    @Test
    fun `action errors clear on retry while cached rows remain available`() = runBlocking {
        val jobs = MutableStateFlow(listOf(readyJob(requestId = "req-retry")))
        val retryGate = BlockingRefresh()
        val center = FakeSubmissionCenter(
            jobs = jobs,
            refresh = SequentialRefresh(
                listOf(
                    RefreshOutcome.Throw(LuoguOpenApiError.Network(IOException("offline"))),
                    RefreshOutcome.Suspend { requestId -> retryGate.refresh(requestId) },
                ),
            )::refresh,
        )
        val viewModel = SubmissionCenterViewModel(center)
        val collector = collectState(viewModel)
        awaitReady(viewModel)

        viewModel.checkResult("req-retry")
        awaitNotBusyRequest(viewModel, "req-retry")
        val failed = awaitReady(viewModel)
        assertEquals(listOf("req-retry"), failed.jobs.map { it.requestId })
        assertEquals(
            SubmissionCenterActionError.Generic(
                requestId = "req-retry",
                message = "Open Platform network error",
            ),
            failed.actionError,
        )

        viewModel.checkResult("req-retry")
        retryGate.started.await()
        awaitBusyRequest(viewModel, "req-retry")
        val retrying = awaitReady(viewModel)
        assertEquals(
            SubmissionCenterActionError.Generic(
                requestId = "req-retry",
                message = "Open Platform network error",
            ),
            retrying.actionError,
        )
        retryGate.release.complete(Unit)
        awaitNotBusyRequest(viewModel, "req-retry")
        val recovered = awaitReady(viewModel)
        assertEquals(listOf("req-retry"), recovered.jobs.map { it.requestId })
        assertNull(recovered.actionError)
        collector.cancel()
    }

    @Test
    fun `failed retry keeps the same request error visible`() = runBlocking {
        val jobs = MutableStateFlow(listOf(readyJob(requestId = "req-fail")))
        val retryGate = BlockingFailureRefresh(LuoguOpenApiError.Network(IOException("offline-again")))
        val center = FakeSubmissionCenter(
            jobs = jobs,
            refresh = SequentialRefresh(
                listOf(
                    RefreshOutcome.Throw(LuoguOpenApiError.Network(IOException("offline"))),
                    RefreshOutcome.Suspend { retryGate.refresh(it) },
                ),
            )::refresh,
        )
        val viewModel = SubmissionCenterViewModel(center)
        val collector = collectState(viewModel)
        awaitReady(viewModel)

        viewModel.checkResult("req-fail")
        awaitNotBusyRequest(viewModel, "req-fail")
        assertEquals(
            SubmissionCenterActionError.Generic(
                requestId = "req-fail",
                message = "Open Platform network error",
            ),
            awaitReady(viewModel).actionError,
        )

        viewModel.checkResult("req-fail")
        retryGate.started.await()
        awaitBusyRequest(viewModel, "req-fail")
        assertEquals(
            SubmissionCenterActionError.Generic(
                requestId = "req-fail",
                message = "Open Platform network error",
            ),
            awaitReady(viewModel).actionError,
        )
        retryGate.release.complete(Unit)
        awaitNotBusyRequest(viewModel, "req-fail")
        assertEquals(
            SubmissionCenterActionError.Generic(
                requestId = "req-fail",
                message = "Open Platform network error",
            ),
            awaitReady(viewModel).actionError,
        )
        collector.cancel()
    }

    @Test
    fun `success for another request does not clear an existing action error`() = runBlocking {
        val jobs = MutableStateFlow(
            listOf(
                readyJob(requestId = "req-a"),
                readyJob(requestId = "req-b"),
            ),
        )
        val gate = BlockingRefresh()
        val center = FakeSubmissionCenter(
            jobs = jobs,
            refresh = { requestId ->
                when (requestId) {
                    "req-a" -> throw LuoguOpenApiError.Network(IOException("offline"))
                    "req-b" -> gate.refresh(requestId)
                    else -> error("unexpected requestId: $requestId")
                }
            },
        )
        val viewModel = SubmissionCenterViewModel(center)
        val collector = collectState(viewModel)
        awaitReady(viewModel)

        viewModel.checkResult("req-a")
        awaitNotBusyRequest(viewModel, "req-a")
        assertEquals(
            SubmissionCenterActionError.Generic(
                requestId = "req-a",
                message = "Open Platform network error",
            ),
            awaitReady(viewModel).actionError,
        )

        viewModel.checkResult("req-b")
        gate.started.await()
        awaitBusyRequest(viewModel, "req-b")
        assertEquals(
            SubmissionCenterActionError.Generic(
                requestId = "req-a",
                message = "Open Platform network error",
            ),
            awaitReady(viewModel).actionError,
        )

        gate.release.complete(Unit)
        awaitNotBusyRequest(viewModel, "req-b")
        assertEquals(
            SubmissionCenterActionError.Generic(
                requestId = "req-a",
                message = "Open Platform network error",
            ),
            awaitReady(viewModel).actionError,
        )
        collector.cancel()
    }

    private suspend fun collectState(viewModel: SubmissionCenterViewModel): Job =
        kotlinx.coroutines.CoroutineScope(coroutineContext).launch {
            viewModel.state.collect()
        }

    private suspend fun awaitReady(viewModel: SubmissionCenterViewModel): SubmissionCenterUiState =
        withTimeout(1_000) {
            while (true) {
                drainMainLooper()
                val state = viewModel.state.value
                if (state is Loadable.Ready<*>) {
                    return@withTimeout state.value as SubmissionCenterUiState
                }
                delay(1)
            }
            error("unreachable")
        }

    private suspend fun awaitBusyRequest(viewModel: SubmissionCenterViewModel, requestId: String) {
        withTimeout(1_000) {
            while (true) {
                drainMainLooper()
                if (requestId in awaitReady(viewModel).busyRequestIds) {
                    return@withTimeout
                }
                delay(1)
            }
        }
    }

    private suspend fun awaitNotBusyRequest(viewModel: SubmissionCenterViewModel, requestId: String) {
        withTimeout(1_000) {
            while (true) {
                drainMainLooper()
                if (requestId !in awaitReady(viewModel).busyRequestIds) {
                    return@withTimeout
                }
                delay(1)
            }
        }
    }

    private fun drainMainLooper() {
        shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    private fun readyJob(requestId: String) = SubmissionJobEntity(
        judge = "luogu",
        requestId = requestId,
        kind = "PROBLEM",
        pid = "P1001",
        language = "cxx/14/gcc",
        status = "READY",
        judgeStatus = 12,
        score = 100,
        createdAt = 1,
        updatedAt = 2,
    )

    private fun pendingJob(requestId: String) = readyJob(requestId).copy(
        status = "PENDING",
        score = null,
        judgeStatus = null,
    )

    private fun readyResult(requestId: String) = LuoguOpenResult.Ready(
        LuoguOpenEvaluation(
            requestId = requestId,
            trackId = null,
            type = "judge",
            compileSuccess = true,
            compileMessage = null,
            status = 12,
            score = 100,
            timeMs = 1,
            memoryKiB = 1,
            output = null,
            exitCode = null,
        ),
    )
}

private class FakeSubmissionCenter(
    private val jobs: MutableStateFlow<List<SubmissionJobEntity>>,
    private val refresh: (suspend (String) -> LuoguOpenResult)? = null,
    val refreshResults: ArrayDeque<RefreshOutcome> = ArrayDeque(),
) : LuoguSubmissionCenter {
    val refreshCalls = mutableListOf<String>()
    var observedLimit: Int? = null

    override fun observeRecentJobs(limit: Int) = jobs.also { observedLimit = limit }

    override suspend fun refreshResult(requestId: String): LuoguOpenResult {
        refreshCalls += requestId
        refresh?.let { return it(requestId) }
        return when (val outcome = refreshResults.removeFirst()) {
            is RefreshOutcome.Return -> outcome.result
            is RefreshOutcome.Throw -> throw outcome.error
            is RefreshOutcome.Suspend -> outcome.block(requestId)
        }
    }

    override suspend fun latestForProblem(pid: String): SubmissionJobEntity? = null
}

private class RecordingScheduler(
    private val error: Throwable? = null,
) : LuoguResultWorkScheduler {
    val requestIds = mutableListOf<String>()

    override fun enqueue(requestId: String) = Unit

    override fun enqueueNow(requestId: String) {
        error?.let { throw it }
        requestIds += requestId
    }
}

private sealed interface RefreshOutcome {
    data class Return(val result: LuoguOpenResult) : RefreshOutcome
    data class Throw(val error: Throwable) : RefreshOutcome
    data class Suspend(val block: suspend (String) -> LuoguOpenResult) : RefreshOutcome
}

private class BlockingRefresh {
    val started = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()

    suspend fun refresh(requestId: String): LuoguOpenResult {
        started.complete(Unit)
        release.await()
        return LuoguOpenResult.Ready(
            LuoguOpenEvaluation(
                requestId = requestId,
                trackId = null,
                type = "judge",
                compileSuccess = true,
                compileMessage = null,
                status = 12,
                score = 100,
                timeMs = 1,
                memoryKiB = 1,
                output = null,
                exitCode = null,
            ),
        )
    }
}

private class BlockingFailureRefresh(
    private val error: Throwable,
) {
    val started = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()

    suspend fun refresh(requestId: String): LuoguOpenResult {
        started.complete(Unit)
        release.await()
        throw error
    }
}

private class SequentialRefresh(
    outcomes: List<RefreshOutcome>,
) {
    private val outcomes = ArrayDeque(outcomes)

    suspend fun refresh(requestId: String): LuoguOpenResult =
        when (val outcome = outcomes.removeFirst()) {
            is RefreshOutcome.Return -> outcome.result
            is RefreshOutcome.Throw -> throw outcome.error
            is RefreshOutcome.Suspend -> outcome.block(requestId)
        }
}
