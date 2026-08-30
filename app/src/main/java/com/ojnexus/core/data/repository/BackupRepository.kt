package com.ojnexus.core.data.repository

import android.content.ContentResolver
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.RoomDatabase
import com.ojnexus.core.database.OjNexusDatabase
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Exports the current Room database after checkpointing WAL so the backup is self-contained. */
class BackupRepository(
    private val database: RoomDatabase,
    private val context: Context,
) {
    suspend fun exportTo(resolver: ContentResolver, destination: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { }
            val source = File(requireNotNull(database.openHelper.writableDatabase.path))
            require(source.isFile) { "database file unavailable" }
            resolver.openOutputStream(destination)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: error("backup destination unavailable")
        }.isSuccess
    }

    suspend fun importFrom(resolver: ContentResolver, source: Uri): Boolean =
        withContext(Dispatchers.IO) {
            val staging = File.createTempFile("oj-nexus-import-", ".db", context.cacheDir)
            runCatching {
                resolver.openInputStream(source)?.use { input ->
                    staging.outputStream().use { output -> input.copyTo(output) }
                } ?: error("backup source unavailable")
                validate(staging)
                staging.copyTo(pendingRestoreFile(context), overwrite = true)
            }.also { staging.delete() }.isSuccess
        }

    private fun validate(file: File) {
        require(file.isFile && file.length() > 0) { "backup file unavailable" }
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { backup ->
            backup.rawQuery("PRAGMA user_version", null).use { cursor ->
                require(cursor.moveToFirst()) { "backup schema unavailable" }
                require(cursor.getInt(0) == OjNexusDatabase.CURRENT_SCHEMA_VERSION) {
                    "backup schema version mismatch"
                }
            }
            backup.rawQuery(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'problems'",
                null,
            ).use { cursor ->
                require(cursor.moveToFirst() && cursor.getInt(0) == 1) {
                    "backup is not an OJ NEXUS database"
                }
            }
        }
    }

    companion object {
        private const val PENDING_RESTORE_NAME = "oj-nexus-pending-restore.db"
        private const val PENDING_RESTORE_TEMP_NAME = "oj-nexus-pending-restore.db.tmp"

        fun restorePending(context: Context): Boolean {
            val pending = pendingRestoreFile(context)
            if (!pending.isFile) return false
            return runCatching {
                validatePending(pending)
                val target = context.getDatabasePath(OjNexusDatabase.DATABASE_NAME)
                val temporary = File(context.filesDir, PENDING_RESTORE_TEMP_NAME)
                temporary.delete()
                pending.copyTo(temporary, overwrite = true)
                File(target.path + "-wal").delete()
                File(target.path + "-shm").delete()
                if (target.exists()) require(target.delete()) { "current database is locked" }
                require(temporary.renameTo(target)) { "database restore could not be completed" }
                require(pending.delete()) { "pending backup could not be cleared" }
            }.isSuccess
        }

        private fun pendingRestoreFile(context: Context): File =
            File(context.filesDir, PENDING_RESTORE_NAME)

        private fun validatePending(file: File) {
            require(file.isFile && file.length() > 0) { "pending backup unavailable" }
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { backup ->
                backup.rawQuery("PRAGMA user_version", null).use { cursor ->
                    require(cursor.moveToFirst()) { "pending schema unavailable" }
                    require(cursor.getInt(0) == OjNexusDatabase.CURRENT_SCHEMA_VERSION) {
                        "pending schema version mismatch"
                    }
                }
            }
        }
    }
}
