package com.ojnexus.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ojnexus.core.database.entity.WorkspaceDraftEntity

@Dao
interface WorkspaceDraftDao {
    @Query("SELECT * FROM workspace_drafts WHERE judge = :judge AND pid = :pid LIMIT 1")
    suspend fun findByKey(judge: String, pid: String): WorkspaceDraftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(draft: WorkspaceDraftEntity)
}
