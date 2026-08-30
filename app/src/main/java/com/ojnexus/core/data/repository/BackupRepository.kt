package com.ojnexus.core.data.repository

import android.content.ContentResolver
import android.net.Uri
import androidx.room.RoomDatabase
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Exports the current Room database after checkpointing WAL so the backup is self-contained. */
class BackupRepository(private val database: RoomDatabase) {
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
}
