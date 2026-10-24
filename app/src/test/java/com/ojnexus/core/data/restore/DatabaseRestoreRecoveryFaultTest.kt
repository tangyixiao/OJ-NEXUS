package com.ojnexus.core.data.restore

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ojnexus.core.database.OjNexusDatabase
import com.ojnexus.core.database.entity.ProblemEntity
import java.io.File
import java.util.Properties
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseRestoreRecoveryFaultTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseFile = context.getDatabasePath(OjNexusDatabase.DATABASE_NAME)
    private val generation = File(context.filesDir, "oj-nexus-data-generation")
    private val journal = File(context.filesDir, "oj-nexus-restore.journal")
    private val opened = mutableListOf<OjNexusDatabase>()

    @After
    fun cleanup() {
        opened.forEach { runCatching { it.close() } }
        listOf(
            databaseFile, File(databaseFile.path + "-wal"), File(databaseFile.path + "-shm"),
            File(databaseFile.path + ".restore-rollback"),
            File(databaseFile.path + "-wal.restore-rollback"),
            File(databaseFile.path + "-shm.restore-rollback"), generation, journal,
            File(journal.path + ".tmp"), File(context.filesDir, "oj-nexus-pending-restore.db"),
            File(context.filesDir, "oj-nexus-pending-restore.db.tmp"),
        ).forEach { it.delete() }
    }

    @Test
    fun `RV015 APPLIED reconciles new generation and is idempotent`() = runBlocking {
        setupAppliedPair()

        val coordinator = DatabaseRestoreCoordinator(context)
        val first = coordinator.restoreBeforeRoomOpen()
        assertEquals("new-generation", generation.readText().trim())
        assertTrue("outcome=$first", first is RestoreOutcome.Applied)
        assertEquals(setOf("new-row"), readProblemIds())

        val second = DatabaseRestoreCoordinator(context).restoreBeforeRoomOpen()
        assertEquals("new-generation", generation.readText().trim())
        assertTrue("second=$second", second is RestoreOutcome.NothingToRestore || second is RestoreOutcome.Applied)
        assertEquals(setOf("new-row"), readProblemIds())
    }

    @Test
    fun `generation replacement failure keeps APPLIED sources and later converges forward`() = runBlocking {
        setupAppliedPair()
        val failing = DatabaseRestoreCoordinator(context, context.contentResolver, FailingFiles(failWriteName = "oj-nexus-data-generation.tmp"))

        val outcome = failing.restoreBeforeRoomOpen()

        assertTrue("outcome=$outcome", outcome is RestoreOutcome.Blocked)
        assertEquals("old-generation", generation.readText().trim())
        assertTrue(journal.isFile)
        assertTrue(File(databaseFile.path + ".restore-rollback").isFile)
        assertEquals(setOf("new-row"), readProblemIds())
        assertTrue(DatabaseRestoreCoordinator(context).restoreBeforeRoomOpen() is RestoreOutcome.Applied)
        assertEquals("new-generation", generation.readText().trim())
        assertEquals(setOf("new-row"), readProblemIds())
    }

    @Test
    fun `cleanup failure after generation is forward only and later converges`() = runBlocking {
        setupAppliedPair()
        val failing = DatabaseRestoreCoordinator(
            context,
            context.contentResolver,
            FailingFiles(failDeleteName = "oj-nexus-pending-restore.db"),
        )

        val outcome = failing.restoreBeforeRoomOpen()

        assertTrue("outcome=$outcome", outcome is RestoreOutcome.Blocked)
        assertEquals("new-generation", generation.readText().trim())
        assertTrue(journal.isFile)
        assertEquals(setOf("new-row"), readProblemIds())
        assertTrue(DatabaseRestoreCoordinator(context).restoreBeforeRoomOpen() is RestoreOutcome.Applied)
        assertEquals("new-generation", generation.readText().trim())
        assertEquals(setOf("new-row"), readProblemIds())
    }

    @Test
    fun `rollback intent write failure performs no destination mutation`() {
        generation.writeText("old-generation")
        databaseFile.delete()
        File(databaseFile.path + ".restore-rollback").writeBytes(byteArrayOf(4, 5, 6))
        File(context.filesDir, "oj-nexus-pending-restore.db").writeBytes(byteArrayOf(7, 8, 9))
        writeJournal(RestoreStage.SWAPPING, "new-generation")
        val rollbackBefore = File(databaseFile.path + ".restore-rollback").readBytes()
        val fault = FailingFiles(failWriteName = "oj-nexus-restore.journal.tmp")

        val outcome = DatabaseRestoreCoordinator(context, context.contentResolver, fault).restoreBeforeRoomOpen()

        assertTrue("outcome=$outcome", outcome is RestoreOutcome.Blocked)
        assertFalse(databaseFile.exists())
        assertEquals(rollbackBefore.toList(), File(databaseFile.path + ".restore-rollback").readBytes().toList())
        assertEquals(0, fault.destinationMutations)
    }

    @Test
    fun `rollback preservation copy failure aborts while keeping old sqlite usable`() = runBlocking {
        val old = createDatabase(OjNexusDatabase.DATABASE_NAME, "old-row")
        old.close()
        val oldBytes = databaseFile.readBytes()
        generation.writeText("old-generation")
        val candidate = createDatabase("rv-copy-candidate.db", "new-row")
        candidate.close()
        context.getDatabasePath("rv-copy-candidate.db").copyTo(File(context.filesDir, "oj-nexus-pending-restore.db"))
        writeJournal(RestoreStage.STAGED, "new-generation")
        val fault = FailingFiles(failCopyName = "${databaseFile.name}.restore-rollback.tmp")

        val result = DatabaseRestoreCoordinator(context, context.contentResolver, fault).restoreBeforeRoomOpen()

        assertTrue("outcome=$result", result is RestoreOutcome.Rejected)
        assertEquals(oldBytes.toList(), databaseFile.readBytes().toList())
        assertEquals(setOf("old-row"), readProblemIds())
        assertEquals("old-generation", generation.readText().trim())
        assertFalse(File(context.filesDir, "oj-nexus-pending-restore.db").exists())
    }

    @Test
    fun `saving rollback intent write failure has zero destructive calls`() = runBlocking {
        val old = createDatabase(OjNexusDatabase.DATABASE_NAME, "old-row")
        old.close()
        val oldBytes = databaseFile.readBytes()
        generation.writeText("old-generation")
        val newDb = createDatabase("rv-save-intent.db", "new-row")
        newDb.close()
        context.getDatabasePath("rv-save-intent.db").copyTo(File(context.filesDir, "oj-nexus-pending-restore.db"))
        writeJournal(RestoreStage.STAGED, "new-generation")
        val fault = FailingFiles(failWriteName = "oj-nexus-restore.journal.tmp")

        val outcome = DatabaseRestoreCoordinator(context, context.contentResolver, fault).restoreBeforeRoomOpen()

        assertTrue("outcome=$outcome", outcome is RestoreOutcome.Blocked)
        assertEquals(0, fault.destinationMutations)
        assertEquals(oldBytes.toList(), databaseFile.readBytes().toList())
        assertEquals(RestoreStage.STAGED.name, Properties().also { p -> journal.inputStream().use(p::load) }.getProperty("stage"))
        assertTrue(File(context.filesDir, "oj-nexus-pending-restore.db").isFile)
    }

    @Test
    fun `readiness permits clean install but orphan evidence never mints generation`() {
        val clean = DatabaseRestoreCoordinator(context).resolveBeforeRoomOpen()
        assertTrue("readiness=$clean", clean is RestoreReadiness.Ready)
        assertTrue(generation.readText().trim().isNotEmpty())

        generation.delete()
        File(databaseFile.path + ".restore-candidate").writeBytes(byteArrayOf(1, 2, 3))
        val blocked = DatabaseRestoreCoordinator(context).resolveBeforeRoomOpen()

        assertTrue("readiness=$blocked", blocked is RestoreReadiness.Blocked)
        assertFalse(generation.exists())
        assertTrue(File(databaseFile.path + ".restore-candidate").exists())
    }

    @Test
    fun `legacy split sidecar sources block and preserve each byte source`() = runBlocking {
        val target = createDatabase(OjNexusDatabase.DATABASE_NAME, "live-main")
        target.close()
        val liveMain = databaseFile.readBytes()
        val rollbackWal = File(databaseFile.path + "-wal.restore-rollback").apply { writeBytes(byteArrayOf(4, 5, 6)) }
        writeJournal(RestoreStage.SWAPPING, "attempted")

        val first = DatabaseRestoreCoordinator(context).restoreBeforeRoomOpen()

        assertTrue("outcome=$first", first is RestoreOutcome.Blocked)
        assertEquals(liveMain.toList(), databaseFile.readBytes().toList())
        assertEquals(listOf<Byte>(4, 5, 6), rollbackWal.readBytes().toList())

        journal.delete()
        File(databaseFile.path + "-wal.restore-rollback").delete()
        val rollbackMain = File(databaseFile.path + ".restore-rollback").apply { writeBytes(liveMain) }
        databaseFile.delete()
        val liveWal = File(databaseFile.path + "-wal").apply { writeBytes(byteArrayOf(7, 8, 9)) }
        writeJournal(RestoreStage.SWAPPING, "attempted")

        val second = DatabaseRestoreCoordinator(context).restoreBeforeRoomOpen()

        assertTrue("outcome=$second", second is RestoreOutcome.Blocked)
        assertFalse(databaseFile.exists())
        assertEquals(liveMain.toList(), rollbackMain.readBytes().toList())
        assertEquals(listOf<Byte>(7, 8, 9), liveWal.readBytes().toList())
    }

    @Test
    fun `legacy swapping without rollback source blocks and preserves staged import`() {
        val pending = File(context.filesDir, "oj-nexus-pending-restore.db").apply { writeBytes(byteArrayOf(9, 8, 7)) }
        writeJournal(RestoreStage.SWAPPING, "attempted")

        val result = DatabaseRestoreCoordinator(context).resolveBeforeRoomOpen()

        assertTrue("readiness=$result", result is RestoreReadiness.Blocked)
        assertTrue(pending.isFile)
        assertTrue(journal.isFile)
        assertFalse(generation.exists())
    }

    @Test
    fun `successful rollback returns and retains existing old generation`() = runBlocking {
        val fixture = createWalOnlyOldFixture("wal-rollback-success")
        val rollback = File(databaseFile.path + ".restore-rollback")
        fixture.main.copyTo(rollback)
        fixture.wal.copyTo(File(databaseFile.path + "-wal.restore-rollback"))
        fixture.shm?.copyTo(File(databaseFile.path + "-shm.restore-rollback"))
        databaseFile.delete()
        generation.writeText("old-generation")
        val pending = File(context.filesDir, "oj-nexus-pending-restore.db")
        val candidate = createDatabase("rv-rollback-new.db", "new-row")
        candidate.close()
        context.getDatabasePath("rv-rollback-new.db").copyTo(pending)
        writeJournal(RestoreStage.SWAPPING, "attempted-new-generation")

        val readiness = DatabaseRestoreCoordinator(context).resolveBeforeRoomOpen()

        assertTrue("readiness=$readiness", readiness is RestoreReadiness.Ready)
        assertEquals("old-generation", (readiness as RestoreReadiness.Ready).generation)
        assertTrue(readiness.outcome is RestoreOutcome.RolledBack)
        assertEquals("old-generation", generation.readText().trim())
        assertEquals(setOf("old-row"), readProblemIds())
    }

    @Test
    fun `each rollback destination failure preserves source set and reconstructed starts do not retry`() = runBlocking {
        val fixture = createWalOnlyOldFixture("wal-rollback-success")
        assertTrue("a real WAL fixture is required for WAL destination coverage", fixture.wal.length() > 32L)
        assertTrue("SQLite should create a SHM source while the WAL database is open", fixture.shm?.isFile == true)
        listOf(databaseFile.name, databaseFile.name + "-wal", databaseFile.name + "-shm").forEach { destinationName ->
            recoveryPaths().forEach { it.delete() }
            val rollback = File(databaseFile.path + ".restore-rollback")
            fixture.main.copyTo(rollback)
            fixture.wal.copyTo(File(databaseFile.path + "-wal.restore-rollback"))
            fixture.shm?.copyTo(File(databaseFile.path + "-shm.restore-rollback"))
            val sourceSet = listOf(rollback, File(databaseFile.path + "-wal.restore-rollback"), File(databaseFile.path + "-shm.restore-rollback"))
                .filter { it.isFile }
            val sourceHashes = sourceSet.associate { it.name to it.sha256() }
            generation.writeText("old-generation")
            File(context.filesDir, "oj-nexus-pending-restore.db").writeBytes(byteArrayOf(1))
            writeJournal(RestoreStage.SWAPPING, "attempted-generation")

            val first = DatabaseRestoreCoordinator(
                context,
                context.contentResolver,
                FailingFiles(failReplaceName = destinationName),
            ).restoreBeforeRoomOpen()
            assertTrue("destination=$destinationName outcome=$first", first is RestoreOutcome.Blocked)
            assertEquals(RestoreStage.ROLLING_BACK.name, Properties().also { p -> journal.inputStream().use(p::load) }.getProperty("stage"))
            assertEquals(sourceHashes, sourceSet.associate { it.name to it.sha256() })

            val retryFiles = CountingFiles()
            repeat(2) {
                val retry = DatabaseRestoreCoordinator(context, context.contentResolver, retryFiles).restoreBeforeRoomOpen()
                assertTrue("retry=$retry", retry is RestoreOutcome.Blocked)
            }
            assertEquals(0, retryFiles.operations)
            assertEquals(sourceHashes, sourceSet.associate { it.name to it.sha256() })
        }
    }

    private data class WalFixture(val main: File, val wal: File, val shm: File?)

    private suspend fun createWalOnlyOldFixture(name: String): WalFixture {
        val sourcePath = context.getDatabasePath("$name.db")
        sourcePath.parentFile?.mkdirs()
        context.deleteDatabase("$name.db")
        File(sourcePath.path + "-wal").delete()
        File(sourcePath.path + "-shm").delete()
        val db = Room.databaseBuilder(context, OjNexusDatabase::class.java, "$name.db")
            .setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build()
        val sql = db.openHelper.writableDatabase
        sql.query("PRAGMA journal_mode").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("wal", cursor.getString(0).lowercase())
        }
        sql.query("PRAGMA wal_autocheckpoint = 0").use { cursor -> while (cursor.moveToNext()) Unit }
        sql.query("PRAGMA wal_checkpoint(TRUNCATE)").use { cursor -> while (cursor.moveToNext()) Unit }
        db.problemDao().insert(
            ProblemEntity(
                judge = "codeforces", externalId = "old-row", title = "old-row",
                difficulty = 800, createdAt = 1L, updatedAt = 1L,
            ),
        )
        val fixtureMain = File(context.cacheDir, "$name-main.db")
        val fixtureWal = File(context.cacheDir, "$name-wal.db")
        val fixtureShm = File(context.cacheDir, "$name-shm.db")
        sourcePath.copyTo(fixtureMain, overwrite = true)
        File(sourcePath.path + "-wal").copyTo(fixtureWal, overwrite = true)
        File(sourcePath.path + "-shm").takeIf { it.isFile }?.copyTo(fixtureShm, overwrite = true)
        db.close()

        SQLiteDatabase.openDatabase(fixtureMain.path, null, SQLiteDatabase.OPEN_READONLY).use { mainOnly ->
            mainOnly.rawQuery("SELECT external_id FROM problems WHERE external_id = ?", arrayOf("old-row")).use { cursor ->
                assertFalse("the OLD row must depend on WAL", cursor.moveToFirst())
            }
        }
        assertTrue("fixture WAL should contain data frames", fixtureWal.length() > 32L)
        return WalFixture(fixtureMain, fixtureWal, fixtureShm.takeIf { it.isFile })
    }

    private fun File.sha256(): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(readBytes()).joinToString("") { "%02x".format(it) }

    private fun recoveryPaths(): List<File> = listOf(
        databaseFile, File(databaseFile.path + "-wal"), File(databaseFile.path + "-shm"),
        File(databaseFile.path + ".restore-rollback"), File(databaseFile.path + "-wal.restore-rollback"),
        File(databaseFile.path + "-shm.restore-rollback"), File(context.filesDir, "oj-nexus-pending-restore.db"),
        journal, generation,
    )

    private suspend fun setupAppliedPair() {
        val old = createDatabase(OjNexusDatabase.DATABASE_NAME, "old-row")
        old.close()
        generation.writeText("old-generation")
        File(databaseFile.path + ".restore-rollback").writeBytes(databaseFile.readBytes())
        val replacement = createDatabase("rv015-new.db", "new-row")
        replacement.close()
        databaseFile.delete()
        context.getDatabasePath("rv015-new.db").copyTo(databaseFile)
        context.getDatabasePath("rv015-new.db").delete()
        writeJournal(RestoreStage.APPLIED, "new-generation")
    }

    private suspend fun createDatabase(name: String, externalId: String): OjNexusDatabase {
        val db = Room.databaseBuilder(context, OjNexusDatabase::class.java, name)
            .setJournalMode(androidx.room.RoomDatabase.JournalMode.TRUNCATE)
            .build()
        opened += db
        db.problemDao().insert(
            ProblemEntity(
                judge = "codeforces", externalId = externalId, title = externalId,
                difficulty = 800, createdAt = 1L, updatedAt = 1L,
            ),
        )
        return db
    }

    private suspend fun readProblemIds(): Set<String> {
        val db = Room.databaseBuilder(context, OjNexusDatabase::class.java, OjNexusDatabase.DATABASE_NAME)
            .setJournalMode(androidx.room.RoomDatabase.JournalMode.TRUNCATE)
            .build()
        return try { db.problemDao().findByJudge("codeforces").map { it.externalId }.toSet() }
        finally { db.close() }
    }

    private fun writeJournal(stage: RestoreStage, generation: String) {
        val properties = Properties().apply {
            setProperty("stage", stage.name)
            setProperty("generation", generation)
        }
        journal.outputStream().use { properties.store(it, null) }
    }

    private class FailingFiles(
        private val failWriteName: String? = null,
        private val failDeleteName: String? = null,
        private val failCopyName: String? = null,
        private val failReplaceName: String? = null,
    ) : RestoreFileOperations by SystemRestoreFileOperations {
        var destinationMutations = 0

        override fun writeAndSync(target: File, bytes: ByteArray) {
            if (target.name == failWriteName) error("injected write failure")
            SystemRestoreFileOperations.writeAndSync(target, bytes)
        }

        override fun copyAndSync(source: File, target: File) {
            if (target.name == failCopyName) error("injected copy failure")
            destinationMutations++
            SystemRestoreFileOperations.copyAndSync(source, target)
        }

        override fun atomicReplace(source: File, target: File) {
            if (target.name == failReplaceName) error("injected replace failure")
            destinationMutations++
            SystemRestoreFileOperations.atomicReplace(source, target)
        }

        override fun deleteChecked(file: File) {
            if (file.name == failDeleteName) error("injected delete failure")
            SystemRestoreFileOperations.deleteChecked(file)
        }
    }

    private class CountingFiles : RestoreFileOperations by SystemRestoreFileOperations {
        var operations = 0
        override fun copyAndSync(source: File, target: File) { operations++; SystemRestoreFileOperations.copyAndSync(source, target) }
        override fun writeAndSync(target: File, bytes: ByteArray) { operations++; SystemRestoreFileOperations.writeAndSync(target, bytes) }
        override fun atomicReplace(source: File, target: File) { operations++; SystemRestoreFileOperations.atomicReplace(source, target) }
        override fun deleteChecked(file: File) { operations++; SystemRestoreFileOperations.deleteChecked(file) }
    }
}
