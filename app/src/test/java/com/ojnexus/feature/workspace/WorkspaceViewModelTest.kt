package com.ojnexus.feature.workspace

import com.ojnexus.judge.luogu.open.LuoguOpenEvaluation
import com.ojnexus.judge.luogu.open.LuoguOpenResult
import com.ojnexus.judge.luogu.open.LuoguOpenSubmission
import com.ojnexus.judge.luogu.open.LuoguOpenGateway
import com.ojnexus.judge.luogu.open.LuoguProblemJudgeRequest
import com.ojnexus.judge.luogu.open.LuoguRunRequest
import com.ojnexus.judge.luogu.open.OpenAppCredential
import com.ojnexus.judge.luogu.open.OpenAppCredentialStore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkspaceViewModelTest {

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
}

private class FakeGateway : LuoguOpenGateway {
    var submitCount = 0
    override suspend fun submitProblem(request: LuoguProblemJudgeRequest): LuoguOpenSubmission {
        submitCount++
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

private class FakeStore : OpenAppCredentialStore {
    override suspend fun read(): OpenAppCredential = OpenAppCredential("u", "s")
    override suspend fun write(value: OpenAppCredential) = Unit
    override suspend fun clear() = Unit
}
