package com.ojnexus.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ojnexus.core.database.OjNexusDatabase
import com.ojnexus.core.database.entity.RemoteProblemEntity
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
