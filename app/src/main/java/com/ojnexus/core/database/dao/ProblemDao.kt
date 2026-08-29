package com.ojnexus.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.ojnexus.core.database.entity.ProblemEntity
import com.ojnexus.core.database.entity.ProblemTagCrossRef
import com.ojnexus.core.database.entity.ProblemTagEntity
import com.ojnexus.core.database.relation.ProblemDetailPojo
import com.ojnexus.core.database.relation.ProblemWithTagsPojo
import kotlinx.coroutines.flow.Flow

@Dao
interface ProblemDao {

    @Transaction
    @Query(
        "SELECT *, EXISTS(SELECT 1 FROM reviews WHERE reviews.problem_id = problems.id) AS in_review " +
            "FROM problems",
    )
    fun observeLibrary(): Flow<List<ProblemWithTagsPojo>>

    @Transaction
    @Query(
        "SELECT *, EXISTS(SELECT 1 FROM reviews WHERE reviews.problem_id = problems.id) AS in_review " +
            "FROM problems",
    )
    suspend fun findLibrary(): List<ProblemWithTagsPojo>

    @Transaction
    @Query("SELECT * FROM problems WHERE id = :id")
    fun observeDetail(id: Long): Flow<ProblemDetailPojo?>

    @Transaction
    @Query("SELECT * FROM problems WHERE id = :id")
    suspend fun findDetail(id: Long): ProblemDetailPojo?

    @Query("SELECT * FROM problems WHERE id = :id")
    suspend fun findById(id: Long): ProblemEntity?

    @Query("SELECT * FROM problems WHERE judge = :judge AND external_id = :externalId")
    suspend fun findByKey(judge: String, externalId: String): ProblemEntity?

    @Insert
    suspend fun insert(problem: ProblemEntity): Long

    @Update
    suspend fun update(problem: ProblemEntity)

    @Delete
    suspend fun delete(problem: ProblemEntity)

    @Query("UPDATE problems SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    /**
     * Counter/state update applied atomically with an attempt insert. `solved` is sticky:
     * once true it never regresses, and `firstSolvedAt` only fills in when previously null.
     */
    @Query(
        "UPDATE problems SET attempt_count = attempt_count + 1, last_attempt_at = :timestamp, " +
            "solved = CASE WHEN solved THEN 1 ELSE :solved END, " +
            "first_solved_at = COALESCE(first_solved_at, :firstSolvedAt), " +
            "updated_at = :updatedAt WHERE id = :id",
    )
    suspend fun applyAttempt(
        id: Long,
        timestamp: Long,
        solved: Boolean,
        firstSolvedAt: Long?,
        updatedAt: Long,
    )

    @Query("UPDATE problems SET updated_at = :updatedAt WHERE id = :id")
    suspend fun touch(id: Long, updatedAt: Long)

    /**
     * Rejudge-safe solved promotion: a synced AC promotes the problem to solved without
     * incrementing counters, and never moves an existing first-solved timestamp.
     */
    @Query(
        "UPDATE problems SET solved = 1, first_solved_at = COALESCE(first_solved_at, :solvedAt), " +
            "updated_at = :updatedAt WHERE id = :id",
    )
    suspend fun promoteSolved(id: Long, solvedAt: Long, updatedAt: Long)

    /** Remote-metadata merge (Phase 2): title/difficulty/url are remote-authoritative. */
    @Query(
        "UPDATE problems SET title = :title, difficulty = :difficulty, " +
            "source_url = :sourceUrl, updated_at = :updatedAt WHERE id = :id",
    )
    suspend fun applyRemoteMetadata(
        id: Long,
        title: String,
        difficulty: Int?,
        sourceUrl: String?,
        updatedAt: Long,
    )

    @Query("SELECT COUNT(*) FROM problems")
    suspend fun count(): Int

    // --- Tags ---

    @Query("SELECT * FROM problem_tags ORDER BY name")
    fun observeTags(): Flow<List<ProblemTagEntity>>

    @Query("SELECT * FROM problem_tags WHERE name = :name")
    suspend fun findTagByName(name: String): ProblemTagEntity?

    @Insert
    suspend fun insertTag(tag: ProblemTagEntity): Long

    @Insert
    suspend fun insertTagCrossRef(crossRef: ProblemTagCrossRef)

    @Delete
    suspend fun deleteTagCrossRef(crossRef: ProblemTagCrossRef)

    @Query(
        "DELETE FROM problem_tag_cross_ref WHERE problem_id = :problemId AND tag_id = :tagId",
    )
    suspend fun deleteTagLink(problemId: Long, tagId: Long)

    @Query("SELECT tag_id FROM problem_tag_cross_ref WHERE problem_id = :problemId")
    suspend fun tagIdsFor(problemId: Long): List<Long>
}
