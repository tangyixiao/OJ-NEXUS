package com.ojnexus.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ojnexus.core.data.DataError
import com.ojnexus.core.data.DataResult
import com.ojnexus.core.database.OjNexusDatabase
import com.ojnexus.core.model.TrainingType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * Regression tests for the single-live-session invariant.
 *
 * Boundary note: the concurrency case relies on Room's `withTransaction` serializing write
 * transactions on the single write connection, which SQLite guarantees. The two coroutines
 * may or may not actually interleave on a given JVM scheduler — if they serialize, the test
 * degenerates to the deterministic guard case and still passes. The invariant it protects
 * (at most one RUNNING/PAUSED session) can therefore never fail flakily: either the
 * transaction ordering makes the guard redundant, or the in-transaction check rejects the
 * second create.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrainingRepositorySessionTest {

    private lateinit var database: OjNexusDatabase
    private lateinit var problemRepository: ProblemRepository
    private lateinit var trainingRepository: TrainingRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, OjNexusDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val clock = Clock.fixed(Instant.parse("2026-08-29T12:00:00Z"), ZoneId.of("UTC"))
        problemRepository = ProblemRepository(database, clock)
        trainingRepository = TrainingRepository(database, clock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `create fails atomically when a live session exists - no partial rows`() = runBlocking {
        val first = trainingRepository.createAndStartSession(TrainingType.PRACTICE, 30, null, emptyList())
        assertTrue(first is DataResult.Success)

        val second = trainingRepository.createAndStartSession(TrainingType.FOCUS, null, null, emptyList())

        assertTrue(second is DataResult.Failure)
        val error = (second as DataResult.Failure).error
        assertTrue(error is DataError.Storage)
        assertEquals("A session is already active", error.message)
        // The rejected create left no partial session rows behind.
        assertEquals(1, database.sessionDao().countActive())
        assertEquals(1, totalSessions())
    }

    @Test
    fun `failed create with problems writes no session row`() = runBlocking {
        val result = trainingRepository.createAndStartSession(
            TrainingType.PRACTICE, null, null, emptyList(),
        )
        assertTrue(result is DataResult.Success)

        // Unknown problem ids fail before anything is written.
        val invalid = trainingRepository.createAndStartSession(
            TrainingType.PRACTICE, null, null, listOf(999L),
        )
        assertTrue(invalid is DataResult.Failure)
        assertTrue((invalid as DataResult.Failure).error is DataError.NotFound)
        assertEquals(1, totalSessions())
    }

    @Test
    fun `concurrent creates admit exactly one live session`() = runBlocking {
        val attempts = (1..4).map {
            async(Dispatchers.IO) {
                trainingRepository.createAndStartSession(TrainingType.PRACTICE, null, null, emptyList())
            }
        }.awaitAll()

        val successes = attempts.count { it is DataResult.Success }
        val failureList = attempts.filterIsInstance<DataResult.Failure>()
        assertEquals("exactly one create may win", 1, successes)
        assertEquals(3, failureList.size)
        failureList.forEach { attempt ->
            val error = attempt.error
            assertTrue(
                "rejections are the session-limit guard, got: $error",
                error is DataError.Storage && error.message == "A session is already active",
            )
        }
        // The invariant that actually matters: at most one RUNNING/PAUSED session, ever.
        assertEquals(1, database.sessionDao().countActive())
        assertEquals(1, totalSessions())
    }

    private suspend fun totalSessions(): Int = database.sessionDao().countAll()
}
