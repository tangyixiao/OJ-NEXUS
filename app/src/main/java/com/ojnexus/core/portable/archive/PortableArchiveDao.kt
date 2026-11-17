package com.ojnexus.core.portable.archive

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/** Rows inserted / deleted by [PortableArchiveDao.replaceAll], measured during the transaction. */
data class ArchiveReplaceCounts(
    val rowsUpserted: Int,
    val rowsDeleted: Int,
)

/**
 * Room access for the bounded domain archive. The only write path is [replaceAll], which
 * runs as ONE transaction so a failed replace can never leave half an archive behind.
 */
@Dao
interface PortableArchiveDao {

    @Query("SELECT * FROM portable_archive_packages LIMIT 1")
    suspend fun manifest(): PortableArchivePackageEntity?

    @Query("SELECT COUNT(*) FROM portable_archive_packages")
    suspend fun packageCount(): Int

    /** Records ordered by their position inside the collection, so rebuild is exact. */
    @Query("SELECT * FROM portable_archive_records ORDER BY ordinal ASC, collection ASC, stable_id ASC")
    suspend fun records(): List<PortableArchiveRecordEntity>

    @Query("SELECT COUNT(*) FROM portable_archive_records")
    suspend fun recordCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertManifest(manifest: PortableArchivePackageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecords(records: List<PortableArchiveRecordEntity>): List<Long>

    @Query("DELETE FROM portable_archive_packages")
    suspend fun deleteAllManifests()

    @Query("DELETE FROM portable_archive_records")
    suspend fun deleteAllRecords()

    /**
     * Delete-then-insert of the whole archive inside one transaction.
     *
     * Counts are measured on the live tables: [ArchiveReplaceCounts.rowsDeleted] is the
     * number of package + record rows that existed before the delete,
     * [ArchiveReplaceCounts.rowsUpserted] is the number of rows actually inserted
     * (record rows plus one for the manifest when it is present).
     */
    @Transaction
    suspend fun replaceAll(
        manifest: PortableArchivePackageEntity?,
        records: List<PortableArchiveRecordEntity>,
    ): ArchiveReplaceCounts {
        val rowsDeleted = packageCount() + recordCount()
        deleteAllManifests()
        deleteAllRecords()
        var rowsUpserted = 0
        if (manifest != null) {
            insertManifest(manifest)
            rowsUpserted += 1
        }
        if (records.isNotEmpty()) {
            rowsUpserted += insertRecords(records).size
        }
        return ArchiveReplaceCounts(rowsUpserted = rowsUpserted, rowsDeleted = rowsDeleted)
    }

    /** Wipe both archive tables inside one transaction. */
    @Transaction
    suspend fun clearAll() {
        deleteAllRecords()
        deleteAllManifests()
    }
}
