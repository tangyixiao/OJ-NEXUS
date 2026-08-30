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
import com.ojnexus.core.database.dao.ContestProblemMarkerDao
import com.ojnexus.core.database.dao.FailureDao
import com.ojnexus.core.database.dao.JudgeAccountDao
import com.ojnexus.core.database.dao.JudgeProfileDao
import com.ojnexus.core.database.dao.KnowledgeDao
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
import com.ojnexus.core.database.entity.ContestProblemMarkerEntity
import com.ojnexus.core.database.entity.FailureEntryEntity
import com.ojnexus.core.database.entity.JudgeAccountEntity
import com.ojnexus.core.database.entity.JudgeProfileEntity
import com.ojnexus.core.database.entity.ProblemEntity
import com.ojnexus.core.database.entity.ProblemNoteEntity
import com.ojnexus.core.database.entity.ProblemKnowledgeEntity
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
 *
 * v3 (Phase 3): string contest identities, typed timestamp cursor metadata, account
 * verification/source reliability, and difficulty provenance for real multi-judge data.
 *
 * v4 (Phase 5): local-only contest problem markers for Arena focus tracking.
 *
 * v5 (Phase 6): explicit problem-to-knowledge relations for local mastery evidence.
 */
const val OJ_NEXUS_SCHEMA_VERSION = 5

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
        ContestProblemMarkerEntity::class,
        ProblemKnowledgeEntity::class,
        SyncStateEntity::class,
    ],
    version = OJ_NEXUS_SCHEMA_VERSION,
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
    abstract fun contestProblemMarkerDao(): ContestProblemMarkerDao
    abstract fun knowledgeDao(): KnowledgeDao
    abstract fun syncStateDao(): SyncStateDao

    companion object {
        const val DATABASE_NAME = "oj-nexus.db"
        const val CURRENT_SCHEMA_VERSION = OJ_NEXUS_SCHEMA_VERSION

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

        /** v2 → v3: preserve every row while removing numeric-only contest assumptions. */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `problems` ADD COLUMN `difficulty_source` " +
                        "TEXT NOT NULL DEFAULT 'UNKNOWN'",
                )
                db.execSQL(
                    "UPDATE `problems` SET `difficulty_source` = 'OFFICIAL' " +
                        "WHERE `judge` = 'codeforces'",
                )

                db.execSQL(
                    "CREATE TABLE `judge_accounts_new` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`judge` TEXT NOT NULL, `handle` TEXT NOT NULL, " +
                        "`canonical_handle` TEXT NOT NULL, `connected_at` INTEGER NOT NULL, " +
                        "`updated_at` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, " +
                        "`verification_state` TEXT NOT NULL, `source_reliability` TEXT NOT NULL)",
                )
                db.execSQL(
                    "INSERT INTO `judge_accounts_new` SELECT `id`, `judge`, `handle`, " +
                        "`canonical_handle`, `connected_at`, `updated_at`, `enabled`, " +
                        "'VERIFIED', CASE WHEN `judge` = 'codeforces' THEN 'OFFICIAL' ELSE 'COMMUNITY' END " +
                        "FROM `judge_accounts`",
                )
                db.execSQL("DROP TABLE `judge_accounts`")
                db.execSQL("ALTER TABLE `judge_accounts_new` RENAME TO `judge_accounts`")
                db.execSQL(
                    "CREATE UNIQUE INDEX `index_judge_accounts_judge_canonical_handle` " +
                        "ON `judge_accounts` (`judge`, `canonical_handle`)",
                )

                db.execSQL(
                    "CREATE TABLE `attempts_new` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`problem_id` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, " +
                        "`day_index` INTEGER NOT NULL, `verdict` TEXT NOT NULL, " +
                        "`raw_verdict` TEXT, `duration_min` INTEGER, `language` TEXT, `note` TEXT, " +
                        "`source_judge` TEXT, `external_submission_id` TEXT, `contest_id` TEXT, " +
                        "`participant_type` TEXT, `testset` TEXT, `passed_test_count` INTEGER, " +
                        "`execution_time_ms` INTEGER, `memory_bytes` INTEGER, `score` REAL, " +
                        "`code_length_bytes` INTEGER, " +
                        "FOREIGN KEY(`problem_id`) REFERENCES `problems`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL(
                    "INSERT INTO `attempts_new` SELECT `id`, `problem_id`, `timestamp`, `day_index`, " +
                        "`verdict`, `raw_verdict`, `duration_min`, `language`, `note`, `source_judge`, " +
                        "`external_submission_id`, CAST(`contest_id` AS TEXT), `participant_type`, " +
                        "`testset`, `passed_test_count`, `execution_time_ms`, `memory_bytes`, NULL, NULL " +
                        "FROM `attempts`",
                )
                db.execSQL("DROP TABLE `attempts`")
                db.execSQL("ALTER TABLE `attempts_new` RENAME TO `attempts`")
                db.execSQL("CREATE INDEX `index_attempts_problem_id` ON `attempts` (`problem_id`)")
                db.execSQL("CREATE INDEX `index_attempts_day_index` ON `attempts` (`day_index`)")
                db.execSQL("CREATE INDEX `index_attempts_timestamp` ON `attempts` (`timestamp`)")
                db.execSQL(
                    "CREATE UNIQUE INDEX `index_attempts_source_judge_external_submission_id` " +
                        "ON `attempts` (`source_judge`, `external_submission_id`)",
                )

                db.execSQL(
                    "CREATE TABLE `rating_changes_new` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `judge` TEXT NOT NULL, " +
                        "`handle` TEXT NOT NULL, `contest_id` TEXT NOT NULL, `contest_name` TEXT NOT NULL, " +
                        "`rank` INTEGER NOT NULL, `old_rating` INTEGER NOT NULL, `new_rating` INTEGER NOT NULL, " +
                        "`rating_update_time_seconds` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "INSERT INTO `rating_changes_new` SELECT `id`, `judge`, `handle`, " +
                        "CAST(`contest_id` AS TEXT), `contest_name`, `rank`, `old_rating`, `new_rating`, " +
                        "`rating_update_time_seconds` FROM `rating_changes`",
                )
                db.execSQL("DROP TABLE `rating_changes`")
                db.execSQL("ALTER TABLE `rating_changes_new` RENAME TO `rating_changes`")
                db.execSQL(
                    "CREATE UNIQUE INDEX `index_rating_changes_judge_contest_id` " +
                        "ON `rating_changes` (`judge`, `contest_id`)",
                )
                db.execSQL(
                    "CREATE INDEX `index_rating_changes_judge_rating_update_time_seconds` " +
                        "ON `rating_changes` (`judge`, `rating_update_time_seconds`)",
                )

                db.execSQL(
                    "CREATE TABLE `remote_problems_new` (" +
                        "`judge` TEXT NOT NULL, `external_id` TEXT NOT NULL, `contest_id` TEXT, " +
                        "`index` TEXT, `name` TEXT NOT NULL, `type` TEXT, `rating` INTEGER, " +
                        "`difficulty_source` TEXT NOT NULL, `points` REAL, `tags` TEXT NOT NULL, " +
                        "`solved_count` INTEGER, `updated_at` INTEGER NOT NULL, `last_seen_at` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`judge`, `external_id`))",
                )
                db.execSQL(
                    "INSERT INTO `remote_problems_new` SELECT `judge`, `external_id`, " +
                        "CAST(`contest_id` AS TEXT), `index`, `name`, `type`, `rating`, " +
                        "CASE WHEN `judge` = 'codeforces' THEN 'OFFICIAL' ELSE 'UNKNOWN' END, " +
                        "`points`, `tags`, `solved_count`, `updated_at`, `updated_at` FROM `remote_problems`",
                )
                db.execSQL("DROP TABLE `remote_problems`")
                db.execSQL("ALTER TABLE `remote_problems_new` RENAME TO `remote_problems`")
                db.execSQL(
                    "CREATE INDEX `index_remote_problems_judge_rating` " +
                        "ON `remote_problems` (`judge`, `rating`)",
                )
                db.execSQL("CREATE INDEX `index_remote_problems_name` ON `remote_problems` (`name`)")

                db.execSQL(
                    "CREATE TABLE `contests_new` (" +
                        "`judge` TEXT NOT NULL, `external_contest_id` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, `type` TEXT, `phase` TEXT NOT NULL, `frozen` INTEGER NOT NULL, " +
                        "`duration_seconds` INTEGER NOT NULL, `start_time_seconds` INTEGER, " +
                        "`relative_time_seconds` INTEGER, `prepared_by` TEXT, `updated_at` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`judge`, `external_contest_id`))",
                )
                db.execSQL(
                    "INSERT INTO `contests_new` SELECT `judge`, CAST(`external_contest_id` AS TEXT), " +
                        "`name`, `type`, `phase`, `frozen`, `duration_seconds`, `start_time_seconds`, " +
                        "`relative_time_seconds`, `prepared_by`, `updated_at` FROM `contests`",
                )
                db.execSQL("DROP TABLE `contests`")
                db.execSQL("ALTER TABLE `contests_new` RENAME TO `contests`")
                db.execSQL(
                    "CREATE INDEX `index_contests_judge_start_time_seconds` " +
                        "ON `contests` (`judge`, `start_time_seconds`)",
                )
                db.execSQL("CREATE INDEX `index_contests_judge_phase` ON `contests` (`judge`, `phase`)")

                db.execSQL("ALTER TABLE `sync_states` ADD COLUMN `account_id` INTEGER")
                db.execSQL(
                    "ALTER TABLE `sync_states` ADD COLUMN `latest_submission_time_seconds` INTEGER",
                )
                db.execSQL(
                    "UPDATE `sync_states` SET `account_id` = (SELECT `id` FROM `judge_accounts` " +
                        "WHERE `judge_accounts`.`judge` = `sync_states`.`judge` " +
                        "AND `judge_accounts`.`enabled` = 1 LIMIT 1)",
                )
            }
        }

        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `contest_problem_markers` (" +
                        "`judge` TEXT NOT NULL, `contest_id` TEXT NOT NULL, " +
                        "`problem_external_id` TEXT NOT NULL, `marker` TEXT NOT NULL, " +
                        "`updated_at` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`judge`, `contest_id`, `problem_external_id`))",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_contest_problem_markers_judge_contest_id` " +
                        "ON `contest_problem_markers` (`judge`, `contest_id`)",
                )
            }
        }

        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `problem_knowledge` (" +
                        "`problem_id` INTEGER NOT NULL, `knowledge_area` TEXT NOT NULL, " +
                        "PRIMARY KEY(`problem_id`, `knowledge_area`), " +
                        "FOREIGN KEY(`problem_id`) REFERENCES `problems`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_problem_knowledge_knowledge_area` " +
                        "ON `problem_knowledge` (`knowledge_area`)",
                )
            }
        }

        fun build(context: Context): OjNexusDatabase =
            Room.databaseBuilder(context, OjNexusDatabase::class.java, DATABASE_NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
    }
}
