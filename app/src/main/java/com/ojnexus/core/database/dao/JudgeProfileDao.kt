package com.ojnexus.core.database.dao

import androidx.room.Query
import androidx.room.Upsert
import androidx.room.Dao
import com.ojnexus.core.database.entity.JudgeProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface JudgeProfileDao {

    @Query("SELECT * FROM judge_profiles WHERE judge = :judge")
    fun observeByJudge(judge: String): Flow<JudgeProfileEntity?>

    @Upsert
    suspend fun upsert(profile: JudgeProfileEntity)

    @Query("DELETE FROM judge_profiles WHERE judge = :judge")
    suspend fun deleteByJudge(judge: String)
}
