package com.ojnexus.core.portable.archive

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ojnexus.core.database.OjNexusDatabase
import com.ojnexus.core.portable.ArchivedManifest
import com.ojnexus.core.portable.ArchivedRecord
import com.ojnexus.core.portable.PortableArchive
import com.ojnexus.core.portable.PortableArchiveStore
import com.ojnexus.core.portable.PortableTier
import com.ojnexus.core.portable.PortableTierReason
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 132 — persistence contract of the bounded domain archive.
 *
 * Count convention asserted here (same words as `RoomPortableArchiveStore`):
 * `rowsDeleted` counts every package row plus every record row alive before the replace, so
 * replacing a 3-record archive that has a manifest reports 4. `rowsUpserted` counts the rows
 * actually inserted: one per record plus one for the manifest when present.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PortableArchiveStoreTest {

    private lateinit var database: OjNexusDatabase
    private lateinit var dao: PortableArchiveDao
    private lateinit var store: PortableArchiveStore

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            OjNexusDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.portableArchiveDao()
        store = RoomPortableArchiveStore(dao)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `empty store loads nothing and reports zero`() = runBlocking {
        val archive = store.load()

        assertNull(archive.manifest)
        assertTrue(archive.records.isEmpty())
        assertEquals(0, store.recordCount())
        assertNull(store.currentIdentity())
    }

    @Test
    fun `replaceAll round-trips every field of a three record archive`() = runBlocking {
        val archive = archiveOf(
            manifest = manifest(identity = "pkg-aaaa", bytes = byteArrayOf(1, 2, 3, 0x7F, -1)),
            records = listOf(
                record(
                    collection = "notes",
                    stableId = "note-1",
                    canonicalJson = """{"id":"note-1","title":"二分图匹配 — naïve 笔记"}""",
                    tier = PortableTier.STORED_ONLY,
                    tierReason = PortableTierReason.BOUNDED_ARCHIVE_NO_DOMAIN_UI,
                    synthetic = false,
                    sourceTag = "imported",
                    packageIdentity = "pkg-aaaa",
                    ordinal = 0,
                ),
                record(
                    collection = "notes",
                    stableId = "note-2",
                    canonicalJson = """{"id":"note-2","title":"最小生成树"}""",
                    tier = PortableTier.EDITABLE,
                    tierReason = PortableTierReason.LIVE_DOMAIN_UI,
                    synthetic = true,
                    sourceTag = "synthetic",
                    packageIdentity = "pkg-aaaa",
                    ordinal = 1,
                ),
                record(
                    collection = "reviews",
                    stableId = "review-1",
                    canonicalJson = """{"id":"review-1","verdict":"AC"}""",
                    tier = PortableTier.REJECTED,
                    tierReason = PortableTierReason.UNKNOWN_FIELD,
                    synthetic = false,
                    sourceTag = "imported",
                    packageIdentity = "pkg-aaaa",
                    ordinal = 2,
                ),
            ),
        )

        val stats = store.replaceAll(archive)
        assertEquals(4, stats.rowsUpserted)
        assertEquals(0, stats.rowsDeleted)

        val loaded = store.load()
        val loadedManifest = requireNotNull(loaded.manifest)
        assertEquals("pkg-aaaa", loadedManifest.identity)
        assertEquals(1, loadedManifest.formatVersion)
        assertEquals("scope-1", loadedManifest.scopeId)
        assertEquals("android", loadedManifest.sourcePlatform)
        assertEquals("""{"generator":"ojnexus","version":1}""", loadedManifest.generatorJson)
        assertEquals(1_700_000_000_000L, loadedManifest.createdAtEpochMs)
        assertEquals("imported", loadedManifest.sourceTag)
        assertEquals(1_700_000_123_456L, loadedManifest.importedAtEpochMs)
        assertArrayEquals(byteArrayOf(1, 2, 3, 0x7F, -1), loadedManifest.canonicalBytes)

        assertEquals(3, loaded.records.size)
        assertEquals(listOf(0, 1, 2), loaded.records.map { it.ordinal })
        assertEquals(listOf("note-1", "note-2"), loaded.records.take(2).map { it.stableId })

        val first = loaded.records[0]
        assertEquals("notes", first.collection)
        assertEquals("""{"id":"note-1","title":"二分图匹配 — naïve 笔记"}""", first.canonicalJson)
        assertEquals(PortableTier.STORED_ONLY, first.tier)
        assertEquals(PortableTierReason.BOUNDED_ARCHIVE_NO_DOMAIN_UI, first.tierReason)
        assertEquals(false, first.synthetic)
        assertEquals("imported", first.sourceTag)
        assertEquals("pkg-aaaa", first.packageIdentity)
        assertEquals("scope-1", first.sourceScopeId)
        assertEquals("ojnexus", first.origin)

        val second = loaded.records[1]
        assertEquals(PortableTier.EDITABLE, second.tier)
        assertEquals(PortableTierReason.LIVE_DOMAIN_UI, second.tierReason)
        assertEquals(true, second.synthetic)
        assertEquals("synthetic", second.sourceTag)

        val third = loaded.records[2]
        assertEquals("reviews", third.collection)
        assertEquals(PortableTier.REJECTED, third.tier)
        assertEquals(PortableTierReason.UNKNOWN_FIELD, third.tierReason)

        assertEquals(3, store.recordCount())
        assertEquals("pkg-aaaa", store.currentIdentity())
    }

    @Test
    fun `replacing a three record archive with two leaves no orphans`() = runBlocking {
        store.replaceAll(
            archiveOf(
                manifest = manifest(identity = "pkg-old", bytes = byteArrayOf(9)),
                records = listOf(
                    record(stableId = "a", packageIdentity = "pkg-old", ordinal = 0),
                    record(stableId = "b", packageIdentity = "pkg-old", ordinal = 1),
                    record(stableId = "c", packageIdentity = "pkg-old", ordinal = 2),
                ),
            ),
        )

        // rowsDeleted counts the 3 old records plus the 1 old manifest row.
        val stats = store.replaceAll(
            archiveOf(
                manifest = manifest(identity = "pkg-new", bytes = byteArrayOf(7, 7)),
                records = listOf(
                    record(stableId = "d", packageIdentity = "pkg-new", ordinal = 0),
                    record(stableId = "e", packageIdentity = "pkg-new", ordinal = 1),
                ),
            ),
        )

        assertEquals(4, stats.rowsDeleted)
        assertEquals(3, stats.rowsUpserted)

        val loaded = store.load()
        assertEquals("pkg-new", requireNotNull(loaded.manifest).identity)
        assertEquals(2, loaded.records.size)
        assertEquals(listOf("d", "e"), loaded.records.map { it.stableId })
        assertTrue(loaded.records.all { it.packageIdentity == "pkg-new" })
        assertEquals(2, store.recordCount())
        assertEquals("pkg-new", store.currentIdentity())
        // The one-row manifest invariant survives a replace.
        assertEquals(1, dao.packageCount())
    }

    @Test
    fun `clear empties packages and records`() = runBlocking {
        store.replaceAll(
            archiveOf(
                manifest = manifest(identity = "pkg-clear", bytes = byteArrayOf(1)),
                records = listOf(record(stableId = "a", packageIdentity = "pkg-clear", ordinal = 0)),
            ),
        )
        assertEquals(1, store.recordCount())

        store.clear()

        assertEquals(0, store.recordCount())
        assertEquals(0, dao.packageCount())
        assertNull(store.currentIdentity())
        assertNull(store.load().manifest)
        assertTrue(store.load().records.isEmpty())
    }

    @Test
    fun `replacing with an empty archive is allowed and leaves the store empty`() = runBlocking {
        store.replaceAll(
            archiveOf(
                manifest = manifest(identity = "pkg-wipe", bytes = byteArrayOf(2)),
                records = listOf(record(stableId = "a", packageIdentity = "pkg-wipe", ordinal = 0)),
            ),
        )

        val stats = store.replaceAll(PortableArchive(manifest = null, records = emptyList()))

        assertEquals(2, stats.rowsDeleted)
        assertEquals(0, stats.rowsUpserted)
        assertEquals(0, store.recordCount())
        assertEquals(0, dao.packageCount())
        assertNull(store.currentIdentity())
        assertNull(store.load().manifest)
        assertTrue(store.load().records.isEmpty())
    }

    @Test
    fun `replaceAll never leaves more than one manifest row`() = runBlocking {
        repeat(3) { index ->
            store.replaceAll(
                archiveOf(
                    manifest = manifest(identity = "pkg-$index", bytes = byteArrayOf(index.toByte())),
                    records = listOf(
                        record(stableId = "s$index", packageIdentity = "pkg-$index", ordinal = 0),
                    ),
                ),
            )
            val packages = dao.packageCount()
            assertTrue("package rows must stay within {0,1}, saw $packages", packages in 0..1)
            assertEquals(1, packages)
            assertEquals("pkg-$index", store.currentIdentity())
        }
    }

    @Test
    fun `unknown stored tier names decode to a safe fallback instead of crashing`() = runBlocking {
        dao.insertManifest(
            PortableArchivePackageEntity(
                identity = "pkg-future",
                formatVersion = 1,
                scopeId = "scope-1",
                sourcePlatform = "android",
                generatorJson = "{}",
                createdAtEpochMs = 1L,
                canonicalBytes = byteArrayOf(5),
                sourceTag = "imported",
                importedAtEpochMs = 2L,
            ),
        )
        dao.insertRecords(
            listOf(
                PortableArchiveRecordEntity(
                    collection = "notes",
                    stableId = "future-1",
                    sourceScopeId = "scope-1",
                    origin = "ojnexus",
                    canonicalJson = """{"id":"future-1"}""",
                    tier = "TIER_FROM_A_NEWER_BUILD",
                    tierReason = "reason_from_a_newer_build",
                    synthetic = false,
                    sourceTag = "imported",
                    packageIdentity = "pkg-future",
                    ordinal = 0,
                ),
            ),
        )

        val loaded = store.load()

        assertEquals(1, loaded.records.size)
        assertEquals(PortableTier.STORED_ONLY, loaded.records[0].tier)
        assertEquals(PortableTierReason.BOUNDED_ARCHIVE_NO_DOMAIN_UI, loaded.records[0].tierReason)
        assertEquals(1, store.recordCount())
    }

    private fun archiveOf(
        manifest: ArchivedManifest?,
        records: List<ArchivedRecord>,
    ): PortableArchive = PortableArchive(manifest = manifest, records = records)

    private fun manifest(
        identity: String,
        bytes: ByteArray,
    ): ArchivedManifest = ArchivedManifest(
        identity = identity,
        formatVersion = 1,
        scopeId = "scope-1",
        sourcePlatform = "android",
        generatorJson = """{"generator":"ojnexus","version":1}""",
        createdAtEpochMs = 1_700_000_000_000L,
        canonicalBytes = bytes,
        sourceTag = "imported",
        importedAtEpochMs = 1_700_000_123_456L,
    )

    private fun record(
        collection: String = "notes",
        stableId: String,
        canonicalJson: String = """{"id":"$stableId"}""",
        tier: PortableTier = PortableTier.STORED_ONLY,
        tierReason: PortableTierReason = PortableTierReason.BOUNDED_ARCHIVE_NO_DOMAIN_UI,
        synthetic: Boolean = false,
        sourceTag: String = "imported",
        packageIdentity: String,
        ordinal: Int,
    ): ArchivedRecord = ArchivedRecord(
        collection = collection,
        stableId = stableId,
        sourceScopeId = "scope-1",
        origin = "ojnexus",
        canonicalJson = canonicalJson,
        tier = tier,
        tierReason = tierReason,
        synthetic = synthetic,
        sourceTag = sourceTag,
        packageIdentity = packageIdentity,
        ordinal = ordinal,
    )
}
