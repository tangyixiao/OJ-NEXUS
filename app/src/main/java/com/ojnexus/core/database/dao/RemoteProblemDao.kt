package com.ojnexus.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ojnexus.core.database.entity.RemoteProblemEntity
import kotlinx.coroutines.flow.Flow

data class ContestProblemProgressRow(
    val judge: String,
    val externalId: String,
    val name: String,
    val index: String?,
    val rating: Int?,
    val difficultySource: String,
    val points: Double?,
    val localProblemId: Long?,
    val solved: Boolean?,
    val attemptCount: Int?,
    val latestVerdict: String?,
)

/**
 * Remote catalog queries are SQL-driven — the catalog can hold tens of thousands of rows,
 * so nothing here ever loads the full table into memory.
 *
 * `solvedFilter`: 0 = all, 1 = solved by the connected account (EXISTS an AC attempt),
 * 2 = not solved (no AC attempt). The EXISTS runs against attempts joined through the
 * materialized local problem row via ProblemKey — indexed, never a full scan in Kotlin.
 */
@Dao
interface RemoteProblemDao {

    @Query(
        "SELECT r.judge AS judge, r.external_id AS externalId, r.name AS name, " +
            "r.`index` AS `index`, r.rating AS rating, r.difficulty_source AS difficultySource, " +
            "r.points AS points, p.id AS localProblemId, p.solved AS solved, " +
            "p.attempt_count AS attemptCount, " +
            "(SELECT a.verdict FROM attempts a WHERE a.problem_id = p.id " +
            "ORDER BY a.timestamp DESC, a.id DESC LIMIT 1) AS latestVerdict " +
            "FROM remote_problems r LEFT JOIN problems p ON p.judge = r.judge " +
            "AND p.external_id = r.external_id " +
            "WHERE r.judge = :judge AND r.contest_id = :contestId " +
            "ORDER BY r.`index` IS NULL, r.`index`, r.external_id",
    )
    fun observeContestProgress(judge: String, contestId: String): Flow<List<ContestProblemProgressRow>>

    @Upsert
    suspend fun upsertAll(problems: List<RemoteProblemEntity>)

    @Query("SELECT COUNT(*) FROM remote_problems WHERE judge = :judge")
    suspend fun countByJudge(judge: String): Int

    @Query(
        "SELECT * FROM remote_problems WHERE judge = :judge " +
            "AND (:query = '' OR name LIKE '%' || :query || '%' OR external_id LIKE '%' || :query || '%') " +
            "AND (:minRating IS NULL OR (rating IS NOT NULL AND rating >= :minRating)) " +
            "AND (:maxRating IS NULL OR (rating IS NOT NULL AND rating <= :maxRating)) " +
            "AND (:solvedFilter = 0 OR " +
            "  (:solvedFilter = 1 AND EXISTS(" +
            "    SELECT 1 FROM attempts a JOIN problems p ON p.id = a.problem_id " +
            "    WHERE p.judge = remote_problems.judge AND p.external_id = remote_problems.external_id " +
            "    AND a.source_judge = remote_problems.judge AND a.verdict = 'AC')) " +
            "  OR (:solvedFilter = 2 AND NOT EXISTS(" +
            "    SELECT 1 FROM attempts a JOIN problems p ON p.id = a.problem_id " +
            "    WHERE p.judge = remote_problems.judge AND p.external_id = remote_problems.external_id " +
            "    AND a.source_judge = remote_problems.judge AND a.verdict = 'AC'))) " +
            "ORDER BY rating IS NULL, rating ASC, external_id ASC LIMIT :limit OFFSET :offset",
    )
    suspend fun search(
        judge: String,
        query: String,
        minRating: Int?,
        maxRating: Int?,
        solvedFilter: Int,
        limit: Int,
        offset: Int,
    ): List<RemoteProblemEntity>

    @Query(
        "SELECT * FROM remote_problems WHERE judge = :judge AND external_id = :externalId LIMIT 1",
    )
    suspend fun findByKey(judge: String, externalId: String): RemoteProblemEntity?

    @Query("DELETE FROM remote_problems WHERE judge = :judge")
    suspend fun deleteByJudge(judge: String)
}
