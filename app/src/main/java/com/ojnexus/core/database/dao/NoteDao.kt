package com.ojnexus.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ojnexus.core.database.entity.ProblemNoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Query("SELECT * FROM problem_notes WHERE problem_id = :problemId")
    fun observeByProblem(problemId: Long): Flow<ProblemNoteEntity?>

    @Upsert
    suspend fun upsert(note: ProblemNoteEntity)
}
