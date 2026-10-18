package com.ojnexus.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 119 — payload codec determinism + the sealer seam contract (no real crypto; the
 * production primitive selection is a later transport decision per RFC §4).
 */
class SyncPayloadCodecTest {

    private fun row(table: String = "problems", row: String = "1234A", kind: String = "UPSERT") =
        SyncLogOpRow(table, row, kind, "{}")

    @Test
    fun encodeSortsRowsForDeterministicBytes() {
        val a = SyncPayloadCodec.encode(listOf(row(row = "2B"), row(row = "1A"), row(table = "reviews", row = "9")))
        val b = SyncPayloadCodec.encode(listOf(row(table = "reviews", row = "9"), row(row = "1A"), row(row = "2B")))
        assertEquals(a, b)
    }

    @Test
    fun roundTripPreservesAllFields() {
        val rows = listOf(row(), row(table = "attempts", row = "77", kind = "DELETE"))
        val decoded = SyncPayloadCodec.decode(SyncPayloadCodec.encode(rows))!!
        // encode() sorts by (table, rowKey): attempts/77 comes before problems/1234A.
        assertEquals(
            listOf(
                SyncLogOpRow("attempts", "77", "DELETE", "{}"),
                SyncLogOpRow("problems", "1234A", "UPSERT", "{}"),
            ),
            decoded,
        )
    }

    @Test
    fun decodeReturnsNullOnGarbage() {
        assertNull(SyncPayloadCodec.decode("not json at all"))
        assertNull(SyncPayloadCodec.decode("[{\"tableName\":123}]"))
        assertNull(SyncPayloadCodec.decode(""))
    }

    @Test
    fun xorSealerRoundTripsAndIsExplicitlyTestOnly() {
        val sealer = XorSealer(byteArrayOf(1, 2, 3, 4))
        val plaintext = "OJ NEXUS".toByteArray()
        val sealed = sealer.seal(plaintext, "device-a|1".toByteArray())
        assertNotNull(sealer.open(sealed, "device-a|1".toByteArray()))
        assertEquals(
            String(plaintext),
            String(sealer.open(sealed, "device-a|1".toByteArray())!!),
        )
        assertTrue(
            "the double must never be mistaken for real crypto",
            XorSealer::class.java.simpleName.contains("Xor"),
        )
    }
}
