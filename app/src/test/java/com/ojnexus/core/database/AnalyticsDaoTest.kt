package com.ojnexus.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ojnexus.core.database.entity.AttemptEntity
import com.ojnexus.core.database.entity.ProblemEntity
import com.ojnexus.core.database.entity.ProblemTagCrossRef
import com.ojnexus.core.database.entity.ProblemTagEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AnalyticsDaoTest {
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
    fun `first try AC counts the earliest submission per problem`() = runBlocking {
        val first = insertProblem("codeforces", "1A")
        val second = insertProblem("atcoder", "abc_a")
        database.attemptDao().insert(AttemptEntity(problemId = first, timestamp = 10, dayIndex = 0, verdict = "WA"))
        database.attemptDao().insert(AttemptEntity(problemId = first, timestamp = 20, dayIndex = 0, verdict = "AC"))
        database.attemptDao().insert(AttemptEntity(problemId = second, timestamp = 30, dayIndex = 0, verdict = "AC"))

        val row = database.analyticsDao().observeFirstTryAc().first()

        assertEquals(2, row.attemptedProblems)
        assertEquals(1, row.firstTryAc)
    }

    @Test
    fun `first try AC is zero when there are no submissions`() = runBlocking {
        val row = database.analyticsDao().observeFirstTryAc().first()

        assertEquals(0, row.attemptedProblems)
        assertEquals(0, row.firstTryAc)
    }

    @Test
    fun `tag performance groups attempts and accepted verdicts`() = runBlocking {
        val first = insertProblem("codeforces", "1A")
        val second = insertProblem("atcoder", "abc_a")
        val dp = insertTag("dp")
        val graphs = insertTag("graphs")
        database.problemDao().insertTagCrossRef(ProblemTagCrossRef(first, dp))
        database.problemDao().insertTagCrossRef(ProblemTagCrossRef(first, graphs))
        database.problemDao().insertTagCrossRef(ProblemTagCrossRef(second, dp))
        database.attemptDao().insert(AttemptEntity(problemId = first, timestamp = 10, dayIndex = 0, verdict = "WA"))
        database.attemptDao().insert(AttemptEntity(problemId = first, timestamp = 20, dayIndex = 0, verdict = "AC"))
        database.attemptDao().insert(AttemptEntity(problemId = second, timestamp = 30, dayIndex = 0, verdict = "AC"))

        val rows = database.analyticsDao().observeTagPerformance().first()

        assertEquals(listOf("dp", "graphs"), rows.map { it.tag })
        assertEquals(3, rows[0].attempts)
        assertEquals(2, rows[0].acCount)
        assertEquals(2, rows[0].problemCount)
        assertEquals(2, rows[1].attempts)
        assertEquals(1, rows[1].acCount)
    }

    private suspend fun insertProblem(judge: String, externalId: String): Long =
        database.problemDao().insert(
            ProblemEntity(
                judge = judge,
                externalId = externalId,
                title = externalId,
                difficulty = 1000,
                createdAt = 0,
                updatedAt = 0,
            ),
        )

    private suspend fun insertTag(name: String): Long =
        database.problemDao().insertTag(ProblemTagEntity(name = name))
}
