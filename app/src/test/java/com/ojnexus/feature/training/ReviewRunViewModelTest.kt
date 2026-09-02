package com.ojnexus.feature.training

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ojnexus.core.database.OjNexusDatabase
import com.ojnexus.core.database.entity.ProblemEntity
import com.ojnexus.core.database.entity.ReviewEntity
import com.ojnexus.core.data.repository.ReviewRepository
import com.ojnexus.core.model.ReviewResult
import com.ojnexus.core.ui.Loadable
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReviewRunViewModelTest {

    private lateinit var database: OjNexusDatabase
    private lateinit var reviewRepository: ReviewRepository
    private val clock = Clock.fixed(Instant.parse("2026-09-03T12:00:00Z"), ZoneId.of("UTC"))

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, OjNexusDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        reviewRepository = ReviewRepository(database, clock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `record advances through captured reviews and completes the run`() = runBlocking {
        val first = insertDueReview("1A", "Alpha", dueAt = 20L)
        val second = insertDueReview("1B", "Beta", dueAt = 10L)
        val viewModel = ReviewRunViewModel(
            reviewRepository = reviewRepository,
            clock = clock,
            localizedErrorMessage = { "LOAD FAILED" },
        )

        awaitReady(viewModel) { it.total == 2 }
        assertEquals(second, awaitReady(viewModel) { it.active != null }.active?.problemId)

        viewModel.record(ReviewResult.PASS)
        val afterFirst = awaitReady(viewModel) { it.completedCount == 1 }
        assertNull(afterFirst.active)
        assertEquals(second, afterFirst.completedItem?.problemId)
        assertEquals(ReviewResult.PASS, afterFirst.lastOutcome?.result)

        viewModel.next()
        val next = awaitReady(viewModel) { it.completedCount == 1 && it.lastOutcome == null }
        assertEquals(first, next.active?.problemId)
        viewModel.record(ReviewResult.HARD)
        val complete = awaitReady(viewModel) { it.completedCount == 2 }
        assertNull(complete.active)
        assertEquals(ReviewResult.HARD, complete.lastOutcome?.result)
    }

    @Test
    fun `record failure keeps the current item visible`() = runBlocking {
        val problemId = insertDueReview("2A", "Gamma", dueAt = 10L)
        val viewModel = ReviewRunViewModel(
            reviewRepository = reviewRepository,
            clock = clock,
            localizedErrorMessage = { "LOAD FAILED" },
        )
        awaitReady(viewModel) { it.active?.problemId == problemId }

        database.reviewDao().deleteByProblem(problemId)
        viewModel.record(ReviewResult.PASS)

        val failed = awaitReady(viewModel) { it.error != null }
        assertEquals(problemId, failed.active?.problemId)
        assertEquals("LOAD FAILED", failed.error)
    }

    @Test
    fun `record recovers when repository throws unexpectedly`() = runBlocking {
        val problemId = insertDueReview("3A", "Delta", dueAt = 10L)
        val viewModelWithFailure = ReviewRunViewModel(
            reviewRepository = reviewRepository,
            clock = clock,
            localizedErrorMessage = { "LOAD FAILED" },
            completeReview = { _, _ -> error("unexpected repository failure") },
        )
        awaitReady(viewModelWithFailure) { it.active?.problemId == problemId }
        viewModelWithFailure.record(ReviewResult.FAIL)

        val recovered = awaitReady(viewModelWithFailure) { it.error != null && !it.isRecording }
        assertEquals(problemId, recovered.active?.problemId)
        assertEquals("LOAD FAILED", recovered.error)
    }

    private suspend fun awaitReady(
        viewModel: ReviewRunViewModel,
        predicate: (ReviewRunUiState) -> Boolean,
    ): ReviewRunUiState = withTimeout(2_000) {
        while (true) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            val state = viewModel.state.value
            if (state is Loadable.Ready && predicate(state.value)) return@withTimeout state.value
            delay(1)
        }
        error("timed out waiting for review run state")
    }

    private suspend fun insertDueReview(code: String, title: String, dueAt: Long): Long {
        val problemId = database.problemDao().insert(
            ProblemEntity(
                judge = "CODEFORCES",
                externalId = code,
                title = title,
                difficulty = 800,
                createdAt = clock.millis(),
                updatedAt = clock.millis(),
            ),
        )
        database.reviewDao().upsert(
            ReviewEntity(
                problemId = problemId,
                stage = 0,
                dueAt = dueAt,
                dueDayIndex = clock.instant().atZone(clock.zone).toLocalDate().toEpochDay(),
                createdAt = clock.millis(),
            ),
        )
        return problemId
    }
}
