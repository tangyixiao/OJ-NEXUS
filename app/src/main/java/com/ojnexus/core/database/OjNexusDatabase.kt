package com.ojnexus.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.ojnexus.core.database.dao.AnalyticsDao
import com.ojnexus.core.database.dao.AttemptDao
import com.ojnexus.core.database.dao.FailureDao
import com.ojnexus.core.database.dao.NoteDao
import com.ojnexus.core.database.dao.ProblemDao
import com.ojnexus.core.database.dao.ReviewDao
import com.ojnexus.core.database.dao.SessionDao
import com.ojnexus.core.database.dao.TaskDao
import com.ojnexus.core.database.entity.AttemptEntity
import com.ojnexus.core.database.entity.FailureEntryEntity
import com.ojnexus.core.database.entity.ProblemEntity
import com.ojnexus.core.database.entity.ProblemNoteEntity
import com.ojnexus.core.database.entity.ProblemTagCrossRef
import com.ojnexus.core.database.entity.ProblemTagEntity
import com.ojnexus.core.database.entity.ReviewEntity
import com.ojnexus.core.database.entity.ReviewLogEntity
import com.ojnexus.core.database.entity.TrainingSessionEntity
import com.ojnexus.core.database.entity.TrainingSessionProblemEntity
import com.ojnexus.core.database.entity.TrainingTaskEntity

/**
 * OJ NEXUS local database, version 1. Schema history is exported to app/schemas and
 * committed; destructive migration is never enabled.
 */
@Database(
    entities = [
        ProblemEntity::class,
        ProblemTagEntity::class,
        ProblemTagCrossRef::class,
        AttemptEntity::class,
        FailureEntryEntity::class,
        ProblemNoteEntity::class,
        ReviewEntity::class,
        ReviewLogEntity::class,
        TrainingTaskEntity::class,
        TrainingSessionEntity::class,
        TrainingSessionProblemEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class OjNexusDatabase : RoomDatabase() {

    abstract fun problemDao(): ProblemDao
    abstract fun attemptDao(): AttemptDao
    abstract fun failureDao(): FailureDao
    abstract fun noteDao(): NoteDao
    abstract fun reviewDao(): ReviewDao
    abstract fun taskDao(): TaskDao
    abstract fun sessionDao(): SessionDao
    abstract fun analyticsDao(): AnalyticsDao

    companion object {
        fun build(context: Context): OjNexusDatabase =
            Room.databaseBuilder(context, OjNexusDatabase::class.java, "oj-nexus.db").build()
    }
}
