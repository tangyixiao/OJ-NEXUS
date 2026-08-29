package com.ojnexus.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.ojnexus.core.database.entity.JudgeAccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface JudgeAccountDao {

    @Query("SELECT * FROM judge_accounts ORDER BY connected_at DESC")
    fun observeAll(): Flow<List<JudgeAccountEntity>>

    @Query("SELECT * FROM judge_accounts WHERE id = :id")
    suspend fun findById(id: Long): JudgeAccountEntity?

    @Query("SELECT * FROM judge_accounts WHERE judge = :judge AND enabled = 1 LIMIT 1")
    suspend fun findActiveByJudge(judge: String): JudgeAccountEntity?

    @Query("SELECT * FROM judge_accounts WHERE judge = :judge AND enabled = 1 LIMIT 1")
    fun observeActiveByJudge(judge: String): Flow<JudgeAccountEntity?>

    @Query("SELECT * FROM judge_accounts WHERE judge = :judge AND canonical_handle = :handle LIMIT 1")
    suspend fun findByJudgeAndHandle(judge: String, handle: String): JudgeAccountEntity?

    @Insert
    suspend fun insert(account: JudgeAccountEntity): Long

    @Update
    suspend fun update(account: JudgeAccountEntity)

    @Query("DELETE FROM judge_accounts WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM judge_accounts WHERE judge = :judge AND enabled = 1")
    suspend fun countActiveByJudge(judge: String): Int
}
