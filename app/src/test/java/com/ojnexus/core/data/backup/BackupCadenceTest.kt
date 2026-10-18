package com.ojnexus.core.data.backup

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCadenceTest {

    @Test
    fun `fromStored accepts known names`() {
        assertEquals(BackupCadence.OFF, BackupCadence.fromStored("OFF"))
        assertEquals(BackupCadence.DAILY, BackupCadence.fromStored("DAILY"))
        assertEquals(BackupCadence.WEEKLY, BackupCadence.fromStored("WEEKLY"))
    }

    @Test
    fun `fromStored degrades unknown values to OFF`() {
        assertEquals(BackupCadence.OFF, BackupCadence.fromStored("MONTHLY"))
        assertEquals(BackupCadence.OFF, BackupCadence.fromStored(""))
        assertEquals(BackupCadence.OFF, BackupCadence.fromStored("daily"))
    }

    @Test
    fun `stored round trips`() {
        BackupCadence.entries.forEach { cadence ->
            assertEquals(cadence, BackupCadence.fromStored(cadence.stored()))
        }
    }

    @Test
    fun `pruneSelection keeps the newest N archives`() {
        val names = listOf(
            "oj-nexus-auto-20261018-100000.zip",
            "oj-nexus-auto-20261018-090000.zip",
            "oj-nexus-auto-20261017-090000.zip",
            "manual-copy.zip",
            "oj-nexus-auto-20261016-090000.zip",
        )

        val toDelete = ScheduledBackupWorker.pruneSelection(names, keep = 2)

        assertEquals(
            listOf(
                "oj-nexus-auto-20261017-090000.zip",
                "oj-nexus-auto-20261016-090000.zip",
            ),
            toDelete,
        )
    }

    @Test
    fun `pruneSelection ignores non-archive names and handles keep above count`() {
        assertEquals(emptyList<String>(), ScheduledBackupWorker.pruneSelection(listOf("a.zip", "b.zip"), keep = 5))
        assertEquals(
            emptyList<String>(),
            ScheduledBackupWorker.pruneSelection(listOf("oj-nexus-auto-1.zip"), keep = 1),
        )
        // keep is clamped to at least 1: the oldest is always dropped when over.
        assertTrue(
            ScheduledBackupWorker.pruneSelection(
                listOf("oj-nexus-auto-2.zip", "oj-nexus-auto-1.zip"),
                keep = 0,
            ).contains("oj-nexus-auto-1.zip"),
        )
    }

    @Test
    fun `autoBackupName is prefixed, suffixed and lexically sortable`() {
        val zone = ZoneId.of("UTC")
        val name = ScheduledBackupWorker.autoBackupName(1_760_000_000_000L, zone)

        assertTrue(name.startsWith(ScheduledBackupWorker.NAME_PREFIX))
        assertTrue(name.endsWith(ScheduledBackupWorker.NAME_SUFFIX))
        // Ten minutes apart: the later timestamp must sort after the earlier one.
        val later = ScheduledBackupWorker.autoBackupName(1_760_000_600_000L, zone)
        assertTrue(name < later)
    }
}
