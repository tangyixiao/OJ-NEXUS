package com.ojnexus.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import com.ojnexus.core.database.OjNexusDatabase
import com.ojnexus.core.database.entity.ProblemEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseMaintenanceRepositoryTest {

    private lateinit var context: Context
    private lateinit var database: OjNexusDatabase
    private lateinit var repository: DatabaseMaintenanceRepository
    private lateinit var databaseName: String

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "maintenance-test-${System.nanoTime()}.db"
        database = Room.databaseBuilder(context, OjNexusDatabase::class.java, databaseName)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .allowMainThreadQueries()
            .build()
        repository = DatabaseMaintenanceRepository(
            database = database,
            databaseFile = context.getDatabasePath(databaseName),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun seedProblem(externalId: String) = kotlinx.coroutines.runBlocking {
        database.problemDao().insert(
            ProblemEntity(
                judge = "codeforces",
                externalId = externalId,
                title = "T$externalId",
                difficulty = null,
                createdAt = 1L,
                updatedAt = 1L,
            ),
        )
    }

    @Test
    fun statsAreHonestFromPragmaAndFiles() = runBlocking {
        seedProblem("1A")
        seedProblem("1B")
        val stats = repository.maintenanceStats()
        assertEquals(2, stats.rowCounts["problems"])
        assertEquals(0, stats.rowCounts["reviews"])
        assertTrue(stats.pageSize > 0)
        assertTrue(stats.pageCount > 0)
        assertEquals(stats.pageSize * stats.pageCount, stats.dbBytes)
        assertTrue(stats.dbFileBytes > 0)
        assertEquals(DatabaseMaintenanceRepository.MAINTENANCE_TABLES.size, stats.rowCounts.size)
    }

    @Test
    fun vacuumCompletesAndKeepsRows() = runBlocking {
        seedProblem("1A")
        repository.vacuum()
        assertEquals(1, database.problemDao().count())
        val stats = repository.maintenanceStats()
        assertTrue(stats.pageCount > 0)
        assertTrue(stats.dbBytes > 0)
    }

    @Test
    fun formatBytesFormatsHonestRanges() {
        assertEquals("?", DatabaseMaintenanceRepository.formatBytes(-1L))
        assertEquals("512 B", DatabaseMaintenanceRepository.formatBytes(512L))
        assertEquals("1.0 KiB", DatabaseMaintenanceRepository.formatBytes(1024L))
        assertEquals("1.0 MiB", DatabaseMaintenanceRepository.formatBytes(1024L * 1024))
        assertEquals("1.00 GiB", DatabaseMaintenanceRepository.formatBytes(1024L * 1024 * 1024))
    }
}
