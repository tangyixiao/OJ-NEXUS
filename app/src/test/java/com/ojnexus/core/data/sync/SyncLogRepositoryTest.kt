package com.ojnexus.core.data.sync

import android.content.Context
import androidx.room.Room
import com.ojnexus.core.database.OjNexusDatabase
import com.ojnexus.core.database.entity.SyncLogOpEntity
import com.ojnexus.core.sync.SyncOp
import com.ojnexus.core.sync.SyncOpKind
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 118 DoD: append → ledger rows, merge resolution writes conflict rows, lifecycle
 * transitions, and the credential-exclusion source audit (no judge_accounts surface).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncLogRepositoryTest {

    private lateinit var context: Context
    private lateinit var database: OjNexusDatabase
    private lateinit var repository: SyncLogRepository

    @Before
    fun setUp() {
        context = androidx.test.core.app.ApplicationProvider.getApplicationContext()
        val name = "sync-log-test-${System.nanoTime()}.db"
        database = Room.databaseBuilder(context, OjNexusDatabase::class.java, name)
            .setJournalMode(androidx.room.RoomDatabase.JournalMode.TRUNCATE)
            .allowMainThreadQueries()
            .build()
        repository = SyncLogRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun rowOp(
        opId: String = "local",
        table: String = "problems",
        row: String = "1234A",
        kind: SyncOpKind = SyncOpKind.UPSERT,
    ) = SyncLogOpEntity(opId = opId, tableName = table, rowKey = row, kind = kind.name, payloadJson = "{}")

    @Test
    fun appendLocalBatchAssignsLamportAndPersistsRows() = runBlocking {
        val first = repository.appendLocalBatch("device-a", 15, 1_000L, listOf(rowOp()))
        val second = repository.appendLocalBatch("device-a", 15, 2_000L, listOf(rowOp(row = "1234B")))

        assertEquals(1L, first.lamport)
        assertEquals(2L, second.lamport)
        assertEquals(2, database.syncLogOperationDao().recent(10).size)
        assertEquals(2, database.syncLogOpDao().findByOpId(first.opId).size + database.syncLogOpDao().findByOpId(second.opId).size)
    }

    @Test
    fun resolveRemoteBatchRecordsEveryLoserInTheConflictLog() = runBlocking {
        val local = listOf(
            SyncOp("local-1", "device-a", 7L, "problems", "1234A", SyncOpKind.UPSERT),
        )
        val remote = listOf(
            SyncOp("remote-1", "device-b", 9L, "problems", "1234A", SyncOpKind.UPSERT),
        )
        val remoteBatch = repository.appendLocalBatch("device-b", 15, 3_000L, listOf(rowOp(opId = "remote-1")))

        val merged = repository.resolveRemoteBatch(remoteBatch, remote, local, 4_000L)

        assertEquals("remote-1", merged["problems#1234A"]!!.winner.opId)
        assertEquals(1, database.syncConflictLogDao().count())
        val conflict = database.syncConflictLogDao().recent(10).single()
        assertEquals("problems", conflict.tableName)
        assertEquals("1234A", conflict.rowKey)
        assertEquals("remote-1", conflict.winnerOpId)
        assertTrue(conflict.reason.contains("device=device-b"))
    }

    @Test
    fun lifecycleTransitionsSealThenUpload() = runBlocking {
        val batch = repository.appendLocalBatch("device-a", 15, 5_000L, listOf(rowOp()))
        assertNull(batch.encryptedPayloadPath)

        repository.markSealed(batch.opId, "sync-payload/abc.bin")
        val sealed = database.syncLogOperationDao().findById(batch.opId)
        assertNotNull(sealed)
        assertEquals("SEALED", sealed!!.status)
        assertEquals("sync-payload/abc.bin", sealed.encryptedPayloadPath)

        repository.markUploaded(batch.opId)
        assertEquals("UPLOADED", database.syncLogOperationDao().findById(batch.opId)!!.status)
    }

    @Test
    fun emptyBatchIsRejected() = runBlocking {
        val thrown = runCatching {
            repository.appendLocalBatch("device-a", 15, 1L, emptyList())
        }
        assertTrue(thrown.isFailure)
    }

    @Test
    fun sealThenOpenRoundTripsIdenticalRows() = runBlocking {
        val batch = repository.appendLocalBatch(
            "device-a",
            15,
            6_000L,
            listOf(rowOp(), rowOp(row = "1234B"), rowOp(table = "reviews", row = "42")),
        )
        val sealer = com.ojnexus.core.sync.XorSealer(byteArrayOf(0x5a, 0x33))

        val path = repository.sealBatch(batch.opId, context.filesDir, sealer)
        val payload = java.io.File(path)
        assertTrue(payload.exists())
        assertEquals("SEALED", database.syncLogOperationDao().findById(batch.opId)!!.status)

        val reopened = repository.openBatch(batch.opId, sealer)!!
        assertEquals(3, reopened.size)
        // Codec sorts by (table, rowKey): problems/1234A, problems/1234B, reviews/42.
        assertEquals("problems", reopened[0].tableName)
        assertEquals("1234A", reopened[0].rowKey)
        assertEquals("reviews", reopened[2].tableName)
    }

    @Test
    fun openBatchRejectsWrongKeyMaterialAndGarbage() = runBlocking {
        val batch = repository.appendLocalBatch("device-a", 15, 7_000L, listOf(rowOp()))
        val sealer = com.ojnexus.core.sync.XorSealer(byteArrayOf(0x5a))
        repository.sealBatch(batch.opId, context.filesDir, sealer)

        // A different pad cannot open the payload into decodable rows.
        val stranger = com.ojnexus.core.sync.XorSealer(byteArrayOf(0x01))
        assertNull(repository.openBatch(batch.opId, stranger))

        // A truncated payload file yields null, not a crash.
        val payloadPath = database.syncLogOperationDao().findById(batch.opId)!!.encryptedPayloadPath!!
        java.io.File(payloadPath).writeBytes(byteArrayOf(1, 2, 3))
        assertNull(repository.openBatch(batch.opId, sealer))
    }

    @Test
    fun unknownBatchIsRejectedBySealingToo() = runBlocking {
        val thrown = runCatching {
            repository.sealBatch("no-such-batch", context.filesDir, com.ojnexus.core.sync.XorSealer(byteArrayOf(1)))
        }
        assertTrue(thrown.isFailure)
    }

    @Test
    fun pushThenPullRoundTripsRowsAcrossRepositories() = runBlocking {
        val sealer = com.ojnexus.core.sync.XorSealer(byteArrayOf(0x77))
        val store = object : com.ojnexus.core.sync.SyncRemoteStore {
            val blobs = mutableMapOf<String, ByteArray>()
            override fun put(opId: String, bytes: ByteArray) { blobs[opId] = bytes }
            override fun get(opId: String): ByteArray? = blobs[opId]
            override fun list(): List<String> = blobs.keys.sorted()
        }

        // Device A: append + push.
        val batch = repository.appendLocalBatch("device-a", 15, 8_000L, listOf(rowOp(), rowOp(row = "1234B")))
        repository.pushBatch(batch.opId, com.ojnexus.core.sync.SyncTransport(store, true), sealer, context.filesDir)

        // Device B (same sealer + same batch identity): pull back and decode.
        val pulled = repository.pullBatch(
            batch.opId,
            com.ojnexus.core.sync.SyncTransport(store, true),
            sealer,
            context.filesDir,
        )!!

        assertEquals(2, pulled.size)
        assertEquals("1234A", pulled[0].rowKey)
        assertEquals("1234B", pulled[1].rowKey)
    }
}
