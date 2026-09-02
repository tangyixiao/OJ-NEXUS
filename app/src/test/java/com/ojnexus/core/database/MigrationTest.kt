package com.ojnexus.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Migration 1→2 test on the JVM (Robolectric, no emulator, no schema assets).
 *
 * Why not MigrationTestHelper? Its schema lookup reads instrumentation assets, which AGP 9
 * does not feed to Robolectric unit tests. The equivalent validation here:
 *
 *  1. build a v1 database by executing the v1 schema DDL straight from the committed
 *     `schemas/…/1.json` (the same source of truth MigrationTestHelper uses),
 *  2. insert Phase 1 rows,
 *  3. run [OjNexusDatabase.MIGRATION_1_2] on the file,
 *  4. open the file with Room v2 — Room validates the migrated schema against its v2
 *     expectations on open and throws if anything mismatches,
 *  5. assert the Phase 1 data survived and v2 columns/tables are usable.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {

    private val dbName = "migration-test.db"

    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        context.getDatabasePath(dbName).delete()
        File(context.getDatabasePath(dbName).path + "-wal").delete()
        File(context.getDatabasePath(dbName).path + "-shm").delete()
    }

    private fun schemaFile(version: Int): File {
        val candidates = listOf(
            File("schemas/com.ojnexus.core.database.OjNexusDatabase/$version.json"),
            File("app/schemas/com.ojnexus.core.database.OjNexusDatabase/$version.json"),
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("schema json not found; tried $candidates (cwd=${File(".").absolutePath})")
    }

    /** Creates a v1 database file by executing the committed v1 schema DDL. */
    private fun createV1Database() {
        val root = Json.parseToJsonElement(schemaFile(1).readText()).jsonObject
        val database = root["database"]!!.jsonObject
        assertEquals(1, database["version"]!!.jsonPrimitive.content.toInt())

        // Framework SQLite with foreign keys off during setup, like Room's helper.
        val setup = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(
            context.getDatabasePath(dbName).absolutePath,
            null,
        )
        setup.execSQL("PRAGMA foreign_keys = OFF")
        database["entities"]!!.jsonArray.forEach { element ->
            val entity = element.jsonObject
            val createSql = entity["createSql"]!!.jsonPrimitive.content
                .replace("\${TABLE_NAME}", entity["tableName"]!!.jsonPrimitive.content)
            setup.execSQL(createSql)
            entity["indices"]?.jsonArray?.forEach { indexElement ->
                val index = indexElement.jsonObject
                setup.execSQL(
                    index["createSql"]!!.jsonPrimitive.content
                        .replace("\${TABLE_NAME}", entity["tableName"]!!.jsonPrimitive.content),
                )
            }
        }
        database["setupQueries"]?.jsonArray?.forEach { queryElement ->
            setup.execSQL(queryElement.jsonPrimitive.content)
        }
        setup.execSQL("INSERT INTO room_master_table (id, identity_hash) VALUES(0, '${database["identityHash"]!!.jsonPrimitive.content}')")
        // Mark the file as a real v1 database, otherwise Room treats version 0 as a fresh
        // install and runs the v2 create-tables path instead of MIGRATION_1_2.
        setup.version = 1
        setup.close()
    }

    /** Creates an empty database at an exported schema version for migration tests. */
    private fun createDatabaseFromSchema(version: Int) {
        val root = Json.parseToJsonElement(schemaFile(version).readText()).jsonObject
        val database = root["database"]!!.jsonObject
        assertEquals(version, database["version"]!!.jsonPrimitive.content.toInt())
        val setup = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(
            context.getDatabasePath(dbName).absolutePath,
            null,
        )
        setup.execSQL("PRAGMA foreign_keys = OFF")
        database["entities"]!!.jsonArray.forEach { element ->
            val entity = element.jsonObject
            val tableName = entity["tableName"]!!.jsonPrimitive.content
            setup.execSQL(entity["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", tableName))
            entity["indices"]?.jsonArray?.forEach { indexElement ->
                setup.execSQL(
                    indexElement.jsonObject["createSql"]!!.jsonPrimitive.content
                        .replace("\${TABLE_NAME}", tableName),
                )
            }
        }
        database["setupQueries"]?.jsonArray?.forEach { setup.execSQL(it.jsonPrimitive.content) }
        setup.execSQL(
            "INSERT INTO room_master_table (id, identity_hash) VALUES(0, " +
                "'${database["identityHash"]!!.jsonPrimitive.content}')",
        )
        setup.version = version
        setup.close()
    }

    @Test
    fun `migrate 1 to 2 preserves all phase 1 data`() {
        createV1Database()

        // Insert Phase 1 rows into the v1 file database (raw SQL, v1 columns only).
        val v1 = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(
            context.getDatabasePath(dbName).absolutePath,
            null,
        )
        v1.execSQL(
            "INSERT INTO problems (judge, external_id, title, difficulty, created_at, updated_at, " +
                "first_solved_at, last_attempt_at, attempt_count, solved, favorite, source_url) " +
                "VALUES ('codeforces', '2134C', 'Yet Another Array Query', 1700, 100, 100, " +
                "NULL, NULL, 0, 0, 1, NULL)",
        )
        v1.execSQL(
            "INSERT INTO attempts (problem_id, timestamp, day_index, verdict, raw_verdict, " +
                "duration_min, language, note) " +
                "VALUES (1, 500, 5, 'AC', 'OK', 12, 'Kotlin', 'my note')",
        )
        v1.execSQL(
            "INSERT INTO reviews (problem_id, stage, due_at, due_day_index, created_at) " +
                "VALUES (1, 1, 900, 9, 100)",
        )
        v1.execSQL(
            "INSERT INTO training_sessions (type, state, started_at, total_paused_ms, " +
                "target_duration_min, day_index) " +
                "VALUES ('PRACTICE', 'FINISHED', 600, 60, 30, 6)",
        )
        v1.close()

        // Open with Room v2: the migration runs, then Room validates the migrated schema
        // against its own v2 expectations (throws on any mismatch).
        val db = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            OjNexusDatabase::class.java,
            dbName,
        ).addMigrations(
            OjNexusDatabase.MIGRATION_1_2,
            OjNexusDatabase.MIGRATION_2_3,
            OjNexusDatabase.MIGRATION_3_4,
            OjNexusDatabase.MIGRATION_4_5,
            OjNexusDatabase.MIGRATION_5_6,
            OjNexusDatabase.MIGRATION_6_7,
            OjNexusDatabase.MIGRATION_7_8,
            OjNexusDatabase.MIGRATION_8_9,
            OjNexusDatabase.MIGRATION_9_10,
            OjNexusDatabase.MIGRATION_10_11,
        ).build()

        try {
            val dao = db.problemDao()
            val library = kotlinx.coroutines.runBlocking { dao.findLibrary() }
            assertEquals(1, library.size)
            val problem = library.single().problem
            assertEquals("2134C", problem.externalId)
            assertTrue(problem.favorite)
        } finally {
            db.close()
        }

        // New v2 columns default to NULL for the pre-existing manual attempt, and v2
        // structures are writable.
        val raw = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(
            context.getDatabasePath(dbName).absolutePath,
            null,
        )
        raw.rawQuery(
            "SELECT verdict, raw_verdict, note, source_judge, external_submission_id FROM attempts",
            null,
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("AC", cursor.getString(0))
            assertEquals("OK", cursor.getString(1))
            assertEquals("my note", cursor.getString(2))
            assertTrue(cursor.isNull(3))
            assertTrue(cursor.isNull(4))
        }
        raw.execSQL(
            "INSERT INTO judge_accounts (judge, handle, canonical_handle, connected_at, updated_at, enabled, " +
                "verification_state, source_reliability) " +
                "VALUES ('codeforces', 'tourist', 'tourist', 1000, 1000, 1, 'VERIFIED', 'OFFICIAL')",
        )
        raw.rawQuery("SELECT COUNT(*) FROM rating_changes", null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        raw.close()
    }

    @Test
    fun `migrate 2 to 3 preserves codeforces and local data while generalizing ids`() {
        createDatabaseFromSchema(2)
        val v2 = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(
            context.getDatabasePath(dbName).absolutePath,
            null,
        )
        v2.execSQL(
            "INSERT INTO problems (id, judge, external_id, title, difficulty, created_at, updated_at, " +
                "attempt_count, solved, favorite) VALUES (1, 'codeforces', '2134C', 'Keep', 1700, 1, 1, 0, 0, 1)",
        )
        v2.execSQL(
            "INSERT INTO problem_notes (problem_id, key_insight, implementation_notes, complexity, general, updated_at) " +
                "VALUES (1, 'note', '', '', '', 2)",
        )
        v2.execSQL(
            "INSERT INTO attempts (problem_id, timestamp, day_index, verdict, source_judge, " +
                "external_submission_id, contest_id) VALUES (1, 3, 0, 'AC', 'codeforces', '99', 2134)",
        )
        v2.execSQL(
            "INSERT INTO judge_accounts (id, judge, handle, canonical_handle, connected_at, updated_at, enabled) " +
                "VALUES (7, 'codeforces', 'Tourist', 'tourist', 4, 4, 1)",
        )
        v2.execSQL(
            "INSERT INTO remote_problems (judge, external_id, contest_id, `index`, name, rating, tags, updated_at) " +
                "VALUES ('codeforces', '2134C', 2134, 'C', 'Keep', 1700, '', 5)",
        )
        v2.execSQL(
            "INSERT INTO contests (judge, external_contest_id, name, phase, frozen, duration_seconds, updated_at) " +
                "VALUES ('codeforces', 2134, 'Round', 'FINISHED', 0, 7200, 6)",
        )
        v2.execSQL(
            "INSERT INTO rating_changes (judge, handle, contest_id, contest_name, rank, old_rating, new_rating, " +
                "rating_update_time_seconds) VALUES ('codeforces', 'tourist', 2134, 'Round', 1, 3900, 3910, 7)",
        )
        v2.execSQL(
            "INSERT INTO sync_states (judge, state, latest_external_submission_id) " +
                "VALUES ('codeforces', 'SUCCESS', 99)",
        )
        v2.close()

        val db = Room.databaseBuilder(context, OjNexusDatabase::class.java, dbName)
            .addMigrations(
                OjNexusDatabase.MIGRATION_2_3,
                OjNexusDatabase.MIGRATION_3_4,
                OjNexusDatabase.MIGRATION_4_5,
                OjNexusDatabase.MIGRATION_5_6,
                OjNexusDatabase.MIGRATION_6_7,
                OjNexusDatabase.MIGRATION_7_8,
                OjNexusDatabase.MIGRATION_8_9,
                OjNexusDatabase.MIGRATION_9_10,
                OjNexusDatabase.MIGRATION_10_11,
            )
            .build()
        db.openHelper.writableDatabase
        db.close()

        val raw = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(
            context.getDatabasePath(dbName).absolutePath,
            null,
        )
        raw.rawQuery(
            "SELECT verification_state, source_reliability FROM judge_accounts WHERE id = 7",
            null,
        ).use {
            assertTrue(it.moveToFirst())
            assertEquals("VERIFIED", it.getString(0))
            assertEquals("OFFICIAL", it.getString(1))
        }
        raw.rawQuery(
            "SELECT contest_id, difficulty_source, last_seen_at FROM remote_problems WHERE external_id = '2134C'",
            null,
        ).use {
            assertTrue(it.moveToFirst())
            assertEquals("2134", it.getString(0))
            assertEquals("OFFICIAL", it.getString(1))
            assertEquals(5L, it.getLong(2))
        }
        raw.rawQuery("SELECT external_contest_id FROM contests", null).use {
            assertTrue(it.moveToFirst())
            assertEquals("2134", it.getString(0))
        }
        raw.rawQuery("SELECT contest_id FROM attempts", null).use {
            assertTrue(it.moveToFirst())
            assertEquals("2134", it.getString(0))
        }
        raw.rawQuery(
            "SELECT account_id, latest_external_submission_id, latest_submission_time_seconds FROM sync_states",
            null,
        ).use {
            assertTrue(it.moveToFirst())
            assertEquals(7L, it.getLong(0))
            assertEquals(99L, it.getLong(1))
            assertTrue(it.isNull(2))
        }
        raw.rawQuery("SELECT key_insight FROM problem_notes WHERE problem_id = 1", null).use {
            assertTrue(it.moveToFirst())
            assertEquals("note", it.getString(0))
        }
        raw.close()
    }

    @Test
    fun `migrate 1 to 3 follows the complete non destructive path`() {
        createV1Database()
        val v1 = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(
            context.getDatabasePath(dbName).absolutePath,
            null,
        )
        v1.execSQL(
            "INSERT INTO problems (judge, external_id, title, created_at, updated_at, attempt_count, solved, favorite) " +
                "VALUES ('local', 'legacy', 'Legacy', 1, 1, 0, 0, 1)",
        )
        v1.close()

        val db = Room.databaseBuilder(context, OjNexusDatabase::class.java, dbName)
            .addMigrations(
                OjNexusDatabase.MIGRATION_1_2,
                OjNexusDatabase.MIGRATION_2_3,
                OjNexusDatabase.MIGRATION_3_4,
                OjNexusDatabase.MIGRATION_4_5,
                OjNexusDatabase.MIGRATION_5_6,
                OjNexusDatabase.MIGRATION_6_7,
                OjNexusDatabase.MIGRATION_7_8,
                OjNexusDatabase.MIGRATION_8_9,
                OjNexusDatabase.MIGRATION_9_10,
                OjNexusDatabase.MIGRATION_10_11,
            )
            .build()
        try {
            assertEquals("Legacy", kotlinx.coroutines.runBlocking { db.problemDao().findLibrary() }.single().problem.title)
        } finally {
            db.close()
        }
    }

    @Test
    fun `migrate 8 to 9 preserves existing rows and adds remote detail cache`() {
        createDatabaseFromSchema(8)
        val v8 = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(
            context.getDatabasePath(dbName).absolutePath,
            null,
        )
        v8.execSQL(
            "INSERT INTO problems (id, judge, external_id, title, created_at, updated_at, " +
                "attempt_count, solved, favorite) VALUES (1, 'local', 'keep', 'Keep', 1, 1, 0, 0, 1)",
        )
        v8.execSQL(
            "INSERT INTO submission_jobs (judge, request_id, kind, language, status, created_at, updated_at) " +
                "VALUES ('luogu', 'request-keep', 'PROBLEM', 'cxx/14/gcc', 'PENDING', 2, 2)",
        )
        v8.close()

        val db = Room.databaseBuilder(context, OjNexusDatabase::class.java, dbName)
            .addMigrations(OjNexusDatabase.MIGRATION_8_9, OjNexusDatabase.MIGRATION_9_10, OjNexusDatabase.MIGRATION_10_11)
            .build()
        try {
            val detail = com.ojnexus.core.database.entity.RemoteProblemDetailEntity(
                judge = "luogu",
                externalId = "P1001",
                title = "A+B",
                difficulty = 1,
                tagsJson = "[1]",
                totalSubmit = 2,
                totalAccepted = 1,
                background = "",
                description = "desc",
                inputFormat = "in",
                outputFormat = "out",
                hint = "",
                samplesJson = "[]",
                timeLimitMs = 1000,
                memoryLimitMb = 128,
                updatedAt = 42,
            )
            kotlinx.coroutines.runBlocking { db.remoteProblemDetailDao().upsert(detail) }
            assertEquals(
                "A+B",
                kotlinx.coroutines.runBlocking {
                    db.remoteProblemDetailDao().findByKey("luogu", "P1001")?.title
                },
            )
        } finally {
            db.close()
        }

        val raw = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(
            context.getDatabasePath(dbName).absolutePath,
            null,
        )
        raw.rawQuery("SELECT title FROM problems WHERE id = 1", null).use {
            assertTrue(it.moveToFirst())
            assertEquals("Keep", it.getString(0))
        }
        raw.rawQuery("SELECT request_id FROM submission_jobs", null).use {
            assertTrue(it.moveToFirst())
            assertEquals("request-keep", it.getString(0))
        }
        raw.close()
    }

    @Test
    fun `migrate 5 to 6 adds nullable Luogu profile and rating facts`() {
        createDatabaseFromSchema(5)

        val db = Room.databaseBuilder(context, OjNexusDatabase::class.java, dbName)
            .addMigrations(OjNexusDatabase.MIGRATION_5_6, OjNexusDatabase.MIGRATION_6_7, OjNexusDatabase.MIGRATION_7_8, OjNexusDatabase.MIGRATION_8_9, OjNexusDatabase.MIGRATION_9_10, OjNexusDatabase.MIGRATION_10_11)
            .build()
        try {
            db.openHelper.writableDatabase
        } finally {
            db.close()
        }

        val raw = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(
            context.getDatabasePath(dbName).absolutePath,
            null,
        )
        fun columns(table: String): Set<String> = buildSet {
            raw.rawQuery("PRAGMA table_info(`$table`)", null).use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) add(cursor.getString(nameIndex))
            }
        }
        assertTrue(columns("judge_profiles").containsAll(setOf("introduction", "ranking", "badge")))
        assertTrue(columns("rating_changes").containsAll(setOf("old_rating", "rank")))
        raw.close()
    }

    @Test
    fun `migrate 6 to 7 adds local submission jobs without source columns`() {
        createDatabaseFromSchema(6)

        val db = Room.databaseBuilder(context, OjNexusDatabase::class.java, dbName)
            .addMigrations(OjNexusDatabase.MIGRATION_6_7, OjNexusDatabase.MIGRATION_7_8, OjNexusDatabase.MIGRATION_8_9, OjNexusDatabase.MIGRATION_9_10, OjNexusDatabase.MIGRATION_10_11)
            .build()
        try {
            db.openHelper.writableDatabase
        } finally {
            db.close()
        }

        val raw = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(
            context.getDatabasePath(dbName).absolutePath,
            null,
        )
        val columns = buildSet {
            raw.rawQuery("PRAGMA table_info(`submission_jobs`)", null).use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) add(cursor.getString(nameIndex))
            }
        }
        assertTrue(columns.containsAll(setOf("request_id", "kind", "status", "created_at", "updated_at")))
        assertTrue("code" !in columns)
        assertTrue("input" !in columns)
        raw.close()
    }

    @Test
    fun `migrate 7 to 8 preserves submission jobs and adds evaluation details`() {
        createDatabaseFromSchema(7)
        val v7 = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(
            context.getDatabasePath(dbName).absolutePath,
            null,
        )
        v7.execSQL(
            "INSERT INTO submission_jobs (judge, request_id, track_id, kind, pid, language, status, " +
                "judge_status, score, created_at, updated_at, last_error_type) " +
                "VALUES ('luogu', 'req-7', NULL, 'PROBLEM', 'P1001', 'cpp', 'PENDING', NULL, NULL, 10, 20, NULL)",
        )
        v7.close()

        val db = Room.databaseBuilder(context, OjNexusDatabase::class.java, dbName)
            .addMigrations(OjNexusDatabase.MIGRATION_7_8, OjNexusDatabase.MIGRATION_8_9, OjNexusDatabase.MIGRATION_9_10, OjNexusDatabase.MIGRATION_10_11)
            .build()
        try {
            db.openHelper.writableDatabase
        } finally {
            db.close()
        }

        val raw = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(
            context.getDatabasePath(dbName).absolutePath,
            null,
        )
        val columns = buildSet {
            raw.rawQuery("PRAGMA table_info(`submission_jobs`)", null).use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) add(cursor.getString(nameIndex))
            }
        }
        assertTrue(
            columns.containsAll(
                setOf("compile_success", "compile_message", "output", "exit_code", "execution_time_ms", "memory_kib"),
            ),
        )
        raw.rawQuery(
            "SELECT request_id, status, created_at, compile_success, output FROM submission_jobs",
            null,
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("req-7", cursor.getString(0))
            assertEquals("PENDING", cursor.getString(1))
            assertEquals(10L, cursor.getLong(2))
            assertTrue(cursor.isNull(3))
            assertTrue(cursor.isNull(4))
        }
        raw.close()
    }

    @Test
    fun `migrate 9 to 10 adds workspace drafts without losing submission jobs`() {
        createDatabaseFromSchema(9)
        val v9 = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(
            context.getDatabasePath(dbName).absolutePath,
            null,
        )
        v9.execSQL(
            "INSERT INTO submission_jobs (judge, request_id, track_id, kind, pid, language, status, " +
                "judge_status, score, created_at, updated_at, last_error_type, compile_success, " +
                "compile_message, output, exit_code, execution_time_ms, memory_kib) VALUES " +
                "('luogu', 'req-v9', NULL, 'PROBLEM', 'P1001', 'cxx/14/gcc', 'PENDING', NULL, " +
                "NULL, 10, 20, NULL, NULL, NULL, NULL, NULL, NULL, NULL)",
        )
        v9.close()

        val db = Room.databaseBuilder(context, OjNexusDatabase::class.java, dbName)
            .addMigrations(OjNexusDatabase.MIGRATION_9_10, OjNexusDatabase.MIGRATION_10_11)
            .build()
        try {
            db.openHelper.writableDatabase
        } finally {
            db.close()
        }

        val raw = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(
            context.getDatabasePath(dbName).absolutePath,
            null,
        )
        val columns = buildSet {
            raw.rawQuery("PRAGMA table_info(`workspace_drafts`)", null).use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) add(cursor.getString(nameIndex))
            }
        }
        assertTrue(
            columns.containsAll(setOf("judge", "pid", "code", "input", "language", "o2", "updated_at")),
        )
        raw.rawQuery("SELECT request_id FROM submission_jobs", null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("req-v9", cursor.getString(0))
        }
        raw.close()
    }

    @Test
    fun `migrate 10 to 11 adds nullable submission title without losing history`() {
        createDatabaseFromSchema(10)
        val v10 = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(
            context.getDatabasePath(dbName).absolutePath,
            null,
        )
        v10.execSQL(
            "INSERT INTO submission_jobs (judge, request_id, track_id, kind, pid, language, status, " +
                "judge_status, score, created_at, updated_at, last_error_type, compile_success, " +
                "compile_message, output, exit_code, execution_time_ms, memory_kib) VALUES " +
                "('luogu', 'req-v10', NULL, 'PROBLEM', 'P1001', 'cxx/14/gcc', 'PENDING', NULL, " +
                "NULL, 10, 20, NULL, NULL, NULL, NULL, NULL, NULL, NULL)",
        )
        v10.close()

        val db = Room.databaseBuilder(context, OjNexusDatabase::class.java, dbName)
            .addMigrations(OjNexusDatabase.MIGRATION_10_11)
            .build()
        try {
            assertEquals("req-v10", kotlinx.coroutines.runBlocking {
                db.submissionJobDao().findByRequestId("req-v10")?.requestId
            })
        } finally {
            db.close()
        }

        val raw = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(
            context.getDatabasePath(dbName).absolutePath,
            null,
        )
        raw.rawQuery("SELECT title FROM submission_jobs WHERE request_id = 'req-v10'", null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.isNull(0))
        }
        raw.close()
    }
}
