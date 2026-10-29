package com.ojnexus.core.data.restore

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import com.ojnexus.core.database.OjNexusDatabase
import com.ojnexus.core.database.entity.ProblemEntity
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 127C: import storage faults (RV004–RV006) must return typed categories, preserve the
 * active store, and clean only the artifacts the failed run owns so a retry after freeing
 * space converges instead of blocking on orphan evidence.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImportStorageFaultTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val journal = File(context.filesDir, "oj-nexus-restore.journal")
    private val pending = File(context.filesDir, "oj-nexus-pending-restore.db")
    private val pendingTemp = File(context.filesDir, "oj-nexus-pending-restore.db.tmp")
    private val opened = mutableListOf<OjNexusDatabase>()

    @After
    fun cleanup() {
        opened.forEach { runCatching { it.close() } }
        context.getDatabasePath("import-source.db").delete()
        journal.delete(); File(journal.path + ".tmp").delete()
        pending.delete(); pendingTemp.delete()
    }

    /** A real Room database with the current schema; valid input for [prepareImport]. */
    private suspend fun importableSource(): File {
        val db = Room.databaseBuilder(context, OjNexusDatabase::class.java, "import-source.db")
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .build()
        opened += db
        db.problemDao().insert(
            ProblemEntity(
                judge = "codeforces", externalId = "source-row", title = "source-row",
                difficulty = 800, createdAt = 1L, updatedAt = 1L,
            ),
        )
        db.close()
        opened.remove(db)
        return context.getDatabasePath("import-source.db")
    }

    private fun coordinator(files: RestoreFileOperations = SystemRestoreFileOperations) =
        DatabaseRestoreCoordinator(context, context.contentResolver, files)

    @Test
    fun `staging allocation failure returns storage full without throwing`() = runBlocking {
        val source = importableSource()
        val outcome = coordinator(IoFailingFiles(failTempAllocation = true))
            .prepareImport(Uri.fromFile(source))
        assertTrue("outcome=$outcome", outcome is RestoreOutcome.Rejected)
        assertEquals(RestoreFailure.STORAGE_FULL, (outcome as RestoreOutcome.Rejected).failure)
        assertFalse(pending.exists()); assertFalse(pendingTemp.exists()); assertFalse(journal.exists())
    }

    @Test
    fun `staged copy failure returns storage full and keeps retry possible`() = runBlocking {
        val source = importableSource()
        val outcome = coordinator(IoFailingFiles(failCopyStream = true))
            .prepareImport(Uri.fromFile(source))
        assertTrue("outcome=$outcome", outcome is RestoreOutcome.Rejected)
        assertEquals(RestoreFailure.STORAGE_FULL, (outcome as RestoreOutcome.Rejected).failure)
        assertFalse("staged pending file must not survive a failed copy", pending.exists())
        assertFalse(pendingTemp.exists()); assertFalse(journal.exists())
        val retry = coordinator().prepareImport(Uri.fromFile(source))
        assertTrue("retry=$retry", retry is RestoreOutcome.Staged)
    }

    @Test
    fun `plain staged io failure returns storage io`() = runBlocking {
        val source = importableSource()
        val outcome = coordinator(IoFailingFiles(failCopyStream = true, copyStreamMessage = "read of source stream failed"))
            .prepareImport(Uri.fromFile(source))
        assertTrue("outcome=$outcome", outcome is RestoreOutcome.Rejected)
        assertEquals(RestoreFailure.STORAGE_IO, (outcome as RestoreOutcome.Rejected).failure)
    }

    @Test
    fun `journal write failure returns storage full and removes staged evidence`() = runBlocking {
        val source = importableSource()
        val outcome = coordinator(IoFailingFiles(failJournalWrite = true))
            .prepareImport(Uri.fromFile(source))
        assertTrue("outcome=$outcome", outcome is RestoreOutcome.Rejected)
        assertEquals(RestoreFailure.STORAGE_FULL, (outcome as RestoreOutcome.Rejected).failure)
        assertFalse("staged pending file must be cleaned so the next start is not blocked", pending.exists())
        assertFalse(pendingTemp.exists()); assertFalse(journal.exists())
        assertFalse(File(journal.path + ".tmp").exists())
        val retry = coordinator().prepareImport(Uri.fromFile(source))
        assertTrue("retry=$retry", retry is RestoreOutcome.Staged)
    }

    @Test
    fun `pre-existing journal survives a failed later attempt`() = runBlocking {
        val source = importableSource()
        assertTrue(coordinator().prepareImport(Uri.fromFile(source)) is RestoreOutcome.Staged)
        val outcome = coordinator(IoFailingFiles(failCopyStream = true))
            .prepareImport(Uri.fromFile(source))
        assertTrue("outcome=$outcome", outcome is RestoreOutcome.Rejected)
        assertTrue("a staged import must survive a failed later attempt", journal.exists())
        assertTrue(pending.exists())
    }

    @Test
    fun `invalid input still rejects as invalid sqlite`() {
        val garbage = File(context.filesDir, "import-source.db")
        garbage.writeBytes(byteArrayOf(1, 2, 3, 4, 5))
        val outcome = coordinator().prepareImport(Uri.fromFile(garbage))
        assertTrue("outcome=$outcome", outcome is RestoreOutcome.Rejected)
        assertEquals(RestoreFailure.INVALID_SQLITE, (outcome as RestoreOutcome.Rejected).failure)
    }

    @Test
    fun `classifier maps enospc markers and cause chains`() {
        assertEquals(
            RestoreFailure.STORAGE_FULL,
            classifyImportIoFailure(IOException("No space left on device")),
        )
        assertEquals(
            RestoreFailure.STORAGE_FULL,
            classifyImportIoFailure(IOException("write failed: ENOSPC")),
        )
        assertEquals(
            RestoreFailure.STORAGE_FULL,
            classifyImportIoFailure(IllegalStateException("wrapped", IOException("not enough space in /data"))),
        )
        assertEquals(
            RestoreFailure.STORAGE_IO,
            classifyImportIoFailure(IOException("read of source stream failed")),
        )
        assertEquals(
            RestoreFailure.STORAGE_IO,
            classifyImportIoFailure(IllegalStateException("outer", IOException("inner io"))),
        )
        assertEquals(null, classifyImportIoFailure(IllegalStateException("not io")))
        assertEquals(null, classifyImportIoFailure(IllegalStateException("outer", IllegalStateException("inner"))))
    }
}

private class IoFailingFiles(
    private val failTempAllocation: Boolean = false,
    private val failCopyStream: Boolean = false,
    private val failJournalWrite: Boolean = false,
    private val copyStreamMessage: String = "No space left on device",
) : RestoreFileOperations by SystemRestoreFileOperations {
    override fun createStagingTemp(directory: File): File {
        if (failTempAllocation) throw IOException("No space left on device")
        return SystemRestoreFileOperations.createStagingTemp(directory)
    }

    override fun copyStreamAndSync(source: InputStream, target: File) {
        if (failCopyStream) throw IOException(copyStreamMessage)
        SystemRestoreFileOperations.copyStreamAndSync(source, target)
    }

    override fun writeAndSync(target: File, bytes: ByteArray) {
        if (failJournalWrite && target.name == "oj-nexus-restore.journal.tmp") {
            throw IOException("No space left on device")
        }
        SystemRestoreFileOperations.writeAndSync(target, bytes)
    }
}
