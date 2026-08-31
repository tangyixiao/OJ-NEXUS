package com.ojnexus.feature.workspace

import com.ojnexus.judge.luogu.open.LuoguOpenEvaluation
import com.ojnexus.judge.luogu.open.LuoguOpenResult
import com.ojnexus.judge.luogu.open.LuoguOpenSubmission
import com.ojnexus.judge.luogu.open.LuoguOpenGateway
import com.ojnexus.judge.luogu.open.LuoguProblemJudgeRequest
import com.ojnexus.judge.luogu.open.LuoguRunRequest
import com.ojnexus.judge.luogu.open.LuoguSubmissionHistory
import com.ojnexus.judge.luogu.open.OpenAppCredential
import com.ojnexus.judge.luogu.open.OpenAppCredentialStore
import com.ojnexus.core.database.entity.SubmissionJobEntity
import com.ojnexus.judge.luogu.open.SubmissionJobKind
import com.ojnexus.judge.luogu.open.SubmissionJobStatus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkspaceViewModelTest {

    @Test
    fun `official gateway starts in submit mode when custom run is unavailable`() = runBlocking {
        val viewModel = WorkspaceViewModel(
            pid = "P1001",
            title = null,
            gateway = FakeGateway(customRunAvailable = false),
            credentialStore = FakeStore(),
            testScope = CoroutineScope(coroutineContext),
        )

        assertEquals(false, viewModel.state.value.customRunAvailable)
        assertEquals(WorkspaceMode.SUBMIT, viewModel.state.value.mode)
    }

    @Test
    fun `unsupported gateway does not restore a stale custom run job`() = runBlocking {
        val viewModel = WorkspaceViewModel(
            pid = "P1001",
            title = null,
            gateway = FakeGateway(customRunAvailable = false),
            credentialStore = FakeStore(),
            history = FakeHistory(
                SubmissionJobEntity(
                    judge = "luogu",
                    requestId = "run-old",
                    kind = SubmissionJobKind.RUN.name,
                    pid = null,
                    language = "cxx/14/gcc",
                    status = SubmissionJobStatus.PENDING.name,
                    createdAt = 1,
                    updatedAt = 2,
                ),
            ),
            testScope = CoroutineScope(coroutineContext),
        )

        assertEquals(WorkspaceMode.SUBMIT, viewModel.state.value.mode)
    }

    @Test
    fun `submit enters pending state and result check resolves it`() = runBlocking {
        val gateway = FakeGateway()
        val viewModel = WorkspaceViewModel("P1001", "A+B", gateway, FakeStore(), CoroutineScope(coroutineContext))

        viewModel.setCode("int main() {}")
        viewModel.setMode(WorkspaceMode.SUBMIT)
        viewModel.submit()

        assertEquals("req-1", viewModel.state.value.requestId)
        assertEquals(WorkspaceResultState.PENDING, viewModel.state.value.resultState)

        viewModel.checkResult()

        assertEquals(WorkspaceResultState.READY, viewModel.state.value.resultState)
        assertEquals(12, viewModel.state.value.evaluation?.status)
    }

    @Test
    fun `result check polls in the foreground until the judge is ready`() = runBlocking {
        val gateway = PollingGateway()
        val viewModel = WorkspaceViewModel(
            pid = "P1001",
            title = "A+B",
            gateway = gateway,
            credentialStore = FakeStore(),
            testScope = CoroutineScope(coroutineContext),
            delayForResult = {},
        )

        viewModel.setCode("int main() {}")
        viewModel.setMode(WorkspaceMode.SUBMIT)
        viewModel.submit()
        viewModel.checkResult()

        assertEquals(3, gateway.resultCalls)
        assertEquals(WorkspaceResultState.READY, viewModel.state.value.resultState)
        assertEquals(12, viewModel.state.value.evaluation?.status)
    }

    @Test
    fun `result check stops after the bounded foreground poll window`() = runBlocking {
        val gateway = PollingGateway(readyAfter = Int.MAX_VALUE)
        val viewModel = WorkspaceViewModel(
            pid = "P1001",
            title = "A+B",
            gateway = gateway,
            credentialStore = FakeStore(),
            testScope = CoroutineScope(coroutineContext),
            delayForResult = {},
        )

        viewModel.setCode("int main() {}")
        viewModel.setMode(WorkspaceMode.SUBMIT)
        viewModel.submit()
        viewModel.checkResult()

        assertEquals(8, gateway.resultCalls)
        assertEquals(WorkspaceResultState.PENDING, viewModel.state.value.resultState)
    }

    @Test
    fun `submit forwards the language selected in the editor`() = runBlocking {
        val gateway = FakeGateway()
        val viewModel = WorkspaceViewModel("P1001", "A+B", gateway, FakeStore(), CoroutineScope(coroutineContext))

        viewModel.setLanguage("python3/c")
        viewModel.setCode("print(1 + 2)")
        viewModel.setMode(WorkspaceMode.SUBMIT)
        viewModel.submit()

        assertEquals("python3/c", gateway.lastProblemRequest?.lang)
    }

    @Test
    fun `busy submit is not duplicated`() = runBlocking {
        val gateway = BusyGateway()
        val scope = CoroutineScope(Dispatchers.Default)
        val viewModel = WorkspaceViewModel("P1001", null, gateway, FakeStore(), scope)
        viewModel.setCode("int main() {}")
        viewModel.setMode(WorkspaceMode.SUBMIT)

        viewModel.submit()
        gateway.started.await()
        viewModel.submit()

        assertEquals(1, gateway.submitCount)
        gateway.release.complete(Unit)
        scope.cancel()
    }

    @Test
    fun `workspace restores the latest local request metadata`() = runBlocking {
        val scope = CoroutineScope(coroutineContext)
        val viewModel = WorkspaceViewModel(
            pid = "P1001",
            title = null,
            gateway = FakeGateway(),
            credentialStore = FakeStore(),
            history = FakeHistory(
                SubmissionJobEntity(
                    judge = "luogu",
                    requestId = "req-restored",
                    kind = SubmissionJobKind.PROBLEM.name,
                    pid = "P1001",
                    language = "cxx/14/gcc",
                    status = SubmissionJobStatus.PENDING.name,
                    createdAt = 1,
                    updatedAt = 2,
                ),
            ),
            testScope = scope,
        )

        assertEquals("req-restored", viewModel.state.value.requestId)
        assertEquals(WorkspaceResultState.PENDING, viewModel.state.value.resultState)
    }

    @Test
    fun `workspace restores ready submission mode and language`() = runBlocking {
        val viewModel = WorkspaceViewModel(
            pid = "P1001",
            title = null,
            gateway = FakeGateway(),
            credentialStore = FakeStore(),
            history = FakeHistory(
                SubmissionJobEntity(
                    judge = "luogu",
                    requestId = "req-ready",
                    kind = SubmissionJobKind.PROBLEM.name,
                    pid = "P1001",
                    language = "cxx/17/gcc",
                    status = SubmissionJobStatus.READY.name,
                    judgeStatus = 12,
                    score = 100,
                    createdAt = 1,
                    updatedAt = 2,
                ),
            ),
            testScope = CoroutineScope(coroutineContext),
        )

        assertEquals(WorkspaceMode.SUBMIT, viewModel.state.value.mode)
        assertEquals("cxx/17/gcc", viewModel.state.value.language)
        assertEquals(WorkspaceResultState.READY, viewModel.state.value.resultState)
        assertEquals(100, viewModel.state.value.evaluation?.score)
    }

    @Test
    fun `workspace exposes a failed restored request`() = runBlocking {
        val viewModel = WorkspaceViewModel(
            pid = "P1001",
            title = null,
            gateway = FakeGateway(),
            credentialStore = FakeStore(),
            history = FakeHistory(
                SubmissionJobEntity(
                    judge = "luogu",
                    requestId = "req-failed",
                    kind = SubmissionJobKind.PROBLEM.name,
                    pid = "P1001",
                    language = "cxx/14/gcc",
                    status = SubmissionJobStatus.FAILED.name,
                    lastErrorType = "Network",
                    createdAt = 1,
                    updatedAt = 2,
                ),
            ),
            testScope = CoroutineScope(coroutineContext),
        )

        assertEquals(WorkspaceResultState.IDLE, viewModel.state.value.resultState)
        assertEquals(WorkspaceError.PREVIOUS_REQUEST_FAILED, viewModel.state.value.error)
    }

    @Test
    fun `history restoration cannot overwrite a newer submission`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default)
        val history = BlockingHistory()
        val viewModel = WorkspaceViewModel(
            pid = "P1001",
            title = null,
            gateway = FakeGateway(),
            credentialStore = FakeStore(),
            history = history,
            testScope = scope,
        )
        history.started.await()

        viewModel.setMode(WorkspaceMode.SUBMIT)
        viewModel.submit()
        withTimeout(1_000) {
            while (viewModel.state.value.requestId != "req-1") delay(1)
        }

        history.release.complete(Unit)
        history.finished.await()
        scope.coroutineContext[Job]?.children?.toList().orEmpty().joinAll()
        assertEquals("req-1", viewModel.state.value.requestId)
        assertEquals(WorkspaceMode.SUBMIT, viewModel.state.value.mode)
        scope.cancel()
    }
}

private class FakeGateway(
    private val customRunAvailable: Boolean = true,
) : LuoguOpenGateway {
    var submitCount = 0
    var lastProblemRequest: LuoguProblemJudgeRequest? = null
    override val supportsCustomInputRun: Boolean = customRunAvailable
    override suspend fun submitProblem(request: LuoguProblemJudgeRequest): LuoguOpenSubmission {
        submitCount++
        lastProblemRequest = request
        return LuoguOpenSubmission("req-1")
    }

    override suspend fun run(request: LuoguRunRequest): LuoguOpenSubmission = LuoguOpenSubmission("run-1")

    override suspend fun fetchResult(requestId: String): LuoguOpenResult =
        LuoguOpenResult.Ready(
            LuoguOpenEvaluation(
                requestId = requestId,
                trackId = null,
                type = "judge",
                compileSuccess = true,
                compileMessage = null,
                status = 12,
                score = 100,
                timeMs = 1,
                memoryKiB = 2,
                output = null,
                exitCode = null,
            ),
        )
}

private class BusyGateway : LuoguOpenGateway {
    val started = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    var submitCount = 0

    override suspend fun submitProblem(request: LuoguProblemJudgeRequest): LuoguOpenSubmission {
        submitCount++
        started.complete(Unit)
        release.await()
        return LuoguOpenSubmission("req-1")
    }

    override suspend fun run(request: LuoguRunRequest): LuoguOpenSubmission = LuoguOpenSubmission("run-1")

    override suspend fun fetchResult(requestId: String): LuoguOpenResult = LuoguOpenResult.Pending
}

private class PollingGateway(
    private val readyAfter: Int = 3,
) : LuoguOpenGateway {
    var resultCalls = 0

    override suspend fun submitProblem(request: LuoguProblemJudgeRequest): LuoguOpenSubmission =
        LuoguOpenSubmission("req-poll")

    override suspend fun run(request: LuoguRunRequest): LuoguOpenSubmission =
        LuoguOpenSubmission("run-poll")

    override suspend fun fetchResult(requestId: String): LuoguOpenResult {
        resultCalls++
        return if (resultCalls < readyAfter) {
            LuoguOpenResult.Pending
        } else {
            LuoguOpenResult.Ready(
                LuoguOpenEvaluation(
                    requestId = requestId,
                    trackId = null,
                    type = "judge",
                    compileSuccess = true,
                    compileMessage = null,
                    status = 12,
                    score = 100,
                    timeMs = 1,
                    memoryKiB = 2,
                    output = null,
                    exitCode = null,
                ),
            )
        }
    }
}

private class FakeStore : OpenAppCredentialStore {
    override suspend fun read(): OpenAppCredential = OpenAppCredential("u", "s")
    override suspend fun write(value: OpenAppCredential) = Unit
    override suspend fun clear() = Unit
}

private class FakeHistory(
    private val job: SubmissionJobEntity,
) : LuoguSubmissionHistory {
    override suspend fun latestForProblem(pid: String): SubmissionJobEntity? = job.takeIf { it.pid == pid }
}

private class BlockingHistory : LuoguSubmissionHistory {
    val started = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    val finished = CompletableDeferred<Unit>()

    override suspend fun latestForProblem(pid: String): SubmissionJobEntity {
        started.complete(Unit)
        release.await()
        finished.complete(Unit)
        return SubmissionJobEntity(
            judge = "luogu",
            requestId = "req-old",
            kind = SubmissionJobKind.PROBLEM.name,
            pid = pid,
            language = "cxx/14/gcc",
            status = SubmissionJobStatus.PENDING.name,
            createdAt = 1,
            updatedAt = 1,
        )
    }
}
