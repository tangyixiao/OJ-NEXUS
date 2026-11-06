package com.ojnexus.core.portable

import java.nio.charset.StandardCharsets.UTF_8
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 132 — G-A acceptance: the mixed golden package survives
 * `validate -> toArchive -> rebuild` losslessly and byte-identically, and a
 * reimport of the rebuilt bytes is a no-op.
 *
 * Every expectation below characterises the frozen contract in
 * `contracts/portable/v1`, not a convenience wrapper: [REFERENCE_IDENTITY] is the
 * same SHA-256 that `contracts/portable/v1/check.py --identity` reports for
 * `golden/pw-p01.json`. Pure JVM JUnit4 — no Robolectric, no Android, no Room.
 */
class PortableGoldenRoundTripTest {

    private val codec = PortableCodec()

    // --- fixtures -----------------------------------------------------------

    private fun goldenBytes(file: String = GOLDEN_FILE): ByteArray =
        javaClass.getResourceAsStream("/portable/v1/golden/$file")?.use { it.readBytes() }
            ?: failWith("golden fixture is not on the unit-test classpath: /portable/v1/golden/$file")

    private fun validateGolden(): ValidatedPackage = codec.validate(goldenBytes())

    private fun archiveGolden(): PortableArchive = codec.toArchive(validateGolden(), IMPORTED_AT_EPOCH_MS)

    /**
     * Reads `cases.json` through the same strict parser the codec uses, so the
     * corpus case list is data, not a second hard-coded copy.
     */
    private fun goldCases(): List<GoldCase> {
        val root = PortableJson.parse(goldenBytes(CASES_FILE)).asObj("$")
        val cases = root.arrField("cases", "$")
        return cases.items.mapIndexed { index, item ->
            val path = "$.cases[$index]"
            val case = item.asObj(path)
            val expect = case.strField("expect", path)
            GoldCase(
                caseId = case.strField("caseId", path),
                file = case.strField("file", path),
                isValid = when (expect) {
                    "valid" -> true
                    "invalid" -> false
                    else -> failWith("$path.expect: unknown expectation \"$expect\"")
                },
                errorCategory = (case.entries["errorCategory"] as? PortableValue.Str)?.value,
            )
        }
    }

    // --- 1. the headline proof ---------------------------------------------

    @Test
    fun `golden package round trip is byte identical`() {
        val golden = goldenBytes()
        val validated = codec.validate(golden)
        assertEquals("validated identity", REFERENCE_IDENTITY, validated.identity)

        val rebuilt = codec.rebuild(codec.toArchive(validated, IMPORTED_AT_EPOCH_MS))

        assertEquals(
            "rebuilt identity must equal the frozen contract identity",
            REFERENCE_IDENTITY,
            codec.identity(rebuilt),
        )
        assertArrayEquals(
            "rebuilt bytes must equal the canonical form of the golden input",
            codec.canonicalBytes(PortableJson.parse(golden)),
            rebuilt,
        )

        val text = String(rebuilt, UTF_8)
        assertTrue("canonical output must end with a newline", text.endsWith("\n"))
        assertFalse("canonical output must not end with a blank line", text.endsWith("\n\n"))
        assertFalse("canonical output must use compact object separators", text.contains("\": "))
        assertFalse("canonical output must use compact array separators", text.contains("\", "))
    }

    // --- 2. semantic equality, not just the hash ----------------------------

    @Test
    fun `semantic content survives the round trip verbatim`() {
        val original = PortableJson.parse(goldenBytes())
        val rebuilt = codec.rebuild(archiveGolden())
        val roundTripped = PortableJson.parse(rebuilt)

        // Whole-tree structural equality; assertSameJson names the first differing path.
        assertSameJson(original, roundTripped, "$")

        assertRootManifest(original, "original")
        assertRootManifest(roundTripped, "rebuilt")

        assertEquals("collection lengths", COLLECTION_LENGTHS, collectionLengths(original))
        assertEquals(
            "collection lengths after round trip",
            collectionLengths(original),
            collectionLengths(roundTripped),
        )

        for ((collection, field, expected) in FIELD_EXPECTATIONS) {
            assertEquals("$.$collection[*].$field", expected, column(original, collection, field))
            assertEquals(
                "$.$collection[*].$field after round trip",
                expected,
                column(roundTripped, collection, field),
            )
        }

        assertCf1AExtra(original, "original")
        assertCf1AExtra(roundTripped, "rebuilt")

        assertLuoguOrigin(original, "original")
        assertLuoguOrigin(roundTripped, "rebuilt")

        assertEquals("dayKey/dueDayKey values", DAY_KEY_VALUES, dayKeyValues(original))
        assertEquals("dayKey/dueDayKey values after round trip", DAY_KEY_VALUES, dayKeyValues(roundTripped))

        assertEquals(
            "sessions[0].problemIds",
            listOf("cf.1A", "luogu.P1001"),
            stringArray(recordsOf(original, "sessions").single(), "problemIds", "$.sessions[0]"),
        )
        assertEquals(
            "problems[0].tags",
            listOf("geometry", "math"),
            stringArray(recordsOf(original, "problems").first(), "tags", "$.problems[0]"),
        )
    }

    // --- 3. idempotence of the round trip -----------------------------------

    @Test
    fun `reimporting the rebuilt bytes is a no-op`() {
        var bytes = codec.rebuild(archiveGolden())
        assertEquals("start point identity", REFERENCE_IDENTITY, codec.identity(bytes))

        repeat(3) { pass ->
            val reparsed = codec.validate(bytes)
            assertArrayEquals(
                "pass ${pass + 1}: validate must not re-canonicalise the bytes",
                bytes,
                reparsed.canonicalBytes,
            )
            val next = codec.rebuild(codec.toArchive(reparsed, IMPORTED_AT_EPOCH_MS))
            assertArrayEquals("pass ${pass + 1}: rebuild must be byte identical", bytes, next)
            assertEquals("pass ${pass + 1}: identity", REFERENCE_IDENTITY, codec.identity(next))
            bytes = next
        }
    }

    // --- 4. the whole corpus, driven from the manifest ----------------------

    @Test
    fun `every valid corpus case round trips and every invalid case is rejected`() {
        val manifest = goldCases()

        assertEquals(
            "the corpus manifest must still describe exactly the expected cases",
            EXPECTED_CASES.map { it.caseId }.sorted(),
            manifest.map { it.caseId }.sorted(),
        )
        for (expected in EXPECTED_CASES) {
            val actual = manifest.single { it.caseId == expected.caseId }
            assertEquals("${expected.caseId}: file", expected.file, actual.file)
            assertEquals("${expected.caseId}: expectation", expected.isValid, actual.isValid)
            assertEquals(
                "${expected.caseId}: error category",
                expected.errorCategory,
                actual.errorCategory,
            )
        }

        var validCases = 0
        var invalidCases = 0
        for (expected in EXPECTED_CASES) {
            val bytes = goldenBytes(expected.file)
            if (expected.isValid) {
                validCases++
                val validated = codec.validate(bytes)
                val rebuilt = codec.rebuild(codec.toArchive(validated, IMPORTED_AT_EPOCH_MS))
                assertEquals(
                    "${expected.caseId}: rebuilt identity must equal the canonical input identity",
                    codec.identity(codec.canonicalBytes(PortableJson.parse(bytes))),
                    codec.identity(rebuilt),
                )
                assertEquals("${expected.caseId}: reference identity", REFERENCE_IDENTITY, codec.identity(rebuilt))
                assertArrayEquals(
                    "${expected.caseId}: rebuilt bytes must equal the canonical input bytes",
                    codec.canonicalBytes(PortableJson.parse(bytes)),
                    rebuilt,
                )
            } else {
                invalidCases++
                var reachedArchive = false
                try {
                    val validated = codec.validate(bytes)
                    // validate() accepted a case the contract calls invalid: that is the defect.
                    reachedArchive = true
                    codec.toArchive(validated, IMPORTED_AT_EPOCH_MS)
                    failWith("${expected.caseId}: invalid package was accepted instead of rejected")
                } catch (e: PortableContractException) {
                    assertFalse(
                        "${expected.caseId}: rejection must come from validate(), " +
                            "never from toArchive/rebuild",
                        reachedArchive,
                    )
                    assertEquals(
                        "${expected.caseId}: rejection category (detail: ${e.reject.detail} " +
                            "at ${e.reject.location})",
                        expected.errorCategory,
                        e.reject.wire,
                    )
                }
            }
        }
        assertEquals("valid case count", 1, validCases)
        assertEquals("invalid case count", 14, invalidCases)
    }

    // --- 5. archive tier and provenance -------------------------------------

    @Test
    fun `archive keeps bounded tiers and provenance for all fifteen records`() {
        val golden = PortableJson.parse(goldenBytes())
        val archive = archiveGolden()
        val manifest = archive.manifest ?: failWith("archive manifest must not be null")

        assertEquals("manifest identity", REFERENCE_IDENTITY, manifest.identity)
        assertArrayEquals(
            "manifest canonical bytes must be the canonical golden bytes",
            codec.canonicalBytes(golden),
            manifest.canonicalBytes,
        )
        assertEquals("manifest formatVersion", 1, manifest.formatVersion)
        assertEquals("manifest scopeId", "scope.alpha", manifest.scopeId)
        assertEquals("manifest sourcePlatform", "android", manifest.sourcePlatform)
        assertEquals(
            "manifest generatorJson",
            """{"name":"synthetic-fixture-gen","version":"1.0"}""",
            manifest.generatorJson,
        )
        assertEquals("manifest createdAtEpochMs", 1_730_500_000_000L, manifest.createdAtEpochMs)
        assertEquals("manifest sourceTag", "SYNTHETIC-TEST-FIXTURE", manifest.sourceTag)
        assertEquals("manifest importedAtEpochMs", IMPORTED_AT_EPOCH_MS, manifest.importedAtEpochMs)

        assertEquals("archived record count", 15, archive.records.size)

        // toArchive() emits records grouped by collection, in PortableSpec order,
        // and each group carries the 0-based ordinal of its position in the array.
        assertEquals(
            "records are grouped in contract collection order",
            PortableSpec.COLLECTIONS,
            archive.records.map { it.collection }.distinct(),
        )
        for (collection in PortableSpec.COLLECTIONS) {
            val ordinals = archive.records.filter { it.collection == collection }.map { it.ordinal }
            assertEquals(
                "$collection: ordinals must ascend without gaps or duplicates",
                List(ordinals.size) { it },
                ordinals,
            )
            assertEquals(
                "$collection: ordinal count must match the golden array length",
                COLLECTION_LENGTHS.getValue(collection),
                ordinals.size,
            )
        }

        val goldenRecords: Map<Pair<String, String>, PortableValue.Obj> = PortableSpec.COLLECTIONS
            .flatMap { collection -> recordsOf(golden, collection).map { collection to it } }
            .associate { (collection, record) ->
                (collection to record.strField("stableId", "$.$collection")) to record
            }

        for (record in archive.records) {
            val path = "${record.collection}[${record.ordinal}]/${record.stableId}"
            assertEquals("$path: tier", PortableTier.STORED_ONLY, record.tier)
            assertEquals(
                "$path: tier reason",
                PortableTierReason.BOUNDED_ARCHIVE_NO_DOMAIN_UI,
                record.tierReason,
            )
            assertEquals("$path: tier reason wire", "bounded_archive_no_domain_ui", record.tierReason.wire)
            assertEquals("$path: synthetic flag", true, record.synthetic)
            assertEquals("$path: source tag", "SYNTHETIC-TEST-FIXTURE", record.sourceTag)
            assertEquals("$path: package identity", REFERENCE_IDENTITY, record.packageIdentity)
            assertTrue("$path: canonicalJson must not be empty", record.canonicalJson.isNotEmpty())

            val parsed = PortableJson.parse(record.canonicalJson.toByteArray(UTF_8)).asObj("$path.canonicalJson")
            assertEquals("$path: canonicalJson stableId", record.stableId, parsed.strField("stableId", path))
            assertEquals(
                "$path: canonicalJson sourceScopeId",
                record.sourceScopeId,
                parsed.strField("sourceScopeId", path),
            )
            assertEquals("$path: canonicalJson origin", record.origin, parsed.strField("origin", path))

            val source = goldenRecords.getValue(record.collection to record.stableId)
            assertEquals(
                "$path: every field of the golden record must be archived verbatim",
                String(PortableJson.write(source), UTF_8).trimEnd('\n'),
                record.canonicalJson,
            )
        }

        val unassigned = archive.records.single { it.stableId == "luogu.P1001" }
        assertEquals("luogu.P1001: origin must stay unassigned", "unassigned", unassigned.origin)
        assertEquals("luogu.P1001: source scope", "scope.alpha", unassigned.sourceScopeId)

        val bySource = archive.records.groupingBy { it.sourceScopeId }.eachCount()
        assertEquals("source scope distribution", mapOf("scope.alpha" to 9, "scope.beta" to 6), bySource)
    }

    // --- 6. extension and unicode fidelity ----------------------------------

    @Test
    fun `archived canonical json keeps the nested extra extension`() {
        val archive = archiveGolden()
        val record = archive.records.single { it.collection == "problems" && it.stableId == "cf.1A" }
        val parsed = PortableJson
            .parse(record.canonicalJson.toByteArray(UTF_8))
            .asObj("$.problems[cf.1A]")
        val extra = parsed.objField("extra", "$.problems[cf.1A]")

        assertEquals("extra key set", CF1A_EXTRA_KEYS, extra.entries.keys.sorted())
        assertEquals("extra.legacyLabel", "cf-1A", extra.strField("legacyLabel", "$.extra"))
        assertEquals(
            "extra.reviewFlags",
            listOf("seen", "hard"),
            stringArray(extra, "reviewFlags", "$.extra"),
        )
        assertEquals("extra.schemaHints", "1", extra.numLiteral("schemaHints", "$.extra"))
        assertTrue(
            "extra.synthetic",
            (extra.field("synthetic", "$.extra") as PortableValue.Bool).value,
        )
        // The extension is nested one level only; the codec must not flatten or drop it.
        assertTrue("extra must survive as a JSON object", record.canonicalJson.contains("\"extra\":{"))
    }

    @Test
    fun `non ascii text survives the canonical writer byte identically`() {
        // The shipped fixture is deliberately pure ASCII, so exercise the writer
        // directly with the shapes a real package carries.
        for (sample in NON_ASCII_SAMPLES) {
            val document = PortableValue.Obj(linkedMapOf<String, PortableValue>("v" to PortableValue.Str(sample)))
            val bytes = PortableJson.write(document)
            assertEquals(
                "writer must emit raw UTF-8, never \\u escapes, for $sample",
                sample,
                (PortableJson.parse(bytes).asObj("$").field("v", "$") as PortableValue.Str).value,
            )
            assertArrayEquals(
                "parse -> write must be a fixed point for $sample",
                bytes,
                PortableJson.write(PortableJson.parse(bytes)),
            )
        }

        // And end-to-end: a non-ASCII title inside the real package must survive
        // validate -> toArchive -> rebuild without escaping or normalisation.
        val root = PortableJson.parse(goldenBytes()).asObj("$")
        val problems = root.arrField("problems")
        val first = problems.items.first().asObj("$.problems[0]")
        first.entries["title"] = PortableValue.Str(NON_ASCII_TITLE)
        val derived = PortableJson.write(root)

        val rebuilt = codec.rebuild(codec.toArchive(codec.validate(derived), IMPORTED_AT_EPOCH_MS))
        assertArrayEquals("non-ASCII package must round trip byte identically", derived, rebuilt)
        assertEquals(
            "non-ASCII package identity",
            PortableJson.sha256Hex(derived),
            PortableJson.sha256Hex(rebuilt),
        )
        assertTrue(
            "rebuilt bytes must contain the raw UTF-8 title",
            String(rebuilt, UTF_8).contains(NON_ASCII_TITLE),
        )
        assertEquals(
            "title must be preserved verbatim",
            NON_ASCII_TITLE,
            recordsOf(PortableJson.parse(rebuilt), "problems").first().strField("title", "$.problems[0]"),
        )
    }

    // --- recursive structural comparison ------------------------------------

    /**
     * Walks both trees and fails with the exact JSON path of the first
     * difference. Object key order is canonicalised by the writer, so keys are
     * compared as sets here; byte-order equality is proven separately by the
     * round-trip test.
     */
    private fun assertSameJson(expected: PortableValue, actual: PortableValue, path: String) {
        when (expected) {
            is PortableValue.Obj -> {
                val actualObj = actual as? PortableValue.Obj
                    ?: failWith("$path: expected object, got ${typeOf(actual)}")
                assertEquals(
                    "$path: object key set",
                    expected.entries.keys.sorted(),
                    actualObj.entries.keys.sorted(),
                )
                for ((key, child) in expected.entries) {
                    assertSameJson(child, actualObj.entries.getValue(key), "$path.$key")
                }
            }

            is PortableValue.Arr -> {
                val actualArr = actual as? PortableValue.Arr
                    ?: failWith("$path: expected array, got ${typeOf(actual)}")
                assertEquals("$path: array length", expected.items.size, actualArr.items.size)
                for (index in expected.items.indices) {
                    assertSameJson(expected.items[index], actualArr.items[index], "$path[$index]")
                }
            }

            is PortableValue.Str -> {
                val actualStr = actual as? PortableValue.Str
                    ?: failWith("$path: expected string, got ${typeOf(actual)}")
                assertEquals("$path: string value", expected.value, actualStr.value)
            }

            is PortableValue.Num -> {
                val actualNum = actual as? PortableValue.Num
                    ?: failWith("$path: expected number, got ${typeOf(actual)}")
                assertEquals("$path: number literal", expected.literal, actualNum.literal)
            }

            is PortableValue.Bool -> {
                val actualBool = actual as? PortableValue.Bool
                    ?: failWith("$path: expected boolean, got ${typeOf(actual)}")
                assertEquals("$path: boolean value", expected.value, actualBool.value)
            }

            PortableValue.Null -> assertEquals("$path: null", PortableValue.Null, actual)
        }
    }

    // --- expectation helpers ------------------------------------------------

    private fun assertRootManifest(root: PortableValue, label: String) {
        val obj = root.asObj("$")
        assertEquals("$label $.formatVersion", "1", obj.numLiteral("formatVersion"))
        assertEquals("$label $.scopeId", "scope.alpha", obj.strField("scopeId"))
        assertEquals("$label $.sourcePlatform", "android", obj.strField("sourcePlatform"))
        assertEquals("$label $.createdAtEpochMs", "1730500000000", obj.numLiteral("createdAtEpochMs"))
        val generator = obj.objField("generator")
        assertEquals(
            "$label $.generator.name",
            "synthetic-fixture-gen",
            generator.strField("name", "$.generator"),
        )
        assertEquals("$label $.generator.version", "1.0", generator.strField("version", "$.generator"))
        assertEquals("$label $.counts", COLLECTION_COUNTS, obj.countsLiteral())
    }

    private fun assertCf1AExtra(tree: PortableValue, label: String) {
        val record = recordsOf(tree, "problems").single { it.strField("stableId", "$.problems") == "cf.1A" }
        val path = "$.problems[cf.1A]"
        val extra = record.objField("extra", path)
        assertEquals("$label $path.extra.legacyLabel", "cf-1A", extra.strField("legacyLabel", "$path.extra"))
        assertEquals(
            "$label $path.extra.reviewFlags",
            listOf("seen", "hard"),
            stringArray(extra, "reviewFlags", "$path.extra"),
        )
        assertEquals("$label $path.extra.schemaHints", "1", extra.numLiteral("schemaHints", "$path.extra"))
        assertTrue(
            "$label $path.extra.synthetic",
            (extra.field("synthetic", "$path.extra") as PortableValue.Bool).value,
        )
        assertEquals("$label $path.extra key set", CF1A_EXTRA_KEYS, extra.entries.keys.sorted())
    }

    private fun assertLuoguOrigin(tree: PortableValue, label: String) {
        val record = recordsOf(tree, "problems").single { it.strField("stableId", "$.problems") == "luogu.P1001" }
        val path = "$.problems[luogu.P1001]"
        assertEquals("$label $path.origin", "unassigned", record.strField("origin", path))
        assertEquals("$label $path.sourceScopeId", "scope.alpha", record.strField("sourceScopeId", path))
        assertEquals("$label $path.solved", false, (record.field("solved", path) as PortableValue.Bool).value)
    }

    // --- small readers ------------------------------------------------------

    private fun recordsOf(tree: PortableValue, collection: String): List<PortableValue.Obj> {
        val array = tree.asObj("$").arrField(collection)
        return array.items.mapIndexed { index, item -> item.asObj("$.$collection[$index]") }
    }

    private fun column(tree: PortableValue, collection: String, field: String): List<String> =
        recordsOf(tree, collection).mapIndexed { index, record ->
            val value = record.field(field, "$.$collection[$index]")
            when (value) {
                is PortableValue.Str -> value.value
                is PortableValue.Num -> value.literal
                is PortableValue.Bool -> value.value.toString()
                else -> failWith("$.$collection[$index].$field: expected a scalar, got ${typeOf(value)}")
            }
        }

    private fun collectionLengths(tree: PortableValue): Map<String, Int> =
        PortableSpec.COLLECTIONS.associateWith { recordsOf(tree, it).size }

    private fun stringArray(record: PortableValue.Obj, key: String, path: String): List<String> =
        record.arrField(key, path).items.mapIndexed { index, item ->
            (item as? PortableValue.Str)?.value
                ?: failWith("$path.$key[$index]: expected string, got ${typeOf(item)}")
        }

    private fun dayKeyValues(tree: PortableValue): Set<String> {
        val out = LinkedHashSet<String>()
        collectDayKeys(tree, out, "$")
        return out
    }

    private fun collectDayKeys(value: PortableValue, out: MutableSet<String>, path: String) {
        when (value) {
            is PortableValue.Obj -> {
                for ((key, child) in value.entries) {
                    if (key == "dayKey" || key == "dueDayKey") {
                        out.add(
                            (child as? PortableValue.Str)?.value
                                ?: failWith("$path.$key: expected string, got ${typeOf(child)}"),
                        )
                    }
                    collectDayKeys(child, out, "$path.$key")
                }
            }

            is PortableValue.Arr -> {
                for ((index, item) in value.items.withIndex()) collectDayKeys(item, out, "$path[$index]")
            }

            else -> Unit
        }
    }

    // --- accessors with precise failure messages ----------------------------

    private fun PortableValue.asObj(path: String): PortableValue.Obj =
        this as? PortableValue.Obj ?: failWith("$path: expected object, got ${typeOf(this)}")

    private fun PortableValue.Obj.field(key: String, path: String = "$"): PortableValue =
        entries[key] ?: failWith("$path: missing key \"$key\"; present keys are ${entries.keys}")

    private fun PortableValue.Obj.strField(key: String, path: String = "$"): String {
        val value = field(key, path)
        return (value as? PortableValue.Str)?.value
            ?: failWith("$path.$key: expected string, got ${typeOf(value)}")
    }

    private fun PortableValue.Obj.numLiteral(key: String, path: String = "$"): String {
        val value = field(key, path)
        return (value as? PortableValue.Num)?.literal
            ?: failWith("$path.$key: expected number, got ${typeOf(value)}")
    }

    private fun PortableValue.Obj.objField(key: String, path: String = "$"): PortableValue.Obj =
        field(key, path).asObj("$path.$key")

    private fun PortableValue.Obj.arrField(key: String, path: String = "$"): PortableValue.Arr {
        val value = field(key, path)
        return value as? PortableValue.Arr ?: failWith("$path.$key: expected array, got ${typeOf(value)}")
    }

    private fun PortableValue.Obj.countsLiteral(path: String = "$"): Map<String, String> {
        val counts = objField("counts", path)
        return PortableSpec.COLLECTIONS.associateWith { counts.numLiteral(it, "$path.counts") }
    }

    private fun typeOf(value: PortableValue): String = when (value) {
        is PortableValue.Obj -> "object"
        is PortableValue.Arr -> "array"
        is PortableValue.Str -> "string"
        is PortableValue.Num -> "number"
        is PortableValue.Bool -> "boolean"
        PortableValue.Null -> "null"
    }

    private fun failWith(message: String): Nothing = throw AssertionError(message)

    /** One row of the frozen corpus manifest. */
    private data class GoldCase(
        val caseId: String,
        val file: String,
        val isValid: Boolean,
        val errorCategory: String?,
    )

    private companion object {
        const val GOLDEN_FILE = "pw-p01.json"
        const val CASES_FILE = "cases.json"

        /** `check.py --identity contracts/portable/v1/golden/pw-p01.json`. */
        const val REFERENCE_IDENTITY = "81bb13c1746381f2d7f95ae69e421503dcc898aae823222e8a3caf40d55b8d44"
        const val IMPORTED_AT_EPOCH_MS = 1_700_000_000_000L

        /**
         * The frozen corpus, spelled out on purpose: if `cases.json` grows a new
         * case this test fails on the set comparison above instead of silently
         * testing less than the contract claims.
         */
        val EXPECTED_CASES = listOf(
            GoldCase("PW-P01", "pw-p01.json", isValid = true, errorCategory = null),
            GoldCase("PW-N01", "pw-n01.json", isValid = false, errorCategory = "UNKNOWN_FIELD"),
            GoldCase("PW-N02", "pw-n02.json", isValid = false, errorCategory = "UNSUPPORTED_VERSION"),
            GoldCase("PW-N03", "pw-n03.json", isValid = false, errorCategory = "INVALID_INPUT"),
            GoldCase("PW-N04", "pw-n04.json", isValid = false, errorCategory = "UNKNOWN_FIELD"),
            GoldCase("PW-N05", "pw-n05.json", isValid = false, errorCategory = "MISSING_REFERENCE"),
            GoldCase("PW-N06", "pw-n06.json", isValid = false, errorCategory = "DUPLICATE_ID"),
            GoldCase("PW-N09", "pw-n09.json", isValid = false, errorCategory = "SIZE_LIMIT"),
            GoldCase("PW-N10", "pw-n10.json", isValid = false, errorCategory = "PATH_UNSAFE"),
            GoldCase("PW-N11", "pw-n11.json", isValid = false, errorCategory = "UNKNOWN_FIELD"),
            GoldCase("PW-N13", "pw-n13.json", isValid = false, errorCategory = "UNKNOWN_FIELD"),
            GoldCase("PW-N15", "pw-n15.json", isValid = false, errorCategory = "INVALID_INPUT"),
            GoldCase(
                "PW-MISSING-FORMAT-VERSION",
                "pw-missing-format-version.json",
                isValid = false,
                errorCategory = "INVALID_INPUT",
            ),
            GoldCase(
                "PW-COUNTS-MISMATCH",
                "pw-counts-mismatch.json",
                isValid = false,
                errorCategory = "COUNTS_MISMATCH",
            ),
            GoldCase(
                "PW-DUPLICATE-JSON-KEYS",
                "pw-duplicate-json-keys.json",
                isValid = false,
                errorCategory = "INVALID_INPUT",
            ),
        )

        /** Text a real package carries: CJK, accented Latin, an em dash, an emoji. */
        val NON_ASCII_SAMPLES = listOf(
            "最长上升子序列",
            "Théâtre de l'Opéra",
            "边界 — two pointers",
            "提交 2/3 通过 ✅",
        )

        const val NON_ASCII_TITLE = "剧院广场 — Théâtre Square 中文标题 ✅"

        val COLLECTION_LENGTHS = mapOf(
            "problems" to 3,
            "attempts" to 8,
            "notes" to 1,
            "reviews" to 2,
            "sessions" to 1,
        )

        val COLLECTION_COUNTS = mapOf(
            "problems" to "3",
            "attempts" to "8",
            "notes" to "1",
            "reviews" to "2",
            "sessions" to "1",
        )

        val DAY_KEY_VALUES = setOf("2026-11-01", "2026-11-02", "2026-11-03", "2026-11-09", "2026-11-10")

        val CF1A_EXTRA_KEYS = listOf("legacyLabel", "reviewFlags", "schemaHints", "synthetic")

        /** Every record's identity-bearing and enum-ish fields, spelled out. */
        val FIELD_EXPECTATIONS = listOf(
            Triple("problems", "stableId", listOf("cf.1A", "atc.AGC001A", "luogu.P1001")),
            Triple("problems", "sourceScopeId", listOf("scope.alpha", "scope.beta", "scope.alpha")),
            Triple("problems", "origin", listOf("scope", "scope", "unassigned")),
            Triple("problems", "judge", listOf("codeforces", "atcoder", "luogu")),
            Triple("problems", "dayKey", listOf("2026-11-01", "2026-11-02", "2026-11-03")),
            Triple(
                "attempts",
                "stableId",
                listOf("att.01", "att.02", "att.03", "att.04", "att.05", "att.06", "att.07", "att.08"),
            ),
            Triple(
                "attempts",
                "sourceScopeId",
                listOf(
                    "scope.alpha", "scope.alpha", "scope.alpha", "scope.alpha",
                    "scope.beta", "scope.beta", "scope.beta", "scope.beta",
                ),
            ),
            Triple("attempts", "origin", List(8) { "scope" }),
            Triple(
                "attempts",
                "problemId",
                listOf(
                    "cf.1A", "cf.1A", "atc.AGC001A", "atc.AGC001A",
                    "atc.AGC001A", "atc.AGC001A", "atc.AGC001A", "atc.AGC001A",
                ),
            ),
            Triple("attempts", "verdict", listOf("AC", "WA", "TLE", "MLE", "RE", "CE", "PE", "OTHER")),
            Triple("attempts", "dayKey", List(8) { "2026-11-02" }),
            Triple("notes", "stableId", listOf("note.001")),
            Triple("notes", "sourceScopeId", listOf("scope.alpha")),
            Triple("notes", "origin", listOf("scope")),
            Triple("notes", "dayKey", listOf("2026-11-02")),
            Triple("reviews", "stableId", listOf("rev.001", "rev.002")),
            Triple("reviews", "sourceScopeId", listOf("scope.alpha", "scope.beta")),
            Triple("reviews", "origin", listOf("scope", "scope")),
            Triple("reviews", "lastResult", listOf("PASS", "HARD")),
            Triple("reviews", "dueDayKey", listOf("2026-11-09", "2026-11-10")),
            Triple("reviews", "dayKey", listOf("2026-11-02", "2026-11-03")),
            Triple("sessions", "stableId", listOf("sess.001")),
            Triple("sessions", "sourceScopeId", listOf("scope.alpha")),
            Triple("sessions", "origin", listOf("scope")),
            Triple("sessions", "type", listOf("PRACTICE")),
            Triple("sessions", "state", listOf("FINISHED")),
            Triple("sessions", "dayKey", listOf("2026-11-02")),
        )
    }
}
