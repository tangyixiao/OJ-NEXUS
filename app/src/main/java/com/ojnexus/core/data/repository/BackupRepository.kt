package com.ojnexus.core.data.repository

import android.content.ContentResolver
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ojnexus.core.data.backup.ManifestTableCount
import com.ojnexus.core.data.backup.SnapshotConflictReport
import com.ojnexus.core.data.backup.SnapshotConflictRow
import com.ojnexus.core.data.backup.SnapshotManifest
import com.ojnexus.core.data.backup.SnapshotManifestCodec
import com.ojnexus.core.data.restore.DatabaseRestoreCoordinator
import com.ojnexus.core.data.restore.RestoreOutcome
import com.ojnexus.core.database.OjNexusDatabase
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Serialises operations that rewrite the main database file: the old-engine fallback export
 * copy and the maintenance VACUUM's checkpoint. Both run on Dispatchers.IO in this process.
 */
internal val mainFileRewriteLock = Mutex()

/**
 * Exports the local Room database and stages imports for pre-Room atomic restoration.
 *
 * The preferred format is a snapshot archive (see [SnapshotManifestCodec]): a ZIP with exactly
 * `manifest.json` and `database.db`. Legacy raw `.db` exports remain importable.
 */
class BackupRepository(
    private val database: RoomDatabase,
    private val context: Context,
    engineSupportsVacuumInto: (() -> Boolean)? = null,
) {
    /**
     * Phase 127D: `VACUUM INTO` needs SQLite 3.27+ (API 29+). Older minSdk-26 engines use
     * the exclusive-checkpoint fallback instead. Injectable so tests can force either path.
     */
    private val supportsVacuumInto: () -> Boolean =
        engineSupportsVacuumInto
            ?: { SqliteVersion.parse(readEngineVersion()) >= VACUUM_INTO_MIN_VERSION }

    private fun readEngineVersion(): String =
        database.openHelper.writableDatabase
            .query("SELECT sqlite_version()").use { cursor ->
                cursor.moveToFirst()
                cursor.getString(0)
            }
    suspend fun exportTo(resolver: ContentResolver, destination: Uri): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val snapshot = createSnapshotFile()
                try {
                    resolver.openOutputStream(destination)?.use { output ->
                        snapshot.inputStream().use { input -> input.copyTo(output) }
                    } ?: error("backup destination unavailable")
                } finally {
                    snapshot.delete()
                }
            }.isSuccess
        }

    /** Exports a snapshot archive (manifest + database) to [destination]. */
    suspend fun exportSnapshot(resolver: ContentResolver, destination: Uri): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val snapshot = createSnapshotFile()
                try {
                    val manifest = SnapshotManifest(
                        schemaVersion = schemaVersion(),
                        dataGeneration = DatabaseRestoreCoordinator(context).currentDataGeneration(),
                        createdAtEpochMs = System.currentTimeMillis(),
                        tableCounts = countTables(snapshot),
                        appVersionName = null,
                    )
                    resolver.openOutputStream(destination)?.use { output ->
                        ZipOutputStream(output).use { zip ->
                            zip.putNextEntry(ZipEntry(SnapshotManifestCodec.MANIFEST_ENTRY))
                            zip.write(SnapshotManifestCodec.encode(manifest).toByteArray(Charsets.UTF_8))
                            zip.closeEntry()
                            zip.putNextEntry(ZipEntry(SnapshotManifestCodec.DATABASE_ENTRY))
                            snapshot.inputStream().use { input -> input.copyTo(zip) }
                            zip.closeEntry()
                        }
                    } ?: error("backup destination unavailable")
                } finally {
                    snapshot.delete()
                }
            }.isSuccess
        }

    /**
     * Stages a snapshot or legacy database import through the restore coordinator and returns
     * the full typed outcome (Phase 127C), so storage failures reach the UI as categories.
     */
    suspend fun importFrom(resolver: ContentResolver, source: Uri): RestoreOutcome =
        withContext(Dispatchers.IO) {
            val extracted = extractSnapshotDatabase(resolver, source)
            if (extracted != null) {
                val extractedUri = Uri.fromFile(extracted)
                try {
                    return@withContext DatabaseRestoreCoordinator(context, resolver)
                        .prepareImport(extractedUri)
                } finally {
                    extracted.delete()
                }
            }
            DatabaseRestoreCoordinator(context, resolver).prepareImport(source)
        }

    /**
     * Read-only preview of a snapshot import: pairs the manifest's recorded table counts
     * with the live database's current counts. Never stages or writes anything — the
     * caller decides whether to proceed with [importFrom]. Returns null when the source
     * is not a valid snapshot archive (legacy raw databases import without a preview).
     */
    suspend fun inspectSnapshot(resolver: ContentResolver, source: Uri): SnapshotConflictReport? =
        withContext(Dispatchers.IO) {
            val manifest = readSnapshotManifest(resolver, source) ?: return@withContext null
            if (manifest.schemaVersion != schemaVersion() || manifest.dataGeneration.isBlank()) {
                return@withContext null
            }
            val liveCounts = liveTableCounts()
            SnapshotConflictReport(
                schemaVersion = manifest.schemaVersion,
                dataGeneration = manifest.dataGeneration,
                createdAtEpochMs = manifest.createdAtEpochMs,
                rows = manifest.tableCounts.map { entry ->
                    SnapshotConflictRow(
                        table = entry.table,
                        incoming = entry.rows,
                        current = liveCounts[entry.table] ?: 0,
                    )
                },
            )
        }

    /**
     * Returns the extracted database file from a valid snapshot archive, or null when the source
     * is not one (the caller then falls back to the legacy raw-database path).
     */
    private fun extractSnapshotDatabase(resolver: ContentResolver, source: Uri): File? {
        val manifestRaw = StringBuilder()
        var databaseBytes = 0L
        val databaseFile = File.createTempFile("oj-nexus-snapshot-", ".db", context.cacheDir)
        var sawManifest = false
        var sawDatabase = false
        val opened: Boolean = runCatching {
            resolver.openInputStream(source)?.use { stream ->
                ZipInputStream(stream).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        when (entry.name) {
                            SnapshotManifestCodec.MANIFEST_ENTRY -> {
                                if (sawManifest) return null
                                sawManifest = true
                                val text = zip.readBytes().toString(Charsets.UTF_8)
                                manifestRaw.append(text)
                            }

                            SnapshotManifestCodec.DATABASE_ENTRY -> {
                                if (sawDatabase) return null
                                sawDatabase = true
                                databaseFile.outputStream().use { output ->
                                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
                                    while (true) {
                                        val read = zip.read(buffer)
                                        if (read < 0) break
                                        databaseBytes += read
                                        if (databaseBytes > MAX_DATABASE_BYTES) {
                                            throw IllegalStateException("snapshot database too large")
                                        }
                                        output.write(buffer, 0, read)
                                    }
                                }
                            }

                            else -> return null
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
                true
            } ?: return null
        }.getOrDefault(false)
        if (!opened || !sawManifest || !sawDatabase) {
            databaseFile.delete()
            return null
        }
        val manifest = SnapshotManifestCodec.decode(manifestRaw.toString())
        if (manifest == null || manifest.schemaVersion != schemaVersion() ||
            manifest.dataGeneration.isBlank()
        ) {
            databaseFile.delete()
            return null
        }
        if (databaseFile.length() == 0L) {
            databaseFile.delete()
            return null
        }
        return databaseFile
    }

    private fun schemaVersion(): Int = database.openHelper.writableDatabase.version

    private fun liveTableCounts(): Map<String, Int> {
        val live = database.openHelper.writableDatabase
        val counts = mutableMapOf<String, Int>()
        REQUIRED_TABLES.forEach { table ->
            live.query("SELECT COUNT(*) FROM \"$table\"").use { cursor ->
                counts[table] = if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
        }
        return counts
    }

    /**
     * Streams only the manifest entry of a snapshot archive without extracting the database
     * payload (the database entry is skipped, never read into memory). Strict rules mirror
     * [extractSnapshotDatabase]: any unknown or duplicate entry makes the archive invalid.
     */
    private fun readSnapshotManifest(resolver: ContentResolver, source: Uri): SnapshotManifest? {
        val raw = StringBuilder()
        var sawManifest = false
        val opened: Boolean = runCatching {
            resolver.openInputStream(source)?.use { stream ->
                ZipInputStream(stream).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        when (entry.name) {
                            SnapshotManifestCodec.MANIFEST_ENTRY -> {
                                if (sawManifest) return null
                                sawManifest = true
                                raw.append(zip.readBytes().toString(Charsets.UTF_8))
                            }

                            SnapshotManifestCodec.DATABASE_ENTRY -> {
                                // Skipped on purpose: the preview must not read the payload.
                            }

                            else -> return null
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
                true
            } ?: return null
        }.getOrDefault(false)
        if (!opened || !sawManifest) return null
        return SnapshotManifestCodec.decode(raw.toString())
    }

    private fun countTables(snapshot: File): List<ManifestTableCount> {
        val opened = SQLiteDatabase.openDatabase(snapshot.path, null, SQLiteDatabase.OPEN_READONLY)
        return opened.use { standalone ->
            val integrity = standalone.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                check(cursor.moveToFirst()) { "snapshot integrity check returned no result" }
                cursor.getString(0)
            }
            check(integrity.equals("ok", ignoreCase = true)) {
                "snapshot integrity check failed: $integrity"
            }
            check(standalone.version == schemaVersion()) {
                "snapshot schema version does not match the active database"
            }
            REQUIRED_TABLES.map { table ->
                val rows = standalone.rawQuery("SELECT COUNT(*) FROM \"$table\"", null).use { cursor ->
                    check(cursor.moveToFirst()) { "snapshot count query returned no result for $table" }
                    cursor.getInt(0)
                }
                ManifestTableCount(table, rows)
            }
        }
    }

    private suspend fun createSnapshotFile(): File {
        val live = database.openHelper.writableDatabase
        val snapshot = File.createTempFile(
            "ojn-${UUID.randomUUID().toString().take(8)}-",
            ".db",
            context.cacheDir,
        ).also { it.delete() }
        try {
            if (supportsVacuumInto()) {
                val escapedPath = snapshot.absolutePath.replace("\\", "/").replace("'", "''")
                // VACUUM INTO reads through SQLite's live connection and creates a self-contained
                // database. If this SQLite version lacks the command, fail closed instead of
                // copying the main database file and silently omitting WAL contents.
                live.execSQL("VACUUM INTO '$escapedPath'")
            } else {
                copyCheckpointedSnapshot(live, snapshot)
            }
            check(snapshot.isFile && snapshot.length() > 0L) { "database snapshot unavailable" }
            validateSnapshot(snapshot)
            return snapshot
        } catch (error: Exception) {
            snapshot.delete()
            throw IllegalStateException("could not create a consistent database snapshot", error)
        }
    }

    /**
     * Pre-3.27 export. SQLite forbids running a checkpoint inside a transaction on the same
     * connection, so the checkpoint runs first, without one. Copy safety, per journal mode:
     * WAL writers append to the -wal file and never touch the main file, so a successful
     * TRUNCATE checkpoint plus [mainFileRewriteLock] (which also serialises the maintenance
     * VACUUM, the only other production checkpoint) leaves the main file static during the
     * copy. Rollback-mode writers DO rewrite the main file on commit, so the copy runs inside
     * a read transaction whose shared lock blocks their commits for its duration. Either way
     * the copied file reflects the checkpointed state — never a stale main file.
     */
    private suspend fun copyCheckpointedSnapshot(live: SupportSQLiteDatabase, snapshot: File) {
        val databasePath = requireNotNull(live.path)
        val dedicated = SQLiteDatabase.openDatabase(databasePath, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            mainFileRewriteLock.withLock {
                val previousAutocheckpoint = dedicated.rawQuery("PRAGMA wal_autocheckpoint", null).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getInt(0) else 1000
                }
                dedicated.rawQuery("PRAGMA wal_autocheckpoint=0", null).use { it.moveToFirst() }
                try {
                    var walMode = false
                    dedicated.rawQuery("PRAGMA journal_mode", null).use { cursor ->
                        if (cursor.moveToFirst()) walMode = cursor.getString(0)?.lowercase() == "wal"
                    }
                    if (walMode) {
                        var checkpointRan = false
                        dedicated.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { cursor ->
                            if (cursor.moveToFirst()) {
                                check(cursor.getInt(0) == 0) { "wal checkpoint blocked by another connection" }
                                checkpointRan = true
                            }
                        }
                        check(checkpointRan) { "wal checkpoint did not run" }
                        val wal = File(databasePath + "-wal")
                        check(!wal.isFile || wal.length() == 0L) { "wal checkpoint did not truncate" }
                    }
                    dedicated.execSQL("BEGIN")
                    try {
                        // Materialise the transaction's lock: a WAL read snapshot, or a rollback
                        // SHARED lock that blocks committing writers while the file is copied.
                        dedicated.rawQuery("SELECT COUNT(*) FROM sqlite_master", null).use { it.moveToFirst() }
                        snapshot.outputStream().use { output ->
                            File(databasePath).inputStream().use { input -> input.copyTo(output) }
                            output.fd.sync()
                        }
                    } finally {
                        dedicated.execSQL("COMMIT")
                    }
                } finally {
                    runCatching {
                        dedicated.rawQuery("PRAGMA wal_autocheckpoint=$previousAutocheckpoint", null)
                            .use { it.moveToFirst() }
                    }
                }
            }
        } finally {
            dedicated.close()
        }
    }

    /**
     * Both export paths must publish a complete database: the same schema/integrity/
     * required-table contract the restore coordinator applies to candidates.
     */
    private fun validateSnapshot(snapshot: File) {
        SQLiteDatabase.openDatabase(snapshot.path, null, SQLiteDatabase.OPEN_READONLY).use { check ->
            check.rawQuery("PRAGMA user_version", null).use { cursor ->
                check(cursor.moveToFirst() && cursor.getInt(0) == OjNexusDatabase.CURRENT_SCHEMA_VERSION) {
                    "snapshot schema version mismatch"
                }
            }
            listOf("PRAGMA quick_check", "PRAGMA integrity_check").forEach { pragma ->
                check.rawQuery(pragma, null).use { cursor ->
                    check(cursor.moveToFirst() && cursor.getString(0) == "ok") { "snapshot integrity check failed" }
                }
            }
            val placeholders = REQUIRED_TABLES.joinToString(",") { "?" }
            check.rawQuery(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name IN ($placeholders)",
                REQUIRED_TABLES.toTypedArray(),
            ).use { cursor ->
                val found = buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
                check(found.containsAll(REQUIRED_TABLES)) { "snapshot is missing required tables" }
            }
        }
    }

    companion object {
        /** Compatibility entry point for callers that only need a success flag. */
        fun restorePending(context: Context): Boolean {
            return DatabaseRestoreCoordinator(context).restoreBeforeRoomOpen() is RestoreOutcome.Applied
        }

        /** Upper bound on a snapshot database entry; a normal export stays far below. */
        const val MAX_DATABASE_BYTES: Long = 512L * 1024L * 1024L

        private val REQUIRED_TABLES = listOf(
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
    }
}

/** Parsed [SELECT sqlite_version()] triple; comparison is component-wise. */
internal data class SqliteVersion(private val parts: List<Int>) : Comparable<SqliteVersion> {
    override fun compareTo(other: SqliteVersion): Int {
        for (index in 0 until maxOf(parts.size, other.parts.size)) {
            val left = parts.getOrElse(index) { 0 }
            val right = other.parts.getOrElse(index) { 0 }
            if (left != right) return left - right
        }
        return 0
    }

    companion object {
        /** Parses "3.19.4"; missing or non-numeric components are zero; always three parts. */
        internal fun parse(text: String): SqliteVersion {
            val parts = text.trim().split('.').map { part -> part.filter(Char::isDigit).toIntOrNull() ?: 0 }
            val padded = (0 until 3).map { index -> parts.getOrElse(index) { 0 } }
            return SqliteVersion(padded)
        }
    }
}

/** [VACUUM INTO] was added in SQLite 3.27.0. */
internal val VACUUM_INTO_MIN_VERSION = SqliteVersion.parse("3.27.0")
