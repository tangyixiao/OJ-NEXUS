package com.ojnexus.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RFC 0001 §3 merge rules as executable spec. These tests are the reason the POC can claim
 * determinism: the merge outcome is a pure function of the op set, identical on every device.
 */
class SyncMergeKernelTest {

    private fun op(
        opId: String,
        device: String,
        lamport: Long,
        table: String = "problems",
        row: String = "1234A",
        kind: SyncOpKind = SyncOpKind.UPSERT,
    ) = SyncOp(opId, device, lamport, table, row, kind)

    @Test
    fun higherLamportWinsRegardlessOfArrivalOrder() {
        val early = op("a", "device-1", lamport = 5)
        val late = op("b", "device-2", lamport = 9)
        val result = SyncMergeKernel.mergeByRow(listOf(early, late))
        assertEquals(late, result["problems#1234A"]!!.winner)
        assertEquals(listOf(early), result["problems#1234A"]!!.conflicts)
    }

    @Test
    fun equalLamportBreaksTieByDeviceIdTotalOrder() {
        val fromA = op("a", "device-a", lamport = 7)
        val fromB = op("b", "device-b", lamport = 7)
        val result = SyncMergeKernel.mergeByRow(listOf(fromB, fromA))
        // device-b sorts after device-a, so it wins deterministically on every device.
        assertEquals(fromB, result["problems#1234A"]!!.winner)
    }

    @Test
    fun replayedStaleDeleteDoesNotOverwriteEqualOrderInsert() {
        val insert = op("i", "device-1", lamport = 10, kind = SyncOpKind.UPSERT)
        val staleDelete = op("d", "device-1", lamport = 10, kind = SyncOpKind.DELETE)
        val result = SyncMergeKernel.mergeByRow(listOf(staleDelete, insert))
        // Equal order: insert survives (delete must be strictly greater to win).
        assertEquals(insert, result["problems#1234A"]!!.winner)
    }

    @Test
    fun strictlyGreaterDeleteWins() {
        val insert = op("i", "device-1", lamport = 4, kind = SyncOpKind.UPSERT)
        val delete = op("d", "device-2", lamport = 11, kind = SyncOpKind.DELETE)
        val result = SyncMergeKernel.mergeByRow(listOf(insert, delete))
        assertEquals(delete, result["problems#1234A"]!!.winner)
    }

    @Test
    fun differentRowsNeverInterfere() {
        val a = op("a", "device-1", lamport = 1, row = "1234A")
        val b = op("b", "device-2", lamport = 2, row = "1234B")
        val result = SyncMergeKernel.mergeByRow(listOf(a, b))
        assertEquals(a, result["problems#1234A"]!!.winner)
        assertEquals(b, result["problems#1234B"]!!.winner)
        assertTrue(result["problems#1234A"]!!.conflicts.isEmpty())
    }

    @Test
    fun clockAdvancesToMaxLamportOnly() {
        val ops = listOf(
            op("a", "device-1", lamport = 3),
            op("b", "device-2", lamport = 17),
            op("c", "device-1", lamport = 9),
        )
        assertEquals(17L, SyncMergeKernel.advanceClock(4L, ops))
        assertEquals(0L, SyncMergeKernel.advanceClock(0L, emptyList()))
    }
}
