package com.ojnexus.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ojnexus.core.database.entity.RemoteProblemEntity
import kotlinx.coroutines.flow.Flow

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
