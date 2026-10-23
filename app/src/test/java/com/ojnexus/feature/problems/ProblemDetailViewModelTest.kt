package com.ojnexus.feature.problems

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.ojnexus.core.data.preferences.UserPreferencesRepository
import com.ojnexus.core.data.repository.KnowledgeRepository
import com.ojnexus.core.data.repository.ProblemRepository
import com.ojnexus.core.data.repository.ReviewRepository
import com.ojnexus.core.database.OjNexusDatabase
import com.ojnexus.core.database.entity.ProblemEntity
import com.ojnexus.core.model.KnowledgeArea
import com.ojnexus.core.model.ProblemNotes
import com.ojnexus.core.time.SystemLocalDaySource
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Job
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProblemDetailViewModelTest {

    private val clock = Clock.fixed(Instant.parse("2026-10-23T12:00:00Z"), ZoneId.of("UTC"))
    private lateinit var database: OjNexusDatabase
    private lateinit var problemRepository: ProblemRepository
    private lateinit var viewModel: ProblemDetailViewModel
    private lateinit var testScope: CoroutineScope
    private lateinit var stateCollector: Job
    private var problemId = 0L

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, OjNexusDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        problemRepository = ProblemRepository(database, clock)
        val reviewRepository = ReviewRepository(database, clock)
        val knowledgeRepository = KnowledgeRepository(database, SystemLocalDaySource(clock))
        problemId = database.problemDao().insert(
            ProblemEntity(
                judge = "CODEFORCES",
                externalId = "phase-122-${System.nanoTime()}",
                title = "Note editor state regression",
                difficulty = 1200,
                createdAt = clock.millis(),
                updatedAt = clock.millis(),
            ),
        )
        testScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        viewModel = ProblemDetailViewModel(
            problemId = problemId,
            problemRepository = problemRepository,
            reviewRepository = reviewRepository,
            knowledgeRepository = knowledgeRepository,
            preferencesRepository = UserPreferencesRepository(context),
        )
        stateCollector = testScope.launch {
            viewModel.state.collect { state ->
                if (state is com.ojnexus.core.ui.Loadable.Ready && state.value.notesSaving) {
                    notesSavingWasObserved = true
                }
            }
        }
        awaitReadyState()
    }

    @After
    fun tearDown() {
        testScope.cancel()
        database.close()
    }

    @Test
    fun `state emits note draft edits before detail flow changes`() = runBlocking {
        viewModel.setNotesField { it.copy(keyInsight = "typed immediately") }
        drainMainLooper()

        val state = readyState()
        assertEquals("typed immediately", state.notesDraft?.keyInsight)
        assertFalse(state.notesSaving)
    }

    @Test
    fun `consecutive note edits retain full draft through detail and knowledge emissions`() = runBlocking {
        viewModel.setNotesField { it.copy(keyInsight = "the insight") }
        drainMainLooper()
        viewModel.setNotesField { it.copy(implementationNotes = "first step") }
        drainMainLooper()

        problemRepository.saveNotes(
            ProblemNotes(
                problemId = problemId,
                keyInsight = "stale stored value",
                implementationNotes = "",
                complexity = "",
                general = "",
                updatedAt = 0,
            ),
        )
        KnowledgeArea.entries.firstOrNull()?.let { area ->
            viewModel.setKnowledge(area, true)
        }
        drainMainLooper()

        val draft = readyState().notesDraft
        assertEquals("the insight", draft?.keyInsight)
        assertEquals("first step", draft?.implementationNotes)
    }

    @Test
    fun `debounced save persists complete latest draft and state exposes saving flow`() = runBlocking {
        val transactionStarted = CompletableDeferred<Unit>()
        val releaseTransaction = CompletableDeferred<Unit>()
        val blocker = CoroutineScope(Dispatchers.IO).launch {
            database.withTransaction {
                transactionStarted.complete(Unit)
                releaseTransaction.await()
            }
        }
        transactionStarted.await()

        viewModel.setNotesField { it.copy(keyInsight = "full key insight") }
        drainMainLooper()
        viewModel.setNotesField { it.copy(implementationNotes = "full implementation") }
        drainMainLooper()
        viewModel.setNotesField { it.copy(complexity = "O(n log n)", general = "Unicode: 题解") }
        drainMainLooper()

        assertEquals("O(n log n)", readyState().notesDraft?.complexity)
        withTimeout(3_000) {
            while (!readyState().notesSaving) {
                shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(10))
                delay(5)
            }
        }
        assertTrue(readyState().notesSaving)
        releaseTransaction.complete(Unit)
        blocker.join()

        withTimeout(3_000) {
            while (database.noteDao().findByProblem(problemId) == null) {
                shadowOf(android.os.Looper.getMainLooper()).idle()
                delay(5)
            }
        }
        val saved = database.noteDao().findByProblem(problemId)
        assertEquals("full key insight", saved?.keyInsight)
        assertEquals("full implementation", saved?.implementationNotes)
        assertEquals("O(n log n)", saved?.complexity)
        assertEquals("Unicode: 题解", saved?.general)
        assertTrue(notesSavingWasObserved)
        withTimeout(3_000) {
            while (readyState().notesSaving) {
                shadowOf(android.os.Looper.getMainLooper()).idle()
                delay(5)
            }
        }
    }

    private var notesSavingWasObserved: Boolean = false

    private suspend fun awaitReadyState() {
        withTimeout(3_000) {
            while (viewModel.state.value !is com.ojnexus.core.ui.Loadable.Ready) {
                shadowOf(android.os.Looper.getMainLooper()).idle()
                delay(5)
            }
        }
    }

    private suspend fun drainMainLooper() {
        shadowOf(android.os.Looper.getMainLooper()).idle()
        delay(5)
        shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    private fun readyState(): ProblemDetailUiState =
        (viewModel.state.value as com.ojnexus.core.ui.Loadable.Ready).value
}
