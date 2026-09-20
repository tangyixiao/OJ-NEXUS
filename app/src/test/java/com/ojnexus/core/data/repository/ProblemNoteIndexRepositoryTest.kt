package com.ojnexus.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ojnexus.core.database.OjNexusDatabase
import com.ojnexus.core.database.entity.ProblemEntity
import com.ojnexus.core.database.entity.ProblemNoteEntity
import com.ojnexus.core.database.entity.ReviewEntity
import com.ojnexus.core.model.JudgeId
import com.ojnexus.core.model.NoteField
import com.ojnexus.core.model.Verdict
import com.ojnexus.core.model.textFor
import kotlinx.coroutines.flow.first
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
 * The note index joins saved notes with the problem identity they belong to. These tests pin the
 * join, the newest-first ordering, review/solved projection, the blank-row rule and the cascade
 * that removes an entry when its problem is deleted.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProblemNoteIndexRepositoryTest {

    private lateinit var database: OjNexusDatabase
    private lateinit var repository: ProblemRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, OjNexusDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val clock = Clock.fixed(Instant.parse("2026-09-20T12:00:00Z"), ZoneId.of("UTC"))
        repository = ProblemRepository(database, clock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun insertProblem(
        judge: String = "codeforces",
        externalId: String,
        title: String,
        difficulty: Int? = 1400,
        solved: Boolean = false,
        attemptCount: Int = 0,
        inReview: Boolean = false,
    ): Long {
        val problemId = database.problemDao().insert(
            ProblemEntity(
                judge = judge,
                externalId = externalId,
                title = title,
                difficulty = difficulty,
                createdAt = 1,
                updatedAt = 1,
                attemptCount = attemptCount,
                solved = solved,
            ),
        )
        if (inReview) {
            database.reviewDao().upsert(
                ReviewEntity(
                    problemId = problemId,
                    stage = 1,
                    dueAt = 10,
                    dueDayIndex = 1,
                    createdAt = 1,
                ),
            )
        }
        return problemId
    }

    private suspend fun insertNotes(
        problemId: Long,
        updatedAt: Long,
        keyInsight: String = "binary search on answer",
        implementation: String = "",
        complexity: String = "",
        general: String = "",
    ) {
        database.noteDao().upsert(
            ProblemNoteEntity(
                problemId = problemId,
                keyInsight = keyInsight,
                implementationNotes = implementation,
                complexity = complexity,
                general = general,
                updatedAt = updatedAt,
            ),
        )
    }

    @Test
    fun `index joins the problem identity with its saved notes`() = runBlocking {
        val problemId = insertProblem(
            judge = "atcoder",
            externalId = "abc300_f",
            title = "More Holidays",
            difficulty = 1900,
            solved = true,
            attemptCount = 3,
            inReview = true,
        )
        insertNotes(problemId, updatedAt = 50, complexity = "O(n log n)")

        val entry = repository.observeNoteIndex().first().single()

        assertEquals(problemId, entry.problemId)
        assertEquals(JudgeId.ATCODER, entry.key.judge)
        assertEquals("abc300_f", entry.key.externalId)
        assertEquals("More Holidays", entry.title)
        assertEquals(1900, entry.difficulty)
        assertEquals(3, entry.attemptCount)
        assertTrue(entry.solved)
        assertTrue(entry.inReview)
        assertEquals("binary search on answer", entry.notes.keyInsight)
        assertEquals("O(n log n)", entry.notes.complexity)
        assertEquals(50L, entry.notes.updatedAt)
    }

    @Test
    fun `newest note comes first and never re-sorts inside the flow`() = runBlocking {
        val older = insertProblem(externalId = "1A", title = "Theatre Square")
        val newer = insertProblem(externalId = "2B", title = "Books")
        insertNotes(older, updatedAt = 10, keyInsight = "older")
        insertNotes(newer, updatedAt = 20, keyInsight = "newer")

        val entries = repository.observeNoteIndex().first()

        assertEquals(listOf("2B", "1A"), entries.map { it.key.externalId })
    }

    @Test
    fun `a notes row emptied field by field leaves the index`() = runBlocking {
        val kept = insertProblem(externalId = "kept", title = "Kept")
        val cleared = insertProblem(externalId = "cleared", title = "Cleared")
        insertNotes(kept, updatedAt = 10, keyInsight = "still here")
        insertNotes(cleared, updatedAt = 20, keyInsight = "", implementation = " ", complexity = "\t", general = "")

        val entries = repository.observeNoteIndex().first()

        assertEquals(listOf("kept"), entries.map { it.key.externalId })
    }

    @Test
    fun `problems without notes never appear in the index`() = runBlocking {
        insertProblem(externalId = "silent", title = "No notes yet")

        assertTrue(repository.observeNoteIndex().first().isEmpty())
    }

    @Test
    fun `deleting a problem cascades its note out of the index`() = runBlocking {
        val problemId = insertProblem(externalId = "gone", title = "Gone")
        insertNotes(problemId, updatedAt = 10)

        repository.deleteProblem(problemId)

        assertTrue(repository.observeNoteIndex().first().isEmpty())
    }

    @Test
    fun `indexed entries keep the library status semantics`() = runBlocking {
        val unsolved = insertProblem(externalId = "u", title = "Unsolved", attemptCount = 0)
        val attempted = insertProblem(externalId = "a", title = "Attempted", attemptCount = 2)
        insertNotes(unsolved, updatedAt = 30, keyInsight = "todo")
        insertNotes(attempted, updatedAt = 20, keyInsight = "wa on case 4")

        val entries = repository.observeNoteIndex().first().associateBy { it.key.externalId }

        assertEquals(
            com.ojnexus.core.model.ProblemStatus.UNSOLVED,
            entries.getValue("u").status,
        )
        assertEquals(
            com.ojnexus.core.model.ProblemStatus.ATTEMPTED,
            entries.getValue("a").status,
        )
    }

    @Test
    fun `index preview stays scoped to the requested note field`() = runBlocking {
        val problemId = insertProblem(externalId = "f", title = "Fields")
        insertNotes(problemId, updatedAt = 10, keyInsight = "", implementation = "use a deque")

        val entry = repository.observeNoteIndex().first().single()

        assertEquals("use a deque", entry.notes.textFor(NoteField.ALL))
        assertEquals("use a deque", entry.notes.textFor(NoteField.IMPLEMENTATION))
        assertEquals(null, entry.notes.textFor(NoteField.KEY_INSIGHT))
    }

    @Test
    fun `a recorded attempt never becomes note-index content`() = runBlocking {
        val problemId = insertProblem(externalId = "verdict", title = "Verdict")
        repository.addAttempt(
            problemId = problemId,
            verdict = Verdict.WA,
            durationMinutes = 20,
            language = "C++",
            note = "off by one",
        )

        assertTrue(repository.observeNoteIndex().first().isEmpty())
    }
}
