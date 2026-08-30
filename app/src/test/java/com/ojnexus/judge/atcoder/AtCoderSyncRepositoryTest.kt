package com.ojnexus.judge.atcoder

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ojnexus.core.database.OjNexusDatabase
import com.ojnexus.core.database.entity.JudgeAccountEntity
import com.ojnexus.core.model.DifficultySource
import com.ojnexus.core.model.JudgeId
import com.ojnexus.core.model.Verdict
import com.ojnexus.judge.atcoder.api.dto.AtCoderContestDto
import com.ojnexus.judge.atcoder.api.dto.AtCoderMergedProblemDto
import com.ojnexus.judge.atcoder.api.dto.AtCoderProblemModelDto
import com.ojnexus.judge.atcoder.api.dto.AtCoderSubmissionDto
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class FakeAtCoderAdapter : AtCoderAdapter {
    val pages = ArrayDeque<List<AtCoderSubmissionDto>>()
    val requestedFrom = mutableListOf<Long>()
    var contests = emptyList<AtCoderContestDto>()
    var problems = emptyList<AtCoderMergedProblemDto>()
    var models = emptyMap<String, AtCoderProblemModelDto>()
    var failProblems = false

    override suspend fun fetchSubmissions(handle: String, fromSecond: Long): List<AtCoderSubmissionDto> {
        requestedFrom += fromSecond
        return pages.removeFirstOrNull().orEmpty()
    }

    override suspend fun fetchContests() = contests
    override suspend fun fetchMergedProblems(): List<AtCoderMergedProblemDto> {
        if (failProblems) throw AtCoderApiError.ServerError(500)
        return problems
    }
    override suspend fun fetchProblemModels() = models
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AtCoderSyncRepositoryTest {
    private lateinit var database: OjNexusDatabase
    private lateinit var adapter: FakeAtCoderAdapter
    private lateinit var repository: AtCoderSyncRepository
    private val clock = Clock.fixed(Instant.ofEpochMilli(2_000_000), ZoneId.of("UTC"))
    private val account = JudgeAccountEntity(
        id = 4,
        judge = JudgeId.ATCODER.id,
        handle = "CaseUser",
        canonicalHandle = "CaseUser",
        connectedAt = 1,
        updatedAt = 1,
        verificationState = "UNVERIFIED",
        sourceReliability = "COMMUNITY",
    )

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            OjNexusDatabase::class.java,
        ).allowMainThreadQueries().build()
        adapter = FakeAtCoderAdapter()
        repository = AtCoderSyncRepository(database, adapter, clock, ZoneId.of("UTC"))
        val id = database.judgeAccountDao().insert(account.copy(id = 0))
        database.syncStateDao().upsert(
            com.ojnexus.core.database.entity.SyncStateEntity(judge = JudgeId.ATCODER.id, accountId = id),
        )
    }

    @After
    fun tearDown() = database.close()

    private fun submission(
        id: Long,
        second: Long,
        result: String = "AC",
        problemId: String = "abc350_a",
    ) = AtCoderSubmissionDto(
        executionTime = 12,
        point = if (result == "AC") 100.0 else 0.0,
        result = result,
        problemId = problemId,
        userId = "CaseUser",
        epochSecond = second,
        contestId = "abc350",
        id = id,
        language = "C++ 23",
        length = 512,
    )

    @Test
    fun `initial sync repeats boundary second and remains idempotent`() = runBlocking {
        adapter.pages += (1L..500L).map { submission(it, it) }
        adapter.pages += listOf(submission(500, 500), submission(501, 501))

        val outcome = repository.syncSubmissions(activeAccount(), force = true)

        assertTrue(outcome.ok)
        assertEquals(listOf(0L, 500L), adapter.requestedFrom)
        assertEquals(501L, database.syncStateDao().findByJudge("atcoder")!!.latestSubmissionTimeSeconds)
        assertEquals(501, database.problemDao().findByKey("atcoder", "abc350_a")!!.attemptCount)
        val attempt = database.attemptDao().findByExternalId("atcoder", "501")!!
        assertEquals("abc350", attempt.contestId)
        assertEquals(100.0, attempt.score)
        assertEquals(512, attempt.codeLengthBytes)
    }

    @Test
    fun `full same-second saturation terminates partial without skipping second`() = runBlocking {
        val saturated = (1L..500L).map { submission(it, 100) }
        adapter.pages += saturated
        adapter.pages += saturated

        val outcome = repository.syncSubmissions(activeAccount(), force = true)

        assertFalse(outcome.ok)
        assertEquals("PaginationStalled", outcome.errorType)
        assertEquals(listOf(0L, 100L), adapter.requestedFrom)
        assertEquals(100L, database.syncStateDao().findByJudge("atcoder")!!.latestSubmissionTimeSeconds)
        assertEquals(500, database.problemDao().findByKey("atcoder", "abc350_a")!!.attemptCount)
    }

    @Test
    fun `incremental sync overlaps 120 seconds and refreshes rejudge`() = runBlocking {
        adapter.pages += listOf(submission(1, 1_000, "WJ"))
        repository.syncSubmissions(activeAccount(), force = true)
        adapter.requestedFrom.clear()
        adapter.pages += listOf(submission(1, 1_000, "AC"), submission(2, 1_001, "WA"))

        repository.syncSubmissions(activeAccount(), force = true)

        assertEquals(listOf(880L), adapter.requestedFrom)
        assertEquals(Verdict.AC.name, database.attemptDao().findByExternalId("atcoder", "1")!!.verdict)
        assertEquals(2, database.problemDao().findByKey("atcoder", "abc350_a")!!.attemptCount)
    }

    @Test
    fun `submission materializes minimal problem when catalog is absent`() = runBlocking {
        adapter.pages += listOf(submission(7, 100, problemId = "abc999_z"))

        repository.syncSubmissions(activeAccount(), force = true)

        val problem = database.problemDao().findByKey("atcoder", "abc999_z")!!
        assertEquals("abc999_z", problem.title)
        assertNull(problem.difficulty)
        assertEquals(DifficultySource.UNKNOWN.name, problem.difficultySource)
        assertEquals("https://atcoder.jp/contests/abc350/tasks/abc999_z", problem.sourceUrl)
    }

    @Test
    fun `catalog upserts remote rows without polluting local library`() = runBlocking {
        adapter.contests = listOf(AtCoderContestDto(1_000, "0-1999", "abc350", 7_200, "ABC 350"))
        adapter.problems = listOf(AtCoderMergedProblemDto("abc350_a", "abc350", "A", "Past ABCs", 10, 100.0))
        adapter.models = mapOf("abc350_a" to AtCoderProblemModelDto(difficulty = 799.8))

        val outcome = repository.syncProblems(activeAccount(), force = true)

        assertTrue(outcome.ok)
        assertEquals(1, database.remoteProblemDao().countByJudge("atcoder"))
        assertNull(database.problemDao().findByKey("atcoder", "abc350_a"))
        val remote = database.remoteProblemDao().findByKey("atcoder", "abc350_a")!!
        assertEquals(DifficultySource.ESTIMATED.name, remote.difficultySource)
    }

    @Test
    fun `later catalog sync enriches a problem materialized by submission`() = runBlocking {
        adapter.pages += listOf(submission(1, 100))
        repository.syncSubmissions(activeAccount(), force = true)
        adapter.contests = listOf(AtCoderContestDto(1_000, "0-1999", "abc350", 7_200, "ABC 350"))
        adapter.problems = listOf(AtCoderMergedProblemDto("abc350_a", "abc350", "A", "Past ABCs", 10, 100.0))
        adapter.models = mapOf("abc350_a" to AtCoderProblemModelDto(difficulty = 799.8))

        repository.syncProblems(activeAccount(), force = true)

        val local = database.problemDao().findByKey("atcoder", "abc350_a")!!
        assertEquals("Past ABCs", local.title)
        assertEquals(800, local.difficulty)
        assertEquals(DifficultySource.ESTIMATED.name, local.difficultySource)
    }

    @Test
    fun `failed catalog refresh retains previous cache`() = runBlocking {
        database.remoteProblemDao().upsertAll(
            listOf(
                com.ojnexus.core.database.entity.RemoteProblemEntity(
                    judge = "atcoder",
                    externalId = "keep_me",
                    name = "Keep Me",
                    updatedAt = 1,
                ),
            ),
        )
        adapter.failProblems = true

        val outcome = repository.syncProblems(activeAccount(), force = true)

        assertFalse(outcome.ok)
        assertNotNull(database.remoteProblemDao().findByKey("atcoder", "keep_me"))
    }

    private suspend fun activeAccount(): JudgeAccountEntity =
        database.judgeAccountDao().findActiveByJudge("atcoder")!!
}
