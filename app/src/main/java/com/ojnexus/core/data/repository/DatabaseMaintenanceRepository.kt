package com.ojnexus.core.data.repository

import androidx.sqlite.db.SupportSQLiteDatabase
import com.ojnexus.core.database.OjNexusDatabase
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Row counts and size facts for one table, reported by the settings housekeeping panel.
 */
data class DatabaseMaintenanceStats(
    val dbFileBytes: Long,
    val walBytes: Long,
    val pageSize: Int,
    val pageCount: Long,
    val freelistCount: Long,
    val rowCounts: Map<String, Int>,
) {
    /** Honest database size straight from `PRAGMA page_count * page_size`. */
    val dbBytes: Long get() = pageCount * pageSize
}

/**
 * Read-only size/row reporting plus a cancellable VACUUM for the settings housekeeping
 * panel. All sizes come from PRAGMA and file probes — never invented.
 */
class DatabaseMaintenanceRepository(
    private val database: OjNexusDatabase,
    private val databaseFile: File,
) {

    suspend fun maintenanceStats(): DatabaseMaintenanceStats = withContext(Dispatchers.IO) {
        val live = database.openHelper.writableDatabase
        val rowCounts = mutableMapOf<String, Int>()
        MAINTENANCE_TABLES.forEach { table ->
            live.query("SELECT COUNT(*) FROM \"$table\"").use { cursor ->
                rowCounts[table] = if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
        }
        DatabaseMaintenanceStats(
            dbFileBytes = databaseFile.length(),
            walBytes = File(databaseFile.parentFile, databaseFile.name + WAL_SUFFIX).length(),
            pageSize = pragmaInt(live, "PRAGMA page_size"),
            pageCount = pragmaLong(live, "PRAGMA page_count"),
            freelistCount = pragmaLong(live, "PRAGMA freelist_count"),
            rowCounts = rowCounts,
        )
    }

    /**
     * Reclaims free pages. SQLite VACUUM is atomic and cannot be interrupted through the
     * framework API; a racing cancel therefore either loses (vacuum commits) or the job is
     * cancelled before the statement starts. [CancellationException] is always rethrown.
     */
    suspend fun vacuum() = withContext(Dispatchers.IO) {
        val live = database.openHelper.writableDatabase
        try {
            live.query("PRAGMA wal_checkpoint(TRUNCATE)").use { cursor -> cursor.moveToFirst() }
            live.compileStatement("VACUUM").executeUpdateDelete()
        } catch (exception: CancellationException) {
            throw exception
        }
    }

    private fun pragmaInt(live: SupportSQLiteDatabase, pragma: String): Int =
        live.query(pragma).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }

    private fun pragmaLong(live: SupportSQLiteDatabase, pragma: String): Long =
        live.query(pragma).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else 0L }

    companion object {
        private const val WAL_SUFFIX = "-wal"

        /** Same 22 tables the restore path requires — the honest maintenance surface. */
        val MAINTENANCE_TABLES: List<String> = listOf(
            "problems",
            "problem_tags",
            "problem_tag_cross_ref",
            "attempts",
            "failure_entries",
            "problem_notes",
            "reviews",
            "review_log",
            "training_tasks",
            "training_sessions",
            "training_session_problems",
            "judge_accounts",
            "judge_profiles",
            "rating_changes",
            "remote_problems",
            "remote_problem_details",
            "contests",
            "contest_problem_markers",
            "problem_knowledge",
            "sync_states",
            "submission_jobs",
            "workspace_drafts",
        )

        fun formatBytes(bytes: Long): String = when {
            bytes < 0 -> "?"
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KiB".format(bytes / 1024.0)
            bytes < 1024L * 1024 * 1024 -> "%.1f MiB".format(bytes / (1024.0 * 1024))
            else -> "%.2f GiB".format(bytes / (1024.0 * 1024 * 1024))
        }
    }
}
