package com.ojnexus.core.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SnapshotManifestCodecTest {

    private fun manifest(
        schemaVersion: Int = 13,
        dataGeneration: String = "gen-1",
        counts: List<ManifestTableCount> = listOf(
            ManifestTableCount("problems", 3),
            ManifestTableCount("attempts", 9),
        ),
        createdAt: Long = 1_700_000_000_000L,
        appVersionName: String? = null,
    ) = SnapshotManifest(
        schemaVersion = schemaVersion,
        dataGeneration = dataGeneration,
        createdAtEpochMs = createdAt,
        tableCounts = counts,
        appVersionName = appVersionName,
    )

    @Test
    fun `round trip preserves every field`() {
        val original = manifest(appVersionName = "0.3.84")
        val decoded = SnapshotManifestCodec.decode(SnapshotManifestCodec.encode(original))

        // encode() sorts table counts by table name, so compare the sorted projection.
        assertEquals(original.copy(tableCounts = original.tableCounts.sortedBy { it.table }), decoded)
        assertEquals("0.3.84", decoded?.appVersionName)
        assertEquals("gen-1", decoded?.dataGeneration)
        assertEquals(1_700_000_000_000L, decoded?.createdAtEpochMs)
        assertEquals(13, decoded?.schemaVersion)
    }

    @Test
    fun `encode sorts table counts deterministically`() {
        val encoded = SnapshotManifestCodec.encode(
            manifest(
                counts = listOf(
                    ManifestTableCount("problems", 3),
                    ManifestTableCount("attempts", 9),
                ),
            ),
        )

        val attemptsIndex = encoded.indexOf("\"attempts\"")
        val problemsIndex = encoded.indexOf("\"problems\"")
        assertEquals(-1, encoded.indexOf("\"attempts\"", attemptsIndex + 1))
        org.junit.Assert.assertTrue(attemptsIndex in 0 until problemsIndex)
    }

    @Test
    fun `decode rejects garbage and empty input`() {
        assertNull(SnapshotManifestCodec.decode(""))
        assertNull(SnapshotManifestCodec.decode("not json"))
        assertNull(SnapshotManifestCodec.decode("{\"schema_version\":13}"))
    }

    @Test
    fun `decode rejects unknown keys`() {
        val payload = SnapshotManifestCodec.encode(manifest()).trimEnd('}') + ",\"extra\":1}"
        assertNull(SnapshotManifestCodec.decode(payload))
    }

    @Test
    fun `decode rejects missing fields`() {
        val payload = SnapshotManifestCodec.encode(manifest())
        // Remove the data_generation field to simulate a tampered manifest.
        val tampered = payload.replace("\"data_generation\":\"gen-1\",", "")
        assertNull(SnapshotManifestCodec.decode(tampered))
    }

    @Test
    fun `rowsFor reads the count for a table`() {
        val decoded = SnapshotManifestCodec.decode(SnapshotManifestCodec.encode(manifest()))

        assertEquals(3, decoded?.rowsFor("problems"))
        assertEquals(9, decoded?.rowsFor("attempts"))
        assertNull(decoded?.rowsFor("reviews"))
    }
}
