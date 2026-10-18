package com.ojnexus.core.data.repository

import android.content.ContentResolver
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.RoomDatabase
import com.ojnexus.core.data.backup.ManifestTableCount
import com.ojnexus.core.data.backup.SnapshotConflictReport
import com.ojnexus.core.data.backup.SnapshotConflictRow
import com.ojnexus.core.data.backup.SnapshotManifest
import com.ojnexus.core.data.backup.SnapshotManifestCodec
import com.ojnexus.core.data.restore.DatabaseRestoreCoordinator
import com.ojnexus.core.data.restore.RestoreOutcome
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Exports the local Room database and stages imports for pre-Room atomic restoration.
 *
 * The preferred format is a snapshot archive (see [SnapshotManifestCodec]): a ZIP with exactly
 * `manifest.json` and `database.db`. Legacy raw `.db` exports remain importable.
 */
class BackupRepository(
    private val database: RoomDatabase,
    private val context: Context,
) {
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

    suspend fun importFrom(resolver: ContentResolver, source: Uri): Boolean =
        withContext(Dispatchers.IO) {
            val extracted = extractSnapshotDatabase(resolver, source)
            if (extracted != null) {
                val extractedUri = Uri.fromFile(extracted)
                try {
                    return@withContext DatabaseRestoreCoordinator(context, resolver)
                        .prepareImport(extractedUri) is RestoreOutcome.Staged
                } finally {
                    extracted.delete()
                }
            }
            DatabaseRestoreCoordinator(context, resolver).prepareImport(source) is RestoreOutcome.Staged
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
        val opened: SQLiteDatabase? = runCatching {
            SQLiteDatabase.openDatabase(snapshot.path, null, SQLiteDatabase.OPEN_READONLY)
        }.getOrNull()
        // When the standalone snapshot cannot be opened (some embedded SQLite builds refuse
        // files they did not create themselves), the counts come from the live database,
        // which the truncate checkpoint in createSnapshotFile just made consistent.
        val counts = mutableListOf<ManifestTableCount>()
        if (opened != null) {
            REQUIRED_TABLES.forEach { table ->
                opened.rawQuery("SELECT COUNT(*) FROM \"$table\"", null).use { cursor ->
                    val rows = if (cursor.moveToFirst()) cursor.getInt(0) else 0
                    counts += ManifestTableCount(table, rows)
                }
            }
            opened.close()
        } else {
            val live = database.openHelper.writableDatabase
            REQUIRED_TABLES.forEach { table ->
                live.query("SELECT COUNT(*) FROM \"$table\"").use { cursor ->
                    val rows = if (cursor.moveToFirst()) cursor.getInt(0) else 0
                    counts += ManifestTableCount(table, rows)
                }
            }
        }
        return counts
    }

    private fun createSnapshotFile(): File {
        // A truncate checkpoint makes the copied main file self-contained even when Room
        // opened the database in production WAL mode.
        database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { }
        val snapshot = File.createTempFile(
            "oj-nexus-export-${UUID.randomUUID()}-",
            ".db",
            context.cacheDir,
        ).also { it.delete() }
        val escapedPath = snapshot.absolutePath.replace("\\", "/").replace("'", "''")
        val vacuumed = runCatching {
            database.openHelper.writableDatabase
                .query("VACUUM INTO '$escapedPath'")
                .use { }
            snapshot.isFile
        }.getOrDefault(false)
        if (!vacuumed) {
            // Older SQLite builds do not implement VACUUM INTO. The truncate
            // checkpoint above leaves a safe main-file snapshot for those builds.
            val source = File(requireNotNull(database.openHelper.writableDatabase.path))
            require(source.isFile) { "database file unavailable" }
            source.copyTo(snapshot, overwrite = true)
        }
        require(snapshot.isFile) { "database snapshot unavailable" }
        return snapshot
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
