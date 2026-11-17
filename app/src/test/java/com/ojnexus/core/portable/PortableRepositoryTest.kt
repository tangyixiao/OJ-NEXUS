package com.ojnexus.core.portable

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 132 — PortableRepository orchestration contract.
 *
 * Uses a fake [PortableArchiveStore] (no Room, no Android) so every assertion is
 * about the repository's own behaviour: preview/cancel are read-only, apply is
 * one transactional write, reimport is a no-op, export rebuilds and verifies
 * identity, and no failure leaves the state stuck in APPLYING.
 *
 * The golden package is the frozen v1 corpus; [REFERENCE_IDENTITY] is the value
 * reported by `contracts/portable/v1/check.py --identity` for pw-p01.json.
 */
class PortableRepositoryTest {

    private companion object {
        const val REFERENCE_IDENTITY =
            "81bb13c1746381f2d7f95ae69e421503dcc898aae823222e8a3caf40d55b8d44"
        const val SYNTHETIC_SOURCE_TAG = "SYNTHETIC-TEST-FIXTURE"
    }

    private val codec = PortableCodec()

    // --- fixtures -----------------------------------------------------------

    private fun golden(): ByteArray =
        javaClass.getResourceAsStream("/portable/v1/golden/pw-p01.json")?.use { it.readBytes() }
            ?: error("golden fixture is not on the unit-test classpath")

    private fun fixture(file: String): ByteArray =
        javaClass.getResourceAsStream("/portable/v1/golden/$file")?.use { it.readBytes() }
            ?: error("fixture is not on the unit-test classpath: $file")

    /** A second, still-valid package that differs only in createdAtEpochMs. */
    private fun goldenWithCreatedAt(value: Long): ByteArray {
        val root = PortableJson.parse(golden()) as PortableValue.Obj
        val copy = LinkedHashMap(root.entries)
        copy["createdAtEpochMs"] = PortableValue.Num(value.toString())
        return PortableJson.write(PortableValue.Obj(copy))
    }

    private fun goldenArchive(importedAt: Long = 1_700_000_000_000L): PortableArchive =
        codec.toArchive(codec.validate(golden()), importedAt)

    /**
     * In-memory store that counts every mutating call. [beforeWrite] runs inside
     * [currentIdentity] so a test can call [PortableRepository.cancel] at the
     * exact pre-write checkpoint without relying on timing.
     */
    private class FakeStore : PortableArchiveStore {
        var archive: PortableArchive = PortableArchive(null, emptyList())
        var loadCalls = 0
        var replaceCalls = 0
        var clearCalls = 0
        var beforeWrite: (() -> Unit)? = null

        override suspend fun load(): PortableArchive {
            loadCalls++
            return archive
        }

        override suspend fun recordCount(): Int = archive.records.size

        override suspend fun currentIdentity(): String? {
            beforeWrite?.invoke()
            return archive.manifest?.identity
        }

        override suspend fun replaceAll(archive: PortableArchive): ApplyStats {
            replaceCalls++
            val rowsDeleted = (if (this.archive.manifest != null) 1 else 0) + this.archive.records.size
            this.archive = archive
            return ApplyStats(
                rowsUpserted = 1 + archive.records.size,
                rowsDeleted = rowsDeleted,
            )
        }

        override suspend fun clear() {
            clearCalls++
            archive = PortableArchive(null, emptyList())
        }

        fun mutations(): Int = replaceCalls + clearCalls
    }

    private fun repository(store: FakeStore): PortableRepository =
        PortableRepository(store, codec) { 1_700_000_000_000L }

    /** Suspend-friendly assertThrows: no nested runBlocking, no SAM ambiguity. */
    private suspend fun <T : Throwable> assertThrowsSuspend(
        klass: Class<T>,
        block: suspend () -> Unit,
    ): T {
        var thrown: Throwable? = null
        try {
            block()
        } catch (e: Throwable) {
            thrown = e
        }
        assertTrue(
            "expected ${klass.name} but was ${thrown?.javaClass?.name ?: "no exception"}",
            thrown != null && klass.isInstance(thrown),
        )
        @Suppress("UNCHECKED_CAST")
        return thrown as T
    }

    // --- 1. preview is a pure read -------------------------------------------

    @Test
    fun `preview validates and negotiates without any store write`() = runBlocking {
        val store = FakeStore()
        val repo = repository(store)

        val preview = repo.preview(golden())

        assertEquals("identity", REFERENCE_IDENTITY, preview.identity)
        assertTrue("golden package is fully acceptable", preview.acceptAll)
        assertEquals("editable count is reported explicitly as zero", 0, preview.editableCount)
        assertEquals("15 stored-only records", 15, preview.storedOnlyCount)
        assertEquals("no rejected records", 0, preview.rejectedCount)
        assertTrue("no skipped records", preview.skipped.isEmpty())
        assertEquals("two sources", 2, preview.sources.size)
        assertEquals("scope.alpha", "scope.alpha", preview.sources[0].sourceScopeId)
        assertEquals(9, preview.sources[0].recordCount)
        assertEquals("scope.beta", "scope.beta", preview.sources[1].sourceScopeId)
        assertEquals(6, preview.sources[1].recordCount)

        assertEquals("phase is PREVIEW", PortableWorkspaceState.Phase.PREVIEW, repo.state.value.phase)
        assertEquals("preview is published", preview, repo.state.value.preview)
        assertEquals("load is a pure read", 1, store.loadCalls)
        assertEquals("preview performs zero writes", 0, store.mutations())
        assertEquals("archive is still empty", 0, store.recordCount())
    }

    // --- 2. preview conflict enrichment -------------------------------------

    @Test
    fun `preview reports conflicts against a different existing archive`() = runBlocking {
        val store = FakeStore()
        // Same rows, but a different package identity: every key conflicts.
        store.archive = goldenArchive().let { a ->
            a.copy(manifest = a.manifest!!.copy(identity = "ffffffffffffffff"))
        }
        val repo = repository(store)

        val preview = repo.preview(golden())

        assertEquals("every incoming row conflicts", 15, preview.conflicts.size)
        assertTrue("all conflicts are overwrite", preview.conflicts.all { it.kind == "overwrite" })
        assertTrue(
            "conflict detail names the displaced package",
            preview.conflicts.all { it.detail.contains("ffffffffffff") },
        )
        assertEquals("conflict reporting is read-only", 0, store.mutations())
    }

    @Test
    fun `preview reports no conflicts for an identical existing identity`() = runBlocking {
        val store = FakeStore()
        store.archive = goldenArchive()
        val repo = repository(store)

        val preview = repo.preview(golden())

        assertTrue("identical identity means apply is a no-op", preview.conflicts.isEmpty())
        assertEquals("still zero writes", 0, store.mutations())
    }

    // --- 3. preview rejection ------------------------------------------------

    @Test
    fun `preview rejects out-of-contract fields with a terminal error and no writes`() =
        runBlocking {
            val store = FakeStore()
            val repo = repository(store)

            val error = assertThrowsSuspend(PortableContractException::class.java) {
                repo.preview(fixture("pw-n01.json"))
            }

            assertEquals("wire category", "UNKNOWN_FIELD", error.reject.wire)
            assertEquals("phase is ERROR", PortableWorkspaceState.Phase.ERROR, repo.state.value.phase)
            assertEquals("reject is published", error.reject, repo.state.value.reject)
            assertNull("no preview published", repo.state.value.preview)
            assertEquals("a rejected preview wrote nothing", 0, store.mutations())
        }

    // --- 4. apply ------------------------------------------------------------

    @Test
    fun `apply replaces the archive once with measured counts`() = runBlocking {
        val store = FakeStore()
        val repo = repository(store)

        val result = repo.apply(golden())

        assertFalse("not an identical reimport", result.identical)
        assertEquals("identity", REFERENCE_IDENTITY, result.identity)
        assertEquals(0, result.editableCount)
        assertEquals(15, result.storedOnlyCount)
        assertEquals(0, result.rejectedCount)
        assertEquals("one manifest + 15 records upserted", 16, result.rowsUpserted)
        assertEquals("nothing deleted from an empty archive", 0, result.rowsDeleted)

        assertEquals("one transactional write", 1, store.replaceCalls)
        assertEquals("no clear", 0, store.clearCalls)
        assertEquals("15 records stored", 15, store.recordCount())
        assertEquals(REFERENCE_IDENTITY, store.currentIdentity())

        val state = repo.state.value
        assertEquals("phase is APPLIED", PortableWorkspaceState.Phase.APPLIED, state.phase)
        assertEquals("result is published", result, state.applyResult)
        assertEquals(
            "synthetic source tag is data, surfaced for TEST SOURCE",
            SYNTHETIC_SOURCE_TAG,
            state.message,
        )
    }

    @Test
    fun `apply is idempotent on an identical reimport`() = runBlocking {
        val store = FakeStore()
        val repo = repository(store)

        val first = repo.apply(golden())
        val second = repo.apply(golden())

        assertFalse(first.identical)
        assertTrue("second import is a no-op", second.identical)
        assertEquals(0, second.rowsUpserted)
        assertEquals(0, second.rowsDeleted)
        assertEquals("only one replaceAll in total", 1, store.replaceCalls)
        assertEquals("archive still holds 15 records", 15, store.recordCount())
        assertEquals("phase is still APPLIED", PortableWorkspaceState.Phase.APPLIED, repo.state.value.phase)
    }

    @Test
    fun `apply of a different package swaps the whole archive in one transaction`() =
        runBlocking {
            val store = FakeStore()
            val repo = repository(store)
            val other = goldenWithCreatedAt(1_730_500_000_001L)
            val otherIdentity = codec.identity(other)

            repo.apply(golden())
            val second = repo.apply(other)

            assertFalse(second.identical)
            assertEquals(otherIdentity, second.identity)
            assertEquals("old manifest + 15 old rows deleted", 16, second.rowsDeleted)
            assertEquals("new manifest + 15 new rows upserted", 16, second.rowsUpserted)
            assertEquals("exactly two transactional writes", 2, store.replaceCalls)
            assertEquals("stored identity is the new package", otherIdentity, store.currentIdentity())
        }

    @Test
    fun `apply rejects an invalid package before any write`() = runBlocking {
        val store = FakeStore()
        val repo = repository(store)

        val error = assertThrowsSuspend(PortableContractException::class.java) {
            repo.apply(fixture("pw-n01.json"))
        }

        assertEquals("UNKNOWN_FIELD", error.reject.wire)
        assertEquals("invalid apply never reaches the store", 0, store.mutations())
        assertEquals("phase is ERROR", PortableWorkspaceState.Phase.ERROR, repo.state.value.phase)
    }

    @Test
    fun `a store failure cannot leave the phase stuck in APPLYING`() = runBlocking {
        val store = object : PortableArchiveStore by FakeStore() {
            override suspend fun replaceAll(archive: PortableArchive): ApplyStats =
                throw IllegalStateException("disk full")
        }
        val repo = PortableRepository(store, codec) { 1_700_000_000_000L }

        assertThrowsSuspend(IllegalStateException::class.java) {
            repo.apply(golden())
        }

        assertEquals("phase is a terminal ERROR", PortableWorkspaceState.Phase.ERROR, repo.state.value.phase)
        assertEquals("error message is published", "disk full", repo.state.value.message)
    }

    // --- 5. export -----------------------------------------------------------

    @Test
    fun `export rebuilds byte-identical canonical bytes and verifies identity`() =
        runBlocking {
            val store = FakeStore()
            val repo = repository(store)
            repo.apply(golden())

            val bytes = repo.export()
            val expectedCanonical = codec.canonicalBytes(PortableJson.parse(golden()))

            assertArrayEquals("rebuilt bytes are the canonical golden bytes", expectedCanonical, bytes)
            assertEquals(REFERENCE_IDENTITY, codec.identity(bytes))

            val state = repo.state.value
            assertEquals("phase is EXPORTED", PortableWorkspaceState.Phase.EXPORTED, state.phase)
            assertNotNull("export bytes published", state.exportBytes)
            assertEquals("export identity published", REFERENCE_IDENTITY, state.exportIdentity)
            assertEquals(SYNTHETIC_SOURCE_TAG, state.message)
            assertEquals("export is a read-only operation", 1, store.replaceCalls)
        }

    @Test
    fun `export on an empty archive is a terminal error and fabricates nothing`() =
        runBlocking {
            val store = FakeStore()
            val repo = repository(store)

            assertThrowsSuspend(IllegalStateException::class.java) {
                repo.export()
            }

            val state = repo.state.value
            assertEquals(PortableWorkspaceState.Phase.ERROR, state.phase)
            assertNull("no bytes fabricated", state.exportBytes)
            assertNull("no identity fabricated", state.exportIdentity)
            assertEquals(0, store.mutations())
        }

    @Test
    fun `export refuses corrupt manifest identity instead of exporting bad bytes`() =
        runBlocking {
            val store = FakeStore()
            val base = goldenArchive()
            store.archive = base.copy(
                manifest = base.manifest!!.copy(identity = "deadbeefdeadbeef"),
            )
            val repo = repository(store)

            val error = assertThrowsSuspend(IllegalStateException::class.java) {
                repo.export()
            }

            assertTrue(
                "failure names the identity mismatch",
                error.message!!.contains("identity mismatch"),
            )
            val state = repo.state.value
            assertEquals(PortableWorkspaceState.Phase.ERROR, state.phase)
            assertNull("corrupt bytes are never published", state.exportBytes)
        }

    // --- 6. cancel -----------------------------------------------------------

    @Test
    fun `cancel clears preview state with zero writes`() = runBlocking {
        val store = FakeStore()
        val repo = repository(store)
        repo.preview(golden())

        repo.cancel()

        assertEquals("phase returns to IDLE", PortableWorkspaceState.Phase.IDLE, repo.state.value.phase)
        assertNull("no preview retained", repo.state.value.preview)
        assertNull("no result retained", repo.state.value.applyResult)
        assertNull("no reject retained", repo.state.value.reject)
        assertNull("no export bytes retained", repo.state.value.exportBytes)
        assertEquals("cancel performs zero writes", 0, store.mutations())
    }

    @Test
    fun `cancel at the pre-write checkpoint aborts apply with no store mutation`() =
        runBlocking {
            val store = FakeStore()
            val repo = PortableRepository(store, codec) { 1_700_000_000_000L }
            // cancel() fires exactly when apply() reads currentIdentity(), before
            // the second ensureNotCancelled checkpoint and before replaceAll.
            store.beforeWrite = { repo.cancel() }

            // Cancellation may surface as CancellationException or be absorbed as
            // normal coroutine cancellation; both are acceptable. What matters is
            // that no write happened and the phase is not stuck in APPLYING.
            try {
                repo.apply(golden())
            } catch (e: CancellationException) {
                // expected
            } catch (e: Exception) {
                // tolerate a wrapping on other coroutine versions
            }

            assertEquals("no replaceAll reached", 0, store.replaceCalls)
            assertEquals("no clear reached", 0, store.clearCalls)
            assertEquals("archive is still empty", 0, store.recordCount())
            assertEquals(
                "phase is IDLE, not APPLYING",
                PortableWorkspaceState.Phase.IDLE,
                repo.state.value.phase,
            )
        }
}
