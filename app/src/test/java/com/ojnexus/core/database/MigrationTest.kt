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
        ).addMigrations(OjNexusDatabase.MIGRATION_1_2).build()

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
            "INSERT INTO judge_accounts (judge, handle, canonical_handle, connected_at, updated_at, enabled) " +
                "VALUES ('codeforces', 'tourist', 'tourist', 1000, 1000, 1)",
        )
        raw.rawQuery("SELECT COUNT(*) FROM rating_changes", null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        raw.close()
    }
}
