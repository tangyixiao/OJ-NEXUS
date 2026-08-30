package com.ojnexus.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ojnexus.core.database.entity.ContestProblemMarkerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContestProblemMarkerDao {
    @Query(
        "SELECT * FROM contest_problem_markers WHERE judge = :judge AND contest_id = :contestId " +
            "ORDER BY problem_external_id",
    )
    fun observeByContest(judge: String, contestId: String): Flow<List<ContestProblemMarkerEntity>>

    @Query(
        "SELECT * FROM contest_problem_markers WHERE judge = :judge AND contest_id = :contestId " +
            "AND problem_external_id = :problemExternalId LIMIT 1",
    )
    suspend fun find(
        judge: String,
        contestId: String,
        problemExternalId: String,
    ): ContestProblemMarkerEntity?

    @Upsert
    suspend fun upsert(marker: ContestProblemMarkerEntity)

    @Query(
        "DELETE FROM contest_problem_markers WHERE judge = :judge AND contest_id = :contestId " +
            "AND problem_external_id = :problemExternalId",
    )
    suspend fun delete(judge: String, contestId: String, problemExternalId: String)

    @Query("DELETE FROM contest_problem_markers WHERE judge = :judge AND contest_id = :contestId")
    suspend fun deleteByContest(judge: String, contestId: String)
}
