package com.ojnexus.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ojnexus.core.database.entity.ProblemNoteEntity
import kotlinx.coroutines.flow.Flow

/**
 * Flat note-index projection: saved notes joined with the problem identity they describe.
 * Deliberately scalar (no `@Relation`) so the index stays a single bounded read.
 */
data class NoteIndexRow(
    val problemId: Long,
    val judge: String,
    val externalId: String,
    val title: String,
    val difficulty: Int?,
    val attemptCount: Int,
    val solved: Boolean,
    val inReview: Boolean,
    val keyInsight: String,
    val implementationNotes: String,
    val complexity: String,
    val general: String,
    val updatedAt: Long,
)

@Dao
interface NoteDao {

    @Query("SELECT * FROM problem_notes WHERE problem_id = :problemId")
    fun observeByProblem(problemId: Long): Flow<ProblemNoteEntity?>

    @Query("SELECT * FROM problem_notes WHERE problem_id = :problemId")
    suspend fun findByProblem(problemId: Long): ProblemNoteEntity?

    @Upsert
    suspend fun upsert(note: ProblemNoteEntity)

    /**
     * Every problem carrying a notes row, newest note first. Notes rows whose four fields are
     * all blank are still returned; the repository drops them so the "has text" rule stays
     * testable outside SQL.
     */
    @Query(
        "SELECT n.problem_id AS problemId, p.judge AS judge, p.external_id AS externalId, " +
            "p.title AS title, p.difficulty AS difficulty, p.attempt_count AS attemptCount, " +
            "p.solved AS solved, " +
            "EXISTS(SELECT 1 FROM reviews r WHERE r.problem_id = p.id) AS inReview, " +
            "n.key_insight AS keyInsight, n.implementation_notes AS implementationNotes, " +
            "n.complexity AS complexity, n.general AS general, n.updated_at AS updatedAt " +
            "FROM problem_notes n JOIN problems p ON p.id = n.problem_id " +
            "ORDER BY n.updated_at DESC, n.problem_id DESC",
    )
    fun observeIndex(): Flow<List<NoteIndexRow>>
}
