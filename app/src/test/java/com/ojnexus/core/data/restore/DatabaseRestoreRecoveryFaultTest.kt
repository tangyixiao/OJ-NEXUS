package com.ojnexus.core.data.restore

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ojnexus.core.database.OjNexusDatabase
import com.ojnexus.core.database.entity.ProblemEntity
import java.io.File
import java.io.FileOutputStream
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
    fun `legacy split real WAL source pairs block and preserve both sides`() = runBlocking {
        val fixture = createWalOnlyOldFixture("legacy-real-split")
        fixture.main.copyTo(databaseFile, overwrite = true)
        val liveMainHash = databaseFile.sha256()
        val rollbackWal = File(databaseFile.path + "-wal.restore-rollback").apply { fixture.wal.copyTo(this, overwrite = true) }
        writeJournal(RestoreStage.SWAPPING, "attempted")

        val first = DatabaseRestoreCoordinator(context).restoreBeforeRoomOpen()

        assertTrue("outcome=$first", first is RestoreOutcome.Blocked)
        assertEquals(liveMainHash, databaseFile.sha256())
        assertEquals(fixture.wal.sha256(), rollbackWal.sha256())
        assertTrue(rollbackWal.length() > 32L)

        journal.delete()
        rollbackWal.delete()
        val rollbackMain = File(databaseFile.path + ".restore-rollback").apply { fixture.main.copyTo(this, overwrite = true) }
        databaseFile.delete()
        val liveWal = File(databaseFile.path + "-wal").apply { fixture.wal.copyTo(this, overwrite = true) }
        writeJournal(RestoreStage.SWAPPING, "attempted")

        val second = DatabaseRestoreCoordinator(context).restoreBeforeRoomOpen()

        assertTrue("outcome=$second", second is RestoreOutcome.Blocked)
        assertFalse(databaseFile.exists())
        assertEquals(fixture.main.sha256(), rollbackMain.sha256())
        assertEquals(fixture.wal.sha256(), liveWal.sha256())
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
        val fixture = createWalOnlyOldFixture("wal-dest")
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
        val fixture = createWalOnlyOldFixture("wal-dest")
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

    @Test
    fun `staged import removes real old WAL before installing new identity`() = runBlocking {
        val fixture = createWalOnlyOldFixture("wal-staged-new")
        fixture.main.copyTo(databaseFile, overwrite = true)
        fixture.wal.copyTo(File(databaseFile.path + "-wal"), overwrite = true)
        fixture.shm?.copyTo(File(databaseFile.path + "-shm"), overwrite = true)
        generation.writeText("old-generation")
        val fresh = createDatabase("wal-staged-new-candidate.db", "new-row")
        fresh.close()
        context.getDatabasePath("wal-staged-new-candidate.db").copyTo(File(context.filesDir, "oj-nexus-pending-restore.db"))
        writeJournal(RestoreStage.STAGED, "new-generation")

        val ready = DatabaseRestoreCoordinator(context).resolveBeforeRoomOpen()

        assertTrue("readiness=$ready", ready is RestoreReadiness.Ready)
        assertEquals("new-generation", (ready as RestoreReadiness.Ready).generation)
        assertTrue(ready.outcome is RestoreOutcome.Applied)
        assertEquals(setOf("new-row"), readProblemIds())
        assertEquals("new-generation", generation.readText().trim())
    }

    @Test
    fun `failure replacing main after live sidecar removal rolls back OLD WAL pair`() = runBlocking {
        val fixture = createWalOnlyOldFixture("wal-delete-fault")
        fixture.main.copyTo(databaseFile, overwrite = true)
        fixture.wal.copyTo(File(databaseFile.path + "-wal"), overwrite = true)
        fixture.shm?.copyTo(File(databaseFile.path + "-shm"), overwrite = true)
        generation.writeText("old-generation")
        val rollbackMain = File(databaseFile.path + ".restore-rollback")
        val rollbackWal = File(databaseFile.path + "-wal.restore-rollback")
        val rollbackShm = File(databaseFile.path + "-shm.restore-rollback")
        val fresh = createDatabase("wal-delete-fault-candidate.db", "new-row")
        fresh.close()
        context.getDatabasePath("wal-delete-fault-candidate.db").copyTo(File(context.filesDir, "oj-nexus-pending-restore.db"))
        writeJournal(RestoreStage.STAGED, "new-generation")

        val result = DatabaseRestoreCoordinator(context, context.contentResolver, FailingFiles(failReplaceName = databaseFile.name))
            .resolveBeforeRoomOpen()

        assertTrue("readiness=$result", result is RestoreReadiness.Ready)
        assertEquals("old-generation", (result as RestoreReadiness.Ready).generation)
        assertTrue(result.outcome is RestoreOutcome.RolledBack)
        assertEquals(fixture.main.sha256(), databaseFile.sha256())
        assertEquals(fixture.wal.sha256(), File(databaseFile.path + "-wal").sha256())
        if (fixture.shm?.isFile == true) assertTrue(File(databaseFile.path + "-shm").isFile)
        assertFalse(rollbackMain.exists())
        assertFalse(rollbackWal.exists())
        assertFalse(rollbackShm.exists())
        assertEquals(setOf("old-row"), readProblemIds())
    }

    @Test
    fun `invalid staged import with unowned rollback or candidate blocks without mutation`() = runBlocking {
        listOf(File(databaseFile.path + ".restore-rollback"), File(databaseFile.path + ".restore-candidate")).forEach { artifact ->
            recoveryPaths().forEach { it.delete() }
            File(context.filesDir, "oj-nexus-pending-restore.db").writeBytes(byteArrayOf(0, 1, 2, 3))
            artifact.writeBytes(byteArrayOf(9, 8, 7, 6))
            writeJournal(RestoreStage.STAGED, "attempted-generation")
            val before = listOf(artifact, File(context.filesDir, "oj-nexus-pending-restore.db"), journal)
                .associate { it.name to it.sha256() }
            val fault = CountingFiles()

            val readiness = DatabaseRestoreCoordinator(context, context.contentResolver, fault).resolveBeforeRoomOpen()

            assertTrue("artifact=${artifact.name} readiness=$readiness", readiness is RestoreReadiness.Blocked)
            assertFalse(generation.exists())
            assertEquals(before, listOf(artifact, File(context.filesDir, "oj-nexus-pending-restore.db"), journal)
                .associate { it.name to it.sha256() })
            assertEquals(0, fault.operations)
        }
    }

    @Test
    fun `rejected missing live target with rollback evidence blocks and preserves bytes`() {
        val rollback = File(databaseFile.path + ".restore-rollback").apply { writeBytes(byteArrayOf(4, 3, 2, 1)) }
        writeJournal(RestoreStage.REJECTED, "attempted-generation")
        val before = rollback.sha256()
        val fault = CountingFiles()

        val readiness = DatabaseRestoreCoordinator(context, context.contentResolver, fault).resolveBeforeRoomOpen()

        assertTrue("readiness=$readiness", readiness is RestoreReadiness.Blocked)
        assertFalse(generation.exists())
        assertEquals(before, rollback.sha256())
        assertEquals(0, fault.operations)
    }

    @Test
    fun `interrupted installing rolls back whether pending import remains or not`() = runBlocking {
        val fixture = createWalOnlyOldFixture("installing-old")
        listOf(true, false).forEach { pendingPresent ->
            recoveryPaths().forEach { it.delete() }
            fixture.main.copyTo(File(databaseFile.path + ".restore-rollback"), overwrite = true)
            fixture.wal.copyTo(File(databaseFile.path + "-wal.restore-rollback"), overwrite = true)
            fixture.shm?.copyTo(File(databaseFile.path + "-shm.restore-rollback"), overwrite = true)
            val newDb = createDatabase("installing-new-$pendingPresent.db", "new-row")
            newDb.close()
            context.getDatabasePath("installing-new-$pendingPresent.db").copyTo(databaseFile, overwrite = true)
            generation.writeText("old-generation")
            if (pendingPresent) context.getDatabasePath("installing-new-$pendingPresent.db")
                .copyTo(File(context.filesDir, "oj-nexus-pending-restore.db"), overwrite = true)
            writeJournal(RestoreStage.INSTALLING, "new-generation")

            val ready = DatabaseRestoreCoordinator(context).resolveBeforeRoomOpen()

            assertTrue("pending=$pendingPresent readiness=$ready", ready is RestoreReadiness.Ready)
            assertEquals("old-generation", (ready as RestoreReadiness.Ready).generation)
            assertTrue(ready.outcome is RestoreOutcome.RolledBack)
            assertEquals(setOf("old-row"), readProblemIds())
        }
    }

    @Test
    fun `main WAL and SHM preservation partial copy and sync failures keep live OLD data`() = runBlocking {
        val fixture = createWalOnlyOldFixture("wal-preserve-faults")
        val targets = listOf(
            databaseFile.name + ".restore-rollback.tmp",
            databaseFile.name + "-wal.restore-rollback.tmp",
            databaseFile.name + "-shm.restore-rollback.tmp",
        )
        for ((targetIndex, targetName) in targets.withIndex()) for ((modeIndex, mode) in listOf("partial-copy", "sync-boundary").withIndex()) {
            recoveryPaths().forEach { it.delete() }
            fixture.main.copyTo(databaseFile, overwrite = true)
            fixture.wal.copyTo(File(databaseFile.path + "-wal"), overwrite = true)
            fixture.shm?.copyTo(File(databaseFile.path + "-shm"), overwrite = true)
            generation.writeText("old-generation")
            val candidateName = "preserve-new-$targetIndex-$modeIndex.db"
            val fresh = createDatabase(candidateName, "new-row")
            fresh.close()
            context.getDatabasePath(candidateName)
                .copyTo(File(context.filesDir, "oj-nexus-pending-restore.db"), overwrite = true)
            writeJournal(RestoreStage.STAGED, "new-generation")
            val fault = if (mode == "partial-copy") FailingFiles(partialCopyName = targetName)
                else FailingFiles(syncFailureName = targetName)

            val outcome = DatabaseRestoreCoordinator(context, context.contentResolver, fault).restoreBeforeRoomOpen()

            assertTrue("target=$targetName mode=$mode outcome=$outcome", outcome is RestoreOutcome.Rejected)
            assertEquals(fixture.main.sha256(), databaseFile.sha256())
            assertEquals(fixture.wal.sha256(), File(databaseFile.path + "-wal").sha256())
            assertEquals("old-generation", generation.readText().trim())
            assertEquals(setOf("old-row"), readProblemIds())
            assertFalse(File(context.filesDir, "oj-nexus-pending-restore.db").exists())
        }
    }

    @Test
    fun `materialized saving checkpoint aborts to validated existing OLD`() = runBlocking {
        val old = createDatabase(OjNexusDatabase.DATABASE_NAME, "old-row")
        old.close()
        generation.writeText("old-generation")
        val oldHash = databaseFile.sha256()
        val partial = File(databaseFile.path + ".restore-rollback.tmp").apply { writeBytes(byteArrayOf(4, 5, 6)) }
        File(context.filesDir, "oj-nexus-pending-restore.db").writeBytes(byteArrayOf(1, 2, 3))
        writeJournal(RestoreStage.SAVING_ROLLBACK, "new-generation")

        val readiness = DatabaseRestoreCoordinator(context).resolveBeforeRoomOpen()

        assertTrue("readiness=$readiness", readiness is RestoreReadiness.Ready)
        assertTrue((readiness as RestoreReadiness.Ready).outcome is RestoreOutcome.Rejected)
        assertEquals("old-generation", readiness.generation)
        assertEquals(oldHash, databaseFile.sha256())
        assertFalse(partial.exists())
        assertFalse(journal.exists())
        assertEquals(setOf("old-row"), readProblemIds())
    }

    @Test
    fun `uncertain saving checkpoint and corrupt journal block preserve all evidence`() = runBlocking {
        val partial = File(databaseFile.path + ".restore-rollback.tmp").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        generation.writeText("old-generation")
        writeJournal(RestoreStage.SAVING_ROLLBACK, "new-generation")
        val filesBefore = listOf(partial, generation, journal).associate { it.name to it.sha256() }
        val uncertain = DatabaseRestoreCoordinator(context).resolveBeforeRoomOpen()
        assertTrue("uncertain=$uncertain", uncertain is RestoreReadiness.Blocked)
        assertEquals(filesBefore, listOf(partial, generation, journal).associate { it.name to it.sha256() })
        assertFalse(databaseFile.exists())

        journal.writeText("not-a-journal")
        val corruptHash = journal.sha256()
        val sourceHash = partial.sha256()
        val corrupt = DatabaseRestoreCoordinator(context).resolveBeforeRoomOpen()
        assertTrue("corrupt=$corrupt", corrupt is RestoreReadiness.Blocked)
        assertEquals(corruptHash, journal.sha256())
        assertEquals(sourceHash, partial.sha256())
        assertEquals("old-generation", generation.readText().trim())
    }

    @Test
    fun `successful rolled back marker validates old generation and converges cleanup`() = runBlocking {
        val fixture = createWalOnlyOldFixture("terminal-rollback")
        fixture.main.copyTo(databaseFile, overwrite = true)
        fixture.wal.copyTo(File(databaseFile.path + "-wal"), overwrite = true)
        fixture.shm?.copyTo(File(databaseFile.path + "-shm"), overwrite = true)
        fixture.main.copyTo(File(databaseFile.path + ".restore-rollback"), overwrite = true)
        generation.writeText("old-generation")
        File(context.filesDir, "oj-nexus-pending-restore.db").writeBytes(byteArrayOf(1, 2))
        writeJournal(RestoreStage.ROLLED_BACK, "new-generation", RestoreFailure.FILE_REPLACEMENT_FAILURE)

        val first = DatabaseRestoreCoordinator(context).resolveBeforeRoomOpen()
        val second = DatabaseRestoreCoordinator(context).resolveBeforeRoomOpen()

        assertTrue("first=$first", first is RestoreReadiness.Ready)
        assertEquals("old-generation", (first as RestoreReadiness.Ready).generation)
        assertTrue(first.outcome is RestoreOutcome.RolledBack)
        assertTrue("second=$second", second is RestoreReadiness.Ready)
        assertEquals("old-generation", (second as RestoreReadiness.Ready).generation)
        assertEquals(setOf("old-row"), readProblemIds())
        assertFalse(journal.exists())
    }

    @Test
    fun `legacy rollback failure marker blocks without deleting source`() {
        val rollback = File(databaseFile.path + ".restore-rollback").apply { writeBytes(byteArrayOf(8, 7, 6)) }
        generation.writeText("old-generation")
        writeJournal(RestoreStage.ROLLED_BACK, "new-generation", RestoreFailure.ROLLBACK_FAILURE)
        val hash = rollback.sha256()

        val outcome = DatabaseRestoreCoordinator(context).resolveBeforeRoomOpen()

        assertTrue("outcome=$outcome", outcome is RestoreReadiness.Blocked)
        assertEquals(hash, rollback.sha256())
        assertEquals("old-generation", generation.readText().trim())
    }

    @Test
    fun `invalid applied target requires inspection and preserves metadata and sources`() {
        listOf(false, true).forEach { corrupt ->
            recoveryPaths().forEach { it.delete() }
            if (corrupt) databaseFile.writeBytes(byteArrayOf(7, 6, 5, 4))
            val rollback = File(databaseFile.path + ".restore-rollback").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            generation.writeText("old-generation")
            writeJournal(RestoreStage.APPLIED, "new-generation")
            val journalHash = journal.sha256()
            val rollbackHash = rollback.sha256()

            val outcome = DatabaseRestoreCoordinator(context).resolveBeforeRoomOpen()

            assertTrue("corrupt=$corrupt outcome=$outcome", outcome is RestoreReadiness.Blocked)
            assertEquals(RecoveryDisposition.INSPECTION_REQUIRED, (outcome as RestoreReadiness.Blocked).recovery)
            assertEquals("old-generation", generation.readText().trim())
            assertEquals(journalHash, journal.sha256())
            assertEquals(rollbackHash, rollback.sha256())
        }
    }

    @Test
    fun `generation atomic replace failure retains applied forward state`() = runBlocking {
        setupAppliedPair()
        val result = DatabaseRestoreCoordinator(context, context.contentResolver,
            FailingFiles(failReplaceName = "oj-nexus-data-generation")).restoreBeforeRoomOpen()
        assertTrue("outcome=$result", result is RestoreOutcome.Blocked)
        assertEquals("old-generation", generation.readText().trim())
        assertTrue(journal.isFile)
        assertEquals(setOf("new-row"), readProblemIds())
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

    private fun writeJournal(stage: RestoreStage, generation: String, detail: RestoreFailure? = null) {
        val properties = Properties().apply {
            setProperty("stage", stage.name)
            setProperty("generation", generation)
            detail?.let { setProperty("detail", it.name) }
        }
        journal.outputStream().use { properties.store(it, null) }
    }

    private class FailingFiles(
        private val failWriteName: String? = null,
        private val failDeleteName: String? = null,
        private val failCopyName: String? = null,
        private val failReplaceName: String? = null,
        private val partialCopyName: String? = null,
        private val syncFailureName: String? = null,
    ) : RestoreFileOperations by SystemRestoreFileOperations {
        var destinationMutations = 0
        private var deleteFailureUsed = false
        private var replaceFailureUsed = false

        override fun writeAndSync(target: File, bytes: ByteArray) {
            if (target.name == failWriteName) error("injected write failure")
            SystemRestoreFileOperations.writeAndSync(target, bytes)
        }

        override fun copyAndSync(source: File, target: File) {
            destinationMutations++
            if (target.name == failCopyName) error("injected copy failure before copy")
            if (target.name == partialCopyName) {
                target.parentFile?.mkdirs()
                val bytes = source.readBytes()
                target.writeBytes(bytes.copyOf((bytes.size / 2).coerceAtLeast(1)))
                error("injected partial destination copy")
            }
            if (target.name == syncFailureName) {
                target.parentFile?.mkdirs()
                FileOutputStream(target).use { output ->
                    source.inputStream().use { it.copyTo(output) }
                    output.flush()
                    error("injected sync-boundary failure before fd.sync")
                }
            }
            SystemRestoreFileOperations.copyAndSync(source, target)
        }

        override fun atomicReplace(source: File, target: File) {
            if (target.name == failReplaceName && !replaceFailureUsed) {
                replaceFailureUsed = true
                error("injected replace failure")
            }
            destinationMutations++
            SystemRestoreFileOperations.atomicReplace(source, target)
        }

        override fun deleteChecked(file: File) {
            if (file.exists()) destinationMutations++
            if (file.name == failDeleteName && !deleteFailureUsed) {
                deleteFailureUsed = true
                error("injected delete failure")
            }
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
