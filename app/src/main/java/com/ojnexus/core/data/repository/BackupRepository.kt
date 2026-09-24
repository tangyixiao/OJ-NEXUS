package com.ojnexus.core.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.room.RoomDatabase
import com.ojnexus.core.data.restore.DatabaseRestoreCoordinator
import com.ojnexus.core.data.restore.RestoreOutcome
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Exports the current Room database and stages imports for pre-Room atomic restoration. */
class BackupRepository(
    private val database: RoomDatabase,
    private val context: Context,
) {
    suspend fun exportTo(resolver: ContentResolver, destination: Uri): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                // A truncate checkpoint makes the copied main file self-contained even when Room
                // opened the database in production WAL mode.
                database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { }
                val snapshot = File.createTempFile(
                    "oj-nexus-export-${UUID.randomUUID()}-",
                    ".db",
                    context.cacheDir,
                ).also { it.delete() }
                val escapedPath = snapshot.absolutePath.replace("\\", "/").replace("'", "''")
                try {
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
                    resolver.openOutputStream(destination)?.use { output ->
                        snapshot.inputStream().use { input -> input.copyTo(output) }
                    } ?: error("backup destination unavailable")
                } finally {
                    snapshot.delete()
                }
            }.isSuccess
        }

    suspend fun importFrom(resolver: ContentResolver, source: Uri): Boolean =
        withContext(Dispatchers.IO) {
            DatabaseRestoreCoordinator(context, resolver).prepareImport(source) is RestoreOutcome.Staged
        }

    companion object {
        /** Compatibility entry point for callers that only need a success flag. */
        fun restorePending(context: Context): Boolean {
            return DatabaseRestoreCoordinator(context).restoreBeforeRoomOpen() is RestoreOutcome.Applied
        }
    }
}
