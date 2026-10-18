package com.ojnexus.core.sync

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Phase 120 — transport seam contract. The store is a fake; the gate is the point: a
 * release-shaped transport must refuse to exist before any I/O.
 */
class SyncTransportTest {

    private class FakeStore : SyncRemoteStore {
        val blobs = mutableMapOf<String, ByteArray>()
        override fun put(opId: String, bytes: ByteArray) { blobs[opId] = bytes }
        override fun get(opId: String): ByteArray? = blobs[opId]
        override fun list(): List<String> = blobs.keys.sorted()
    }

    @Test(expected = SyncPocDisabledException::class)
    fun gateOffTransportRefusesToConstruct() {
        SyncTransport(FakeStore(), gateEnabled = false)
    }

    @Test
    fun uploadWritesBytesIntoStore() {
        val store = FakeStore()
        val dir = File(System.getProperty("java.io.tmpdir"), "sync-transport-test-${System.nanoTime()}")
        dir.mkdirs()
        val payload = File(dir, "op-1.bin")
        payload.writeBytes(byteArrayOf(9, 8, 7))

        SyncTransport(store, gateEnabled = true).upload("op-1", payload.absolutePath)

        assertArrayEquals(byteArrayOf(9, 8, 7), store.get("op-1"))
        dir.deleteRecursively()
    }

    @Test
    fun downloadRoundTripsBytes() {
        val store = FakeStore()
        store.put("op-1", byteArrayOf(1, 2, 3))
        val dir = File(System.getProperty("java.io.tmpdir"), "sync-transport-test-${System.nanoTime()}")
        dir.mkdirs()

        val path = SyncTransport(store, gateEnabled = true).download("op-1", dir)

        assertEquals("sync-payload", File(path!!).parentFile.name)
        assertArrayEquals(byteArrayOf(1, 2, 3), File(path).readBytes())
        dir.deleteRecursively()
    }

    @Test
    fun downloadUnknownOpReturnsNull() {
        val dir = File(System.getProperty("java.io.tmpdir"), "sync-transport-test-${System.nanoTime()}")
        dir.mkdirs()
        assertNull(SyncTransport(FakeStore(), gateEnabled = true).download("ghost", dir))
        dir.deleteRecursively()
    }

    @Test
    fun uploadMissingPayloadThrows() {
        val dir = File(System.getProperty("java.io.tmpdir"), "sync-transport-test-${System.nanoTime()}")
        dir.mkdirs()
        val thrown = runCatching {
            SyncTransport(FakeStore(), gateEnabled = true).upload("op-x", File(dir, "absent.bin").absolutePath)
        }
        assertTrue(thrown.isFailure)
        dir.deleteRecursively()
    }
}
