package com.ojnexus.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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
    fun `fresh v15 schema carries both audited composite indices`() {
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
        // Build a faithful v13 database: capture the exact CREATE TABLE statements from a
        // fresh Room open (v14 added indices only, no columns — so the table DDL equals
        // v13's), create a bare file with those two tables, seed rows, and rewind the
        // version marker to 13. No room_master_table → Room treats it as a migrated-from-
        // legacy db, exactly the upgrade path real installs take.
        val probe = open("audit-probe-${System.nanoTime()}.db")
        val ddl = mutableListOf<String>()
        probe.openHelper.writableDatabase.query(
            "SELECT name, sql FROM sqlite_master WHERE type = 'table' " +
                "AND name IN ('submission_jobs', 'remote_problems')",
            emptyArray(),
        ).use { c ->
            while (c.moveToNext()) {
                ddl.add(c.getString(1))
            }
        }
        // Capture the remaining entity tables plus every declared index DDL so the migrated
        // database passes Room's full schema validation (Room validates EVERY entity and
        // every declared index, not only the migrated tables). The v13 indices on
        // submission_jobs/remote_problems themselves stay part of the expected schema —
        // only the indices the v14 migration creates are excluded by NAME.
        probe.openHelper.writableDatabase.query(
            "SELECT name, sql FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' " +
                "AND name NOT IN ('android_metadata', 'room_master_table', 'submission_jobs', 'remote_problems')",
            emptyArray(),
        ).use { c ->
            while (c.moveToNext()) {
                ddl.add(c.getString(1))
            }
        }
        probe.openHelper.writableDatabase.query(
            "SELECT name, sql FROM sqlite_master WHERE type = 'index' AND sql IS NOT NULL " +
                "AND name NOT IN ('index_submission_jobs_judge_status_updated_at', 'index_remote_problems_judge_external_id')",
            emptyArray(),
        ).use { c ->
            while (c.moveToNext()) {
                ddl.add(c.getString(1))
            }
        }
        probe.close()

        val db = context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null)
        ddl.forEach(db::execSQL)
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
            assertEquals(15, migrated.openHelper.writableDatabase.version)
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
        )
    }
}
