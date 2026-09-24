package com.ojnexus.feature.training

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ojnexus.core.data.repository.ProblemRepository
import com.ojnexus.core.data.repository.ReviewRepository
import com.ojnexus.core.data.repository.TrainingRepository
import com.ojnexus.core.database.OjNexusDatabase
import com.ojnexus.core.database.entity.ProblemEntity
import com.ojnexus.core.model.Verdict
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionViewModelTest {

    private lateinit var database: OjNexusDatabase
    private lateinit var viewModel: SessionViewModel
    private val clock = Clock.fixed(Instant.parse("2026-09-04T12:00:00Z"), ZoneId.of("UTC"))

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, OjNexusDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        viewModel = SessionViewModel(
            sessionId = null,
            trainingRepository = TrainingRepository(database, clock),
            problemRepository = ProblemRepository(database, clock),
            reviewRepository = ReviewRepository(database, clock),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `successful result emits logged problem and clears in-flight state`() = runBlocking {
        val problemId = insertProblem()

        viewModel.logAttempt(problemId, Verdict.WA)
        awaitActionSettled()

        assertEquals(problemId, viewModel.lastLoggedProblemId.value)
        assertFalse(viewModel.actionInFlight.value)
        assertNull(viewModel.actionError.value)
    }

    @Test
    fun `failed result keeps last success and exposes action error`() = runBlocking {
        viewModel.logAttempt(999L, Verdict.WA)
        awaitActionSettled()

        assertNull(viewModel.lastLoggedProblemId.value)
        assertFalse(viewModel.actionInFlight.value)
        assertTrue(viewModel.actionError.value is SessionActionError.Generic)
    }

    @Test
    fun `second immediate result action is ignored while first is in flight`() = runBlocking {
        val firstId = insertProblem("First")
        val secondId = insertProblem("Second")

        viewModel.logAttempt(firstId, Verdict.WA)
        viewModel.logAttempt(secondId, Verdict.AC)
        awaitActionSettled()

        assertEquals(firstId, viewModel.lastLoggedProblemId.value)
        assertEquals(1, database.attemptDao().findByProblem(firstId).size)
        assertEquals(0, database.attemptDao().findByProblem(secondId).size)
    }

    private suspend fun awaitActionSettled() = withTimeout(2_000) {
        while (viewModel.actionInFlight.value) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            delay(1)
        }
        shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    private suspend fun insertProblem(title: String = "Problem") = database.problemDao().insert(
        ProblemEntity(
            judge = "CODEFORCES",
            externalId = "${title.lowercase()}-${System.nanoTime()}",
            title = title,
            difficulty = 1200,
            createdAt = clock.millis(),
            updatedAt = clock.millis(),
        ),
    )
}
