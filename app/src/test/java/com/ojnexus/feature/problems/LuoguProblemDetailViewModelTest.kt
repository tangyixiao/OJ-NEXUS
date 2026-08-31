package com.ojnexus.feature.problems

import com.ojnexus.core.ui.Loadable
import com.ojnexus.judge.luogu.LuoguProblemDetail
import com.ojnexus.judge.luogu.LuoguProblemDetailResult
import com.ojnexus.judge.luogu.LuoguProblemDetailSource
import com.ojnexus.judge.luogu.LuoguProblemDetailReader
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LuoguProblemDetailViewModelTest {
    @Test
    fun `initial load exposes cached source`() = runBlocking {
        val viewModel = LuoguProblemDetailViewModel(
            pid = "P1001",
            repository = FakeRepository(
                fetchResult = LuoguProblemDetailResult(detail("Cached"), LuoguProblemDetailSource.CACHE),
            ),
            testScope = CoroutineScope(coroutineContext),
        )

        val state = viewModel.state.value
        assertEquals("Cached", (state.content as Loadable.Ready).value.title)
        assertEquals(LuoguProblemDetailSource.CACHE, state.source)
        assertFalse(state.refreshing)
    }

    @Test
    fun `refresh exposes busy state then network source`() = runBlocking {
        val repository = FakeRepository(
            fetchResult = LuoguProblemDetailResult(detail("Cached"), LuoguProblemDetailSource.CACHE),
            refreshResult = LuoguProblemDetailResult(detail("Fresh"), LuoguProblemDetailSource.NETWORK),
        )
        val viewModel = LuoguProblemDetailViewModel(
            pid = "P1001",
            repository = repository,
            testScope = CoroutineScope(coroutineContext),
        )

        viewModel.refresh()

        val state = viewModel.state.value
        assertEquals("Fresh", (state.content as Loadable.Ready).value.title)
        assertEquals(LuoguProblemDetailSource.NETWORK, state.source)
        assertFalse(state.refreshing)
        assertFalse(state.refreshError)
        assertEquals(1, repository.refreshCalls)
    }

    @Test
    fun `refresh reports busy before a suspended repository call completes`() = runBlocking {
        val repository = BlockingRepository(
            fetchResult = LuoguProblemDetailResult(detail("Cached"), LuoguProblemDetailSource.CACHE),
            refreshResult = LuoguProblemDetailResult(detail("Fresh"), LuoguProblemDetailSource.NETWORK),
        )
        val viewModel = LuoguProblemDetailViewModel(
            pid = "P1001",
            repository = repository,
            testScope = CoroutineScope(coroutineContext),
        )

        viewModel.refresh()
        repository.started.await()
        assertTrue(viewModel.state.value.refreshing)

        repository.release.complete(Unit)
        yield()
        assertEquals("Fresh", (viewModel.state.value.content as Loadable.Ready).value.title)
        assertFalse(viewModel.state.value.refreshing)
    }

    @Test
    fun `cache fallback keeps detail visible and marks retryable refresh error`() = runBlocking {
        val repository = FakeRepository(
            fetchResult = LuoguProblemDetailResult(detail("Cached"), LuoguProblemDetailSource.CACHE),
            refreshResult = LuoguProblemDetailResult(detail("Cached"), LuoguProblemDetailSource.CACHE_FALLBACK),
        )
        val viewModel = LuoguProblemDetailViewModel(
            pid = "P1001",
            repository = repository,
            testScope = CoroutineScope(coroutineContext),
        )

        viewModel.refresh()

        val state = viewModel.state.value
        assertEquals("Cached", (state.content as Loadable.Ready).value.title)
        assertEquals(LuoguProblemDetailSource.CACHE_FALLBACK, state.source)
        assertTrue(state.refreshError)
    }

    private fun detail(title: String) = LuoguProblemDetail(
        pid = "P1001",
        title = title,
        difficulty = 1,
        tags = emptyList(),
        totalSubmit = null,
        totalAccepted = null,
        background = "",
        description = "",
        inputFormat = "",
        outputFormat = "",
        hint = "",
        samples = emptyList(),
        timeLimitMs = null,
        memoryLimitMb = null,
    )

    private class FakeRepository(
        private val fetchResult: LuoguProblemDetailResult,
        private val refreshResult: LuoguProblemDetailResult = fetchResult,
    ) : LuoguProblemDetailReader {
        var refreshCalls = 0
        override suspend fun fetch(pid: String): LuoguProblemDetailResult = fetchResult
        override suspend fun refresh(pid: String): LuoguProblemDetailResult {
            refreshCalls++
            return refreshResult
        }
    }

    private class BlockingRepository(
        private val fetchResult: LuoguProblemDetailResult,
        private val refreshResult: LuoguProblemDetailResult,
    ) : LuoguProblemDetailReader {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        override suspend fun fetch(pid: String): LuoguProblemDetailResult = fetchResult

        override suspend fun refresh(pid: String): LuoguProblemDetailResult {
            started.complete(Unit)
            release.await()
            return refreshResult
        }
    }
}
