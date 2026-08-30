package com.ojnexus.feature.submissions

import com.ojnexus.core.database.entity.SubmissionJobEntity
import com.ojnexus.core.ui.Loadable
import com.ojnexus.judge.luogu.open.LuoguOpenApiError
import com.ojnexus.judge.luogu.open.LuoguOpenEvaluation
import com.ojnexus.judge.luogu.open.LuoguOpenResult
import com.ojnexus.judge.luogu.open.LuoguSubmissionCenter
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
    fun `action errors clear on retry while cached rows remain available`() = runBlocking {
        val jobs = MutableStateFlow(listOf(readyJob(requestId = "req-retry")))
        val center = FakeSubmissionCenter(
            jobs = jobs,
            refreshResults = ArrayDeque(
                listOf(
                    RefreshOutcome.Throw(LuoguOpenApiError.Network(IOException("offline"))),
                    RefreshOutcome.Return(
                        LuoguOpenResult.Ready(
                            LuoguOpenEvaluation(
                                requestId = "req-retry",
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
                        ),
                    ),
                ),
            ),
        )
        val viewModel = SubmissionCenterViewModel(center)
        val collector = collectState(viewModel)
        awaitReady(viewModel)

        viewModel.checkResult("req-retry")
        awaitNotBusyRequest(viewModel, "req-retry")
        val failed = awaitReady(viewModel)
        assertEquals(listOf("req-retry"), failed.jobs.map { it.requestId })
        assertEquals(
            SubmissionCenterActionError.Generic("Open Platform network error"),
            failed.actionError,
        )

        viewModel.checkResult("req-retry")
        awaitNotBusyRequest(viewModel, "req-retry")
        val recovered = awaitReady(viewModel)
        assertEquals(listOf("req-retry"), recovered.jobs.map { it.requestId })
        assertNull(recovered.actionError)
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
        }
    }

    override suspend fun latestForProblem(pid: String): SubmissionJobEntity? = null
}

private sealed interface RefreshOutcome {
    data class Return(val result: LuoguOpenResult) : RefreshOutcome
    data class Throw(val error: Throwable) : RefreshOutcome
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
