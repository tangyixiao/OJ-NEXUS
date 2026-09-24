package com.ojnexus.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ojnexus.core.database.entity.RemoteProblemDetailEntity

@Dao
interface RemoteProblemDetailDao {
    @Query(
        "SELECT * FROM remote_problem_details WHERE judge = :judge AND external_id = :externalId LIMIT 1",
    )
    suspend fun findByKey(judge: String, externalId: String): RemoteProblemDetailEntity?

    @Upsert
    suspend fun upsert(detail: RemoteProblemDetailEntity)
}
