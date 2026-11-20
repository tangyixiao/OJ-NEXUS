package com.ojnexus.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Phase 106 Room index audit — proves the v13→v14 migration is additive and that the two
 * new composite indices are actually picked up by EXPLAIN QUERY PLAN for the audited
 * production queries. See docs/superpowers/specs/2026-10-18-room-index-audit.md.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomIndexAuditTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        context.deleteDatabase("index-audit-${System.nanoTime()}.db")
    }

    private fun open(name: String) = Room.databaseBuilder(context, OjNexusDatabase::class.java, name)
        .setJournalMode(androidx.room.RoomDatabase.JournalMode.TRUNCATE)
        .allowMainThreadQueries()
        .build()

    private fun schemaFile(version: Int): File {
        val candidates = listOf(
            File("schemas/com.ojnexus.core.database.OjNexusDatabase/$version.json"),
            File("app/schemas/com.ojnexus.core.database.OjNexusDatabase/$version.json"),
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("schema json not found; tried $candidates (cwd=${File(".").absolutePath})")
    }

    private fun plan(database: OjNexusDatabase, sql: String, vararg args: Any): String {
        // SupportSQLiteDatabase.query(String, Array<Any?>) — vararg spread is not offered.
        val cursor = database.openHelper.writableDatabase.query("EXPLAIN QUERY PLAN $sql", args)
        cursor.use { c ->
            val details = StringBuilder()
            while (c.moveToNext()) {
                details.append(c.getString(3)).append(" | ")
            }
            return details.toString()
        }
    }

    @Test
    fun `fresh v17 schema carries both audited composite indices`() {
        val database = open("audit-fresh-${System.nanoTime()}.db")
        try {
            val plans = plan(
                database,
                "SELECT * FROM submission_jobs WHERE judge = 'luogu' AND status = 'PENDING' " +
                    "ORDER BY updated_at ASC, id ASC LIMIT 10",
            )
            assertTrue("plan was: $plans", plans.contains("index_submission_jobs_judge_status_updated_at"))

            val remotePlan = plan(
                database,
                "SELECT * FROM remote_problems WHERE judge = 'codeforces' ORDER BY external_id",
            )
            assertTrue("plan was: $remotePlan", remotePlan.contains("index_remote_problems_judge_external_id"))
        } finally {
            database.close()
        }
    }

    @Test
    fun `migrate v13 to latest keeps rows and adds indices non-destructively`() {
        val name = "audit-migrate-${System.nanoTime()}.db"
        // Build a faithful v13 database from the committed exported schema (the replay-
        // current-DDL trick this test used before phase 137 breaks the moment a migration
        // adds a column: the replayed schema already contains it, and a plain
        // ALTER TABLE ADD COLUMN is not idempotent). The exported 13.json is Room's own
        // v13 definition — column additions in later migrations stay safe.
        val root = Json.parseToJsonElement(schemaFile(13).readText()).jsonObject
        val database = root["database"]!!.jsonObject
        assertEquals(13, database["version"]!!.jsonPrimitive.content.toInt())
        val db = context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null)
        db.execSQL("PRAGMA foreign_keys = OFF")
        database["entities"]!!.jsonArray.forEach { element ->
            val entity = element.jsonObject
            val tableName = entity["tableName"]!!.jsonPrimitive.content
            db.execSQL(entity["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", tableName))
            entity["indices"]?.jsonArray?.forEach { indexElement ->
                db.execSQL(
                    indexElement.jsonObject["createSql"]!!.jsonPrimitive.content
                        .replace("\${TABLE_NAME}", tableName),
                )
            }
        }
        database["setupQueries"]?.jsonArray?.forEach { db.execSQL(it.jsonPrimitive.content) }
        db.execSQL(
            "INSERT INTO room_master_table (id, identity_hash) VALUES(0, " +
                "'${database["identityHash"]!!.jsonPrimitive.content}')",
        )
        db.execSQL(
            "INSERT INTO submission_jobs (judge, request_id, kind, language, status, created_at, updated_at) " +
                "VALUES ('luogu', 'req-1', 'PROBLEM', 'C++14', 'PENDING', 1, 1)",
        )
        db.execSQL(
            "INSERT INTO remote_problems (judge, external_id, name, difficulty_source, tags, updated_at, last_seen_at) " +
                "VALUES ('codeforces', '1234A', 'Sample', 'OFFICIAL', '[]', 1, 1)",
        )
        db.version = 13
        db.close()

        val migrated = Room.databaseBuilder(context, OjNexusDatabase::class.java, name)
            .addMigrations(*ALL_MIGRATIONS)
            .setJournalMode(androidx.room.RoomDatabase.JournalMode.TRUNCATE)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals(17, migrated.openHelper.writableDatabase.version)
            // Row survived the migration: read the seeded job back through the raw store.
            val jobs = mutableListOf<String>()
            migrated.openHelper.writableDatabase.query(
                "SELECT request_id FROM submission_jobs WHERE judge = 'luogu'",
                emptyArray(),
            ).use { c ->
                while (c.moveToNext()) {
                    jobs.add(c.getString(0))
                }
            }
            assertEquals(listOf("req-1"), jobs)
            assertTrue(
                plan(
                    migrated,
                    "SELECT * FROM submission_jobs WHERE judge = 'luogu' AND status = 'PENDING' " +
                        "ORDER BY updated_at ASC, id ASC LIMIT 10",
                ).contains("index_submission_jobs_judge_status_updated_at"),
            )
            assertTrue(
                plan(
                    migrated,
                    "SELECT * FROM remote_problems WHERE judge = 'codeforces' ORDER BY external_id",
                ).contains("index_remote_problems_judge_external_id"),
            )
        } finally {
            migrated.close()
        }
    }

    private companion object {
        val ALL_MIGRATIONS = arrayOf(
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
            OjNexusDatabase.MIGRATION_11_12,
            OjNexusDatabase.MIGRATION_12_13,
            OjNexusDatabase.MIGRATION_13_14,
            OjNexusDatabase.MIGRATION_14_15,
            OjNexusDatabase.MIGRATION_15_16,
            OjNexusDatabase.MIGRATION_16_17,
        )
    }
}
