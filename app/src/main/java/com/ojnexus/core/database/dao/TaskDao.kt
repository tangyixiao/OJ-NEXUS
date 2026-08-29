package com.ojnexus.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.ojnexus.core.database.entity.TrainingTaskEntity
import com.ojnexus.core.database.relation.TaskWithProblemPojo
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Transaction
    @Query(
        "SELECT * FROM training_tasks WHERE date_epoch_day = :dateEpochDay " +
            "ORDER BY completed ASC, priority DESC, created_at ASC",
    )
    fun observeByDate(dateEpochDay: Long): Flow<List<TaskWithProblemPojo>>

    @Insert
    suspend fun insert(task: TrainingTaskEntity): Long

    @Update
    suspend fun update(task: TrainingTaskEntity)

    @Query("DELETE FROM training_tasks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM training_tasks WHERE date_epoch_day = :dateEpochDay AND completed = 1")
    suspend fun deleteCompleted(dateEpochDay: Long)
}
