package com.ojnexus.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ojnexus.core.database.entity.AttemptEntity
import com.ojnexus.core.database.entity.FailureEntryEntity
import com.ojnexus.core.database.entity.ProblemEntity
import com.ojnexus.core.database.entity.ProblemNoteEntity
import com.ojnexus.core.database.entity.ProblemTagCrossRef
import com.ojnexus.core.database.entity.ProblemTagEntity
import com.ojnexus.core.database.entity.ReviewEntity
import com.ojnexus.core.database.entity.TrainingTaskEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * DAO-level behaviour tests run on the JVM through Robolectric. They verify schema
 * guarantees (unique keys, cascades) and the counter-maintenance queries that keep
 * derived problem fields consistent.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OjNexusDatabaseTest {

    private lateinit var database: OjNexusDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, OjNexusDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun problem(judge: String, externalId: String, title: String = "Problem $externalId") =
        ProblemEntity(
            judge = judge,
            externalId = externalId,
            title = title,
            difficulty = 1800,
            createdAt = 1_000L,
            updatedAt = 1_000L,
        )

    @Test
    fun `same external id on different judges does not collide`() = runBlocking {
        database.problemDao().insert(problem("codeforces", "P1000"))
        database.problemDao().insert(problem("luogu", "P1000"))
        assertEquals(2, database.problemDao().count())
    }

    @Test
    fun `duplicate judge and external id is rejected by unique index`() {
        assertThrows(RuntimeException::class.java) {
            runBlocking {
                database.problemDao().insert(problem("codeforces", "1919F"))
                database.problemDao().insert(problem("codeforces", "1919F"))
            }
        }
    }

    @Test
    fun `applyAttempt keeps counters consistent`() = runBlocking {
        val problemId = database.problemDao().insert(problem("codeforces", "1A"))

        database.problemDao().applyAttempt(
            id = problemId, timestamp = 2_000L, solved = false, firstSolvedAt = null, updatedAt = 2_000L,
        )
        val afterWa = database.problemDao().findById(problemId)!!
        assertEquals(1, afterWa.attemptCount)
        assertEquals(2_000L, afterWa.lastAttemptAt)
        assertFalse(afterWa.solved)
        assertNull(afterWa.firstSolvedAt)

        database.problemDao().applyAttempt(
            id = problemId, timestamp = 3_000L, solved = true, firstSolvedAt = 3_000L, updatedAt = 3_000L,
        )
        val afterAc = database.problemDao().findById(problemId)!!
        assertEquals(2, afterAc.attemptCount)
        assertTrue(afterAc.solved)
        assertEquals(3_000L, afterAc.firstSolvedAt)

        // A later failure never unsolves the problem or moves first AC.
        database.problemDao().applyAttempt(
            id = problemId, timestamp = 4_000L, solved = false, firstSolvedAt = null, updatedAt = 4_000L,
        )
        val afterLaterWa = database.problemDao().findById(problemId)!!
        assertEquals(3, afterLaterWa.attemptCount)
        assertTrue(afterLaterWa.solved)
        assertEquals(3_000L, afterLaterWa.firstSolvedAt)
    }

    @Test
    fun `deleting a problem cascades to every owned row`() = runBlocking {
        val problemId = database.problemDao().insert(problem("codeforces", "2B"))
        val attemptId = database.attemptDao().insert(
            AttemptEntity(problemId = problemId, timestamp = 10L, dayIndex = 0, verdict = "WA"),
        )
        database.failureDao().insert(
            FailureEntryEntity(
                problemId = problemId, attemptId = attemptId, category = "THINKING",
                description = "wrong direction", createdAt = 11L, dayIndex = 0,
            ),
        )
        database.noteDao().upsert(ProblemNoteEntity(problemId = problemId, general = "notes", updatedAt = 12L))
        database.reviewDao().upsert(
            ReviewEntity(problemId = problemId, stage = 0, dueAt = 100L, dueDayIndex = 1, createdAt = 13L),
        )
        database.taskDao().insert(
            TrainingTaskEntity(dateEpochDay = 1, type = "SOLVE", problemId = problemId, createdAt = 14L),
        )
        val tagId = database.problemDao().insertTag(ProblemTagEntity(name = "dp"))
        database.problemDao().insertTagCrossRef(ProblemTagCrossRef(problemId, tagId))

        database.problemDao().delete(database.problemDao().findById(problemId)!!)

        assertEquals(0, database.problemDao().count())
        assertTrue(database.attemptDao().findByProblem(problemId).isEmpty())
        assertNull(database.noteDao().findByProblem(problemId))
        assertNull(database.reviewDao().findByProblem(problemId))
        assertTrue(database.problemDao().tagIdsFor(problemId).isEmpty())
        // The failure entry and the linked task die with the problem too.
        assertEquals(0, database.failureDao().count())
        assertEquals(0, database.taskDao().count())
    }

    @Test
    fun `library exposes in_review flag from the reviews table`() = runBlocking {
        val id1 = database.problemDao().insert(problem("codeforces", "3C"))
        val id2 = database.problemDao().insert(problem("codeforces", "3D"))
        database.reviewDao().upsert(
            ReviewEntity(problemId = id1, stage = 1, dueAt = 100L, dueDayIndex = 2, createdAt = 3L),
        )

        val library = database.problemDao().findLibrary()
        assertEquals(2, library.size)
        val inReview = library.first { it.problem.id == id1 }
        val notInReview = library.first { it.problem.id == id2 }
        assertTrue(inReview.inReview)
        assertFalse(notInReview.inReview)
        assertNotNull(inReview.tags)
    }

    @Test
    fun `training candidate query stays bounded for scroll performance`() = runBlocking {
        repeat(40) { index ->
            database.problemDao().insert(problem("codeforces", "candidate-$index"))
        }

        val candidates = database.problemDao().observeTrainingCandidates(0L, limit = 20).first()

        assertEquals(20, candidates.size)
    }
}
