package com.ojnexus.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ojnexus.core.database.OjNexusDatabase
import com.ojnexus.core.database.entity.RemoteProblemEntity
import com.ojnexus.core.database.entity.SyncStateEntity
import com.ojnexus.core.model.JudgeId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JudgeDataRepositoryTest {

    private lateinit var database: OjNexusDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            OjNexusDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `a missing Luogu cache query is fulfilled by the provider and cached`() = runBlocking {
        var calls = 0
        val expected = remoteProblem("P1001")
        val repository = JudgeDataRepository(
            database,
            remoteProblemProviders = mapOf(
                JudgeId.LUOGU to RemoteProblemSearchProvider { judge, query, limit, offset ->
                    calls++
                    assertEquals(JudgeId.LUOGU, judge)
                    assertEquals("P1001", query)
                    assertEquals(50, limit)
                    assertEquals(0, offset)
                    listOf(expected)
                },
            ),
        )

        val first = repository.searchRemoteProblems(JudgeId.LUOGU, " P1001 ", 0, 50, 0)
        val second = repository.searchRemoteProblems(JudgeId.LUOGU, "P1001", 0, 50, 0)

        assertEquals(listOf(expected), first)
        assertEquals(listOf(expected), second)
        assertEquals(1, calls)
    }

    @Test
    fun `blank query remains local and never calls a provider`() = runBlocking {
        var calls = 0
        val repository = JudgeDataRepository(
            database,
            remoteProblemProviders = mapOf(
                JudgeId.LUOGU to RemoteProblemSearchProvider { _, _, _, _ ->
                    calls++
                    emptyList()
                },
            ),
        )

        val result = repository.searchRemoteProblems(JudgeId.LUOGU, "   ", 0, 50, 0)

        assertTrue(result.isEmpty())
        assertEquals(0, calls)
    }

    @Test
    fun `queueing a sync preserves the last successful timestamp`() = runBlocking {
        database.syncStateDao().upsert(
            SyncStateEntity(
                judge = JudgeId.LUOGU.id,
                accountId = 3,
                state = "SUCCESS",
                startedAt = 10L,
                finishedAt = 20L,
                lastSuccessfulSyncAt = 30L,
                currentStage = "PROBLEMS",
            ),
        )
        val repository = JudgeDataRepository(database)

        repository.markSyncQueued(JudgeId.LUOGU, accountId = 7)

        val state = database.syncStateDao().findByJudge(JudgeId.LUOGU.id)!!
        assertEquals("QUEUED", state.state)
        assertEquals(7L, state.accountId)
        assertEquals(null, state.startedAt)
        assertEquals(null, state.finishedAt)
        assertEquals(null, state.currentStage)
        assertEquals(30L, state.lastSuccessfulSyncAt)
    }

    @Test
    fun `a missing later page is fetched and appended to the same query cache`() = runBlocking {
        var requestedOffset = -1
        database.remoteProblemDao().upsertAll(
            (0 until 50).map { index ->
                remoteProblem("P${index.toString().padStart(4, '0')}").copy(name = "Graph Problem $index")
            },
        )
        val repository = JudgeDataRepository(
            database,
            remoteProblemProviders = mapOf(
                JudgeId.LUOGU to RemoteProblemSearchProvider { _, query, limit, offset ->
                    requestedOffset = offset
                    assertEquals("graph", query)
                    assertEquals(50, limit)
                    listOf(remoteProblem("P9999").copy(name = "Graph Problem 9999"))
                },
            ),
        )

        val result = repository.searchRemoteProblems(JudgeId.LUOGU, "graph", 0, 50, 50)

        assertEquals(50, requestedOffset)
        assertEquals("P9999", result.single().externalId)
    }

    private fun remoteProblem(externalId: String) = RemoteProblemEntity(
        judge = JudgeId.LUOGU.id,
        externalId = externalId,
        name = "A+B Problem",
        contestId = null,
        index = null,
        type = "PROGRAMMING",
        rating = 1,
        difficultySource = "OFFICIAL",
        tags = "math",
        solvedCount = 1,
        points = null,
        updatedAt = 1L,
    )
}
