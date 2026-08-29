package com.ojnexus.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ojnexus.core.database.dao.AnalyticsDao
import com.ojnexus.core.database.dao.AttemptDao
import com.ojnexus.core.database.dao.ContestDao
import com.ojnexus.core.database.dao.FailureDao
import com.ojnexus.core.database.dao.JudgeAccountDao
import com.ojnexus.core.database.dao.JudgeProfileDao
import com.ojnexus.core.database.dao.NoteDao
import com.ojnexus.core.database.dao.ProblemDao
import com.ojnexus.core.database.dao.RatingChangeDao
import com.ojnexus.core.database.dao.RemoteProblemDao
import com.ojnexus.core.database.dao.ReviewDao
import com.ojnexus.core.database.dao.SessionDao
import com.ojnexus.core.database.dao.SyncStateDao
import com.ojnexus.core.database.dao.TaskDao
import com.ojnexus.core.database.entity.AttemptEntity
import com.ojnexus.core.database.entity.ContestEntity
import com.ojnexus.core.database.entity.FailureEntryEntity
import com.ojnexus.core.database.entity.JudgeAccountEntity
import com.ojnexus.core.database.entity.JudgeProfileEntity
import com.ojnexus.core.database.entity.ProblemEntity
import com.ojnexus.core.database.entity.ProblemNoteEntity
import com.ojnexus.core.database.entity.ProblemTagCrossRef
import com.ojnexus.core.database.entity.ProblemTagEntity
import com.ojnexus.core.database.entity.RatingChangeEntity
import com.ojnexus.core.database.entity.RemoteProblemEntity
import com.ojnexus.core.database.entity.ReviewEntity
import com.ojnexus.core.database.entity.ReviewLogEntity
import com.ojnexus.core.database.entity.SyncStateEntity
import com.ojnexus.core.database.entity.TrainingSessionEntity
import com.ojnexus.core.database.entity.TrainingSessionProblemEntity
import com.ojnexus.core.database.entity.TrainingTaskEntity

/**
 * OJ NEXUS local database. Schema history is exported to app/schemas and committed;
 * destructive migration is never enabled.
 *
 * v2 (Phase 2): judge accounts, profile snapshots, rating history, remote problem catalog,
 * contests, sync state — plus remote-origin columns on `attempts` for idempotent
 * submission sync. Migration 1→2 preserves every Phase 1 row (all new `attempts` columns
 * are nullable; new tables are created fresh).
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
        JudgeAccountEntity::class,
        JudgeProfileEntity::class,
        RatingChangeEntity::class,
        RemoteProblemEntity::class,
        ContestEntity::class,
        SyncStateEntity::class,
    ],
    version = 2,
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
    abstract fun judgeAccountDao(): JudgeAccountDao
    abstract fun judgeProfileDao(): JudgeProfileDao
    abstract fun ratingChangeDao(): RatingChangeDao
    abstract fun remoteProblemDao(): RemoteProblemDao
    abstract fun contestDao(): ContestDao
    abstract fun syncStateDao(): SyncStateDao

    companion object {

        /**
         * v1 → v2: extend `attempts` with nullable remote-origin columns (no NOT NULL
         * without default, so no data loss), add the remote-idempotency unique index, and
         * create the six new tables. No table rebuild is needed — nothing is dropped or
         * renamed.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `attempts` ADD COLUMN `source_judge` TEXT")
                db.execSQL("ALTER TABLE `attempts` ADD COLUMN `external_submission_id` TEXT")
                db.execSQL("ALTER TABLE `attempts` ADD COLUMN `contest_id` INTEGER")
                db.execSQL("ALTER TABLE `attempts` ADD COLUMN `participant_type` TEXT")
                db.execSQL("ALTER TABLE `attempts` ADD COLUMN `testset` TEXT")
                db.execSQL("ALTER TABLE `attempts` ADD COLUMN `passed_test_count` INTEGER")
                db.execSQL("ALTER TABLE `attempts` ADD COLUMN `execution_time_ms` INTEGER")
                db.execSQL("ALTER TABLE `attempts` ADD COLUMN `memory_bytes` INTEGER")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "`index_attempts_source_judge_external_submission_id` " +
                        "ON `attempts` (`source_judge`, `external_submission_id`)",
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `judge_accounts` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`judge` TEXT NOT NULL, " +
                        "`handle` TEXT NOT NULL, " +
                        "`canonical_handle` TEXT NOT NULL, " +
                        "`connected_at` INTEGER NOT NULL, " +
                        "`updated_at` INTEGER NOT NULL, " +
                        "`enabled` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "`index_judge_accounts_judge_canonical_handle` " +
                        "ON `judge_accounts` (`judge`, `canonical_handle`)",
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `judge_profiles` (" +
                        "`judge` TEXT NOT NULL, " +
                        "`handle` TEXT NOT NULL, " +
                        "`first_name` TEXT, " +
                        "`last_name` TEXT, " +
                        "`country` TEXT, " +
                        "`city` TEXT, " +
                        "`organization` TEXT, " +
                        "`contribution` INTEGER, " +
                        "`rating` INTEGER, " +
                        "`max_rating` INTEGER, " +
                        "`rank` TEXT, " +
                        "`max_rank` TEXT, " +
                        "`friend_of_count` INTEGER, " +
                        "`registration_time_seconds` INTEGER, " +
                        "`last_online_time_seconds` INTEGER, " +
                        "`avatar` TEXT, " +
                        "`title_photo` TEXT, " +
                        "`updated_at` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`judge`))",
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `rating_changes` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`judge` TEXT NOT NULL, " +
                        "`handle` TEXT NOT NULL, " +
                        "`contest_id` INTEGER NOT NULL, " +
                        "`contest_name` TEXT NOT NULL, " +
                        "`rank` INTEGER NOT NULL, " +
                        "`old_rating` INTEGER NOT NULL, " +
                        "`new_rating` INTEGER NOT NULL, " +
                        "`rating_update_time_seconds` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "`index_rating_changes_judge_contest_id` " +
                        "ON `rating_changes` (`judge`, `contest_id`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                        "`index_rating_changes_judge_rating_update_time_seconds` " +
                        "ON `rating_changes` (`judge`, `rating_update_time_seconds`)",
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `remote_problems` (" +
                        "`judge` TEXT NOT NULL, " +
                        "`external_id` TEXT NOT NULL, " +
                        "`contest_id` INTEGER, " +
                        "`index` TEXT, " +
                        "`name` TEXT NOT NULL, " +
                        "`type` TEXT, " +
                        "`rating` INTEGER, " +
                        "`points` REAL, " +
                        "`tags` TEXT NOT NULL, " +
                        "`solved_count` INTEGER, " +
                        "`updated_at` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`judge`, `external_id`))",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_remote_problems_judge_rating` " +
                        "ON `remote_problems` (`judge`, `rating`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_remote_problems_name` " +
                        "ON `remote_problems` (`name`)",
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `contests` (" +
                        "`judge` TEXT NOT NULL, " +
                        "`external_contest_id` INTEGER NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`type` TEXT, " +
                        "`phase` TEXT NOT NULL, " +
                        "`frozen` INTEGER NOT NULL, " +
                        "`duration_seconds` INTEGER NOT NULL, " +
                        "`start_time_seconds` INTEGER, " +
                        "`relative_time_seconds` INTEGER, " +
                        "`prepared_by` TEXT, " +
                        "`updated_at` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`judge`, `external_contest_id`))",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_contests_judge_start_time_seconds` " +
                        "ON `contests` (`judge`, `start_time_seconds`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_contests_judge_phase` " +
                        "ON `contests` (`judge`, `phase`)",
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sync_states` (" +
                        "`judge` TEXT NOT NULL, " +
                        "`state` TEXT NOT NULL, " +
                        "`started_at` INTEGER, " +
                        "`finished_at` INTEGER, " +
                        "`last_successful_sync_at` INTEGER, " +
                        "`last_error_type` TEXT, " +
                        "`last_error_message` TEXT, " +
                        "`current_stage` TEXT, " +
                        "`submissions_imported` INTEGER, " +
                        "`profile_synced_at` INTEGER, " +
                        "`rating_synced_at` INTEGER, " +
                        "`submissions_synced_at` INTEGER, " +
                        "`contests_synced_at` INTEGER, " +
                        "`problemset_synced_at` INTEGER, " +
                        "`latest_external_submission_id` INTEGER, " +
                        "PRIMARY KEY(`judge`))",
                )
            }
        }

        fun build(context: Context): OjNexusDatabase =
            Room.databaseBuilder(context, OjNexusDatabase::class.java, "oj-nexus.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
