package com.ojnexus.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ojnexus.core.database.entity.SyncModuleStatus
import com.ojnexus.core.database.entity.SyncOperationEntity
import com.ojnexus.core.database.entity.SyncOperationModuleEntity
import com.ojnexus.core.database.entity.SyncOperationStatus
import com.ojnexus.core.data.sync.SyncStage
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncOperationDaoTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "sync-operation-dao-test.db"
    private val database = Room.databaseBuilder(context, OjNexusDatabase::class.java, dbName)
        .allowMainThreadQueries()
        .build()

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(dbName)
        File(context.getDatabasePath(dbName).path + "-wal").delete()
        File(context.getDatabasePath(dbName).path + "-shm").delete()
    }

    @Test
    fun `operation lifecycle stores committed module outcomes and stable statuses`() = runBlocking {
        val dao = database.syncOperationDao()
        val operationId = dao.openOperation(
            SyncOperationEntity(
                judge = "codeforces",
                accountId = 7,
                dataGeneration = "generation-a",
                startedAt = 1_000,
            ),
        )
        dao.appendCommittedModule(
            SyncOperationModuleEntity(
                operationId = operationId,
                stage = SyncStage.PROFILE.name,
                status = SyncModuleStatus.SUCCESS.name,
                attemptedCount = 1,
                importedCount = 1,
                updatedCount = 0,
                completedAt = 1_100,
            ),
        )
        dao.closeOperation(
            operationId = operationId,
            status = SyncOperationStatus.PARTIAL.name,
            finishedAt = 1_200,
            errorType = "Network",
        )

        val row = dao.observeRecentByJudge("codeforces", limit = 10).first().single()
        assertEquals(SyncOperationStatus.PARTIAL.name, row.operation.status)
        assertEquals("generation-a", row.operation.dataGeneration)
        assertEquals("Network", row.operation.lastErrorType)
        assertEquals(1, row.modules.size)
        assertEquals(SyncStage.PROFILE.name, row.modules.single().stage)
        assertEquals(SyncModuleStatus.SUCCESS.name, row.modules.single().status)
        assertEquals("STALE_GENERATION", SyncOperationStatus.STALE_GENERATION.name)
        assertNotNull(dao.findById(operationId))
    }

    @Test
    fun `retention prunes only old completed rows for selected judge`() = runBlocking {
        val dao = database.syncOperationDao()
        val oldCompleted = dao.openOperation(operation("codeforces", 1_000))
        val newestCompleted = dao.openOperation(operation("codeforces", 2_000))
        val newestCompletedTwo = dao.openOperation(operation("codeforces", 3_000))
        val active = dao.openOperation(operation("codeforces", 4_000))
        val otherJudge = dao.openOperation(operation("luogu", 100))
        dao.closeOperation(oldCompleted, SyncOperationStatus.SUCCESS.name, 1_100)
        dao.closeOperation(newestCompleted, SyncOperationStatus.ERROR.name, 2_100, "ServerError")
        dao.closeOperation(newestCompletedTwo, SyncOperationStatus.CANCELLED.name, 3_100)
        dao.closeOperation(otherJudge, SyncOperationStatus.SUCCESS.name, 200)

        dao.pruneCompletedHistory("codeforces", keepCount = 2)

        assertEquals(null, dao.findById(oldCompleted))
        assertTrue(dao.findById(newestCompleted) != null)
        assertTrue(dao.findById(newestCompletedTwo) != null)
        assertEquals(SyncOperationStatus.RUNNING.name, dao.findById(active)?.operation?.status)
        assertTrue(dao.findById(otherJudge)?.operation?.status == SyncOperationStatus.SUCCESS.name)
    }

    @Test
    fun `recent history is deterministic and modules cascade with their operation`() = runBlocking {
        val dao = database.syncOperationDao()
        val older = dao.openOperation(operation("codeforces", 1_000))
        val newer = dao.openOperation(operation("codeforces", 3_000))
        val middle = dao.openOperation(operation("codeforces", 2_000))
        dao.appendCommittedModule(
            SyncOperationModuleEntity(
                operationId = older,
                stage = SyncStage.RATING.name,
                status = SyncModuleStatus.ERROR.name,
                failureType = "Parse",
            ),
        )

        val ordered = dao.observeRecentByJudge("codeforces", limit = 10).first()
        assertEquals(listOf(newer, middle, older), ordered.map { it.operation.id })
        dao.deleteById(older)
        assertEquals(null, dao.findById(older))
        database.openHelper.writableDatabase.query(
            "SELECT COUNT(*) FROM sync_operation_modules WHERE operation_id = $older",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
    }

    private fun operation(judge: String, startedAt: Long) = SyncOperationEntity(
        judge = judge,
        accountId = 9,
        dataGeneration = "generation-$judge",
        startedAt = startedAt,
    )
}
