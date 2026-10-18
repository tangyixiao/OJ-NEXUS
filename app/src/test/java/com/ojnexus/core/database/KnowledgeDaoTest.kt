package com.ojnexus.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ojnexus.core.database.entity.AttemptEntity
import com.ojnexus.core.database.entity.FailureEntryEntity
import com.ojnexus.core.database.entity.ProblemEntity
import com.ojnexus.core.database.entity.ProblemKnowledgeEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KnowledgeDaoTest {
    private lateinit var database: OjNexusDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            OjNexusDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `performance groups attempts and failures by explicit knowledge relation`() = runBlocking {
        val problemId = database.problemDao().insert(
            ProblemEntity(
                judge = "local",
                externalId = "p1",
                title = "Graph",
                difficulty = 1200,
                createdAt = 1,
                updatedAt = 1,
                solved = true,
                attemptCount = 2,
            ),
        )
        database.knowledgeDao().upsert(ProblemKnowledgeEntity(problemId, "GRAPH"))
        val wa = database.attemptDao().insert(
            AttemptEntity(problemId = problemId, timestamp = 2, dayIndex = 0, verdict = "WA"),
        )
        database.attemptDao().insert(
            AttemptEntity(problemId = problemId, timestamp = 3, dayIndex = 0, verdict = "AC"),
        )
        database.failureDao().insert(
            FailureEntryEntity(
                problemId = problemId,
                attemptId = wa,
                category = "KNOWLEDGE_GAP",
                description = "missing invariant",
                createdAt = 4,
                dayIndex = 0,
            ),
        )

        val row = database.knowledgeDao().observePerformance(todayEpochDay = 0L).first().single()
        assertEquals("GRAPH", row.area)
        assertEquals(1, row.attemptedProblems)
        assertEquals(1, row.solvedProblems)
        assertEquals(2, row.attempts)
        assertEquals(1, row.failures)
        // Never solved on day 0, so first_solved_at is null and the decay input has nothing.
        assertEquals(null, row.daysSinceLastSolved)
    }

    @Test
    fun `performance reports days since the newest solved problem in the area`() = runBlocking {
        val problemId = database.problemDao().insert(
            ProblemEntity(
                judge = "local",
                externalId = "p1",
                title = "Graph",
                difficulty = 1200,
                createdAt = 1,
                updatedAt = 1,
                solved = true,
                attemptCount = 1,
                firstSolvedAt = 10L * 86_400_000L,
            ),
        )
        database.knowledgeDao().upsert(ProblemKnowledgeEntity(problemId, "GRAPH"))

        val row = database.knowledgeDao().observePerformance(todayEpochDay = 15L).first().single()
        assertEquals(5, row.daysSinceLastSolved)
    }

    @Test
    fun `days since solved is null for an area with no solved problem`() = runBlocking {
        val problemId = database.problemDao().insert(
            ProblemEntity(
                judge = "local",
                externalId = "p1",
                title = "Graph",
                difficulty = 1200,
                createdAt = 1,
                updatedAt = 1,
                solved = false,
                attemptCount = 1,
            ),
        )
        database.knowledgeDao().upsert(ProblemKnowledgeEntity(problemId, "GRAPH"))

        val row = database.knowledgeDao().observePerformance(todayEpochDay = 15L).first().single()
        assertEquals(null, row.daysSinceLastSolved)
    }
}
