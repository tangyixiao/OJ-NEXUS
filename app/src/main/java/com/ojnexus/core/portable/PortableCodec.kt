package com.ojnexus.core.portable

import java.nio.charset.StandardCharsets

/**
 * Controller-owned, frozen contract codec for Portable Workspace v1 (phase 132).
 *
 * This is a faithful Kotlin port of contracts/portable/v1/check.py. It is pure
 * Kotlin (no Android, Room or Compose) so it runs unchanged in JVM unit tests and
 * in the app. The validation pipeline and the exact reject categories match the
 * Python reference checker step for step:
 *
 *   1. SIZE_LIMIT (raw size)      2. INVALID_INPUT (strict decode)
 *   3. UNSUPPORTED_VERSION        4. UNKNOWN_FIELD (closed schema + credentials)
 *   5. SIZE_LIMIT (overflow)      6. PATH_UNSAFE
 *   7. DUPLICATE_ID               8. MISSING_REFERENCE
 *   9. COUNTS_MISMATCH
 *
 * Exactly one [PortableReject] is raised on failure. The canonical writer in
 * [PortableJson] reproduces check.py's canonical_bytes() byte-for-byte, so the
 * SHA-256 identity of a validated package equals the reference identity.
 */

/** Result of a successful, read-only validation of one package. */
class ValidatedPackage internal constructor(
    val root: PortableValue.Obj,
    val canonicalBytes: ByteArray,
    val identity: String,
    val scopeId: String,
    val sourcePlatform: String,
    val generator: String,
    val generatorJson: String,
    val createdAtEpochMs: Long,
    val counts: Map<String, Int>,
    val sourceTag: String,
    val synthetic: Boolean,
    /** Per-collection records in their original array order. */
    val records: Map<String, List<ValidatedRecord>>,
)

class ValidatedRecord internal constructor(
    val collection: String,
    val ordinal: Int,
    val canonicalJson: String,
    val stableId: String,
    val sourceScopeId: String,
    val origin: String,
    val synthetic: Boolean,
)

// --- Internal draft-07 subset schema tree (mirrors contracts/portable/v1/schema.json) ---

private sealed class Rule {
    data class TObj(
        val required: List<String>,
        val props: LinkedHashMap<String, Rule>,
        val closed: Boolean,
    ) : Rule()

    data class TArr(val items: Rule?, val maxItems: Int?) : Rule()

    data class TStr(
        val pattern: Regex?,
        val minLen: Int?,
        val maxLen: Int?,
        val enum: Set<String>?,
    ) : Rule()

    data class TInt(val min: Long?, val enum: Set<Long>?) : Rule()
    data object TBool : Rule()
}

private const val MAX_LEN = 16384 // schema maxLength counts characters, not bytes

/** Translate a terminal, unescaped `$` into Kotlin's absolute-end anchor `\z`. */
private fun effectivePattern(pattern: String): String =
    if (pattern.endsWith("$") && !pattern.endsWith("\\$")) {
        pattern.dropLast(1) + "\\z"
    } else {
        pattern
    }

private fun str(
    pattern: String? = null,
    minLen: Int? = null,
    maxLen: Int? = null,
    enum: Set<String>? = null,
): Rule.TStr = Rule.TStr(pattern?.let { Regex(effectivePattern(it)) }, minLen, maxLen, enum)

private fun int(min: Long? = null, enum: Set<Long>? = null): Rule.TInt = Rule.TInt(min, enum)

private fun obj(required: List<String>, vararg props: Pair<String, Rule>): Rule.TObj =
    Rule.TObj(required, linkedMapOf(*props), true)

private val SCHEMA: Rule.TObj by lazy { buildSchema() }

private fun buildSchema(): Rule.TObj {
    val stableId = str(pattern = PortableSpec.STABLE_ID_PATTERN, maxLen = MAX_LEN)
    val dayKey = str(pattern = PortableSpec.DAY_KEY_PATTERN, maxLen = MAX_LEN)
    val origin = str(enum = PortableSpec.ORIGINS)
    // `extra` is declared as `{"type":"object"}` only: value shapes and depth
    // are enforced by the checker beyond the schema subset, not here.
    val extra = Rule.TObj(emptyList(), linkedMapOf<String, Rule>(), false)
    val plainStr = str(maxLen = MAX_LEN)
    val epoch = int(min = 0L)

    fun entityBase() = arrayOf(
        "stableId" to stableId,
        "sourceScopeId" to stableId,
        "origin" to origin,
        "extra" to extra,
    )

    val problemsEntity = obj(
        listOf("stableId", "sourceScopeId", "origin", "judge", "externalId", "title", "solved", "dayKey"),
        *entityBase(),
        "judge" to str(enum = PortableSpec.JUDGES),
        "externalId" to str(pattern = PortableSpec.EXTERNAL_ID_PATTERN, maxLen = MAX_LEN),
        "title" to plainStr,
        "sourceUrl" to str(pattern = "^https?://", maxLen = MAX_LEN),
        "solved" to Rule.TBool,
        "firstSolvedAtEpochMs" to epoch,
        "difficulty" to int(), // absent stays absent; never coerced to 0
        "tags" to Rule.TArr(plainStr, null),
        "dayKey" to dayKey,
    )

    val attemptsEntity = obj(
        listOf("stableId", "sourceScopeId", "origin", "problemId", "timestampEpochMs", "verdict", "dayKey"),
        *entityBase(),
        "problemId" to stableId,
        "timestampEpochMs" to epoch,
        "dayKey" to dayKey,
        "verdict" to str(enum = PortableSpec.VERDICTS),
        "rawVerdict" to plainStr,
        "language" to plainStr,
        "durationMin" to epoch,
        "externalSubmissionId" to plainStr,
        "note" to plainStr,
    )

    val notesEntity = obj(
        listOf(
            "stableId", "sourceScopeId", "origin", "problemId", "keyInsight",
            "implementationNotes", "complexity", "general", "updatedEpochMs", "dayKey",
        ),
        *entityBase(),
        "problemId" to stableId,
        "keyInsight" to plainStr,
        "implementationNotes" to plainStr,
        "complexity" to plainStr,
        "general" to plainStr,
        "updatedEpochMs" to epoch,
        "dayKey" to dayKey,
    )

    val reviewsEntity = obj(
        listOf(
            "stableId", "sourceScopeId", "origin", "problemId", "stage", "dueEpochMs",
            "dueDayKey", "createdEpochMs", "dayKey",
        ),
        *entityBase(),
        "problemId" to stableId,
        "stage" to int(min = 1L),
        "dueEpochMs" to epoch,
        "dueDayKey" to dayKey,
        "lastResult" to str(enum = PortableSpec.LAST_RESULTS),
        "lastReviewedEpochMs" to epoch,
        "createdEpochMs" to epoch,
        "dayKey" to dayKey,
    )

    val sessionsEntity = obj(
        listOf("stableId", "sourceScopeId", "origin", "type", "state", "startedEpochMs", "totalPausedMs", "dayKey"),
        *entityBase(),
        "type" to str(enum = PortableSpec.SESSION_TYPES),
        "state" to str(enum = PortableSpec.SESSION_STATES),
        "startedEpochMs" to epoch,
        "finishedEpochMs" to epoch,
        "totalPausedMs" to epoch,
        "targetDurationMin" to epoch,
        "problemIds" to Rule.TArr(str(pattern = PortableSpec.STABLE_ID_PATTERN, maxLen = MAX_LEN), null),
        "dayKey" to dayKey,
    )

    val generator = obj(
        listOf("name", "version"),
        "name" to str(minLen = 1, maxLen = MAX_LEN),
        "version" to str(minLen = 1, maxLen = MAX_LEN),
    )
    val counts = obj(
        listOf("problems", "attempts", "notes", "reviews", "sessions"),
        "problems" to epoch,
        "attempts" to epoch,
        "notes" to epoch,
        "reviews" to epoch,
        "sessions" to epoch,
    )

    fun collection(entity: Rule.TObj) = Rule.TArr(entity, PortableSpec.MAX_RECORDS)

    return obj(
        listOf(
            "formatVersion", "scopeId", "sourcePlatform", "generator", "createdAtEpochMs",
            "counts", "problems", "attempts", "notes", "reviews", "sessions",
        ),
        "formatVersion" to int(enum = setOf(1L)),
        "scopeId" to str(pattern = PortableSpec.STABLE_ID_PATTERN, maxLen = MAX_LEN),
        "sourcePlatform" to str(enum = PortableSpec.PLATFORMS),
        "generator" to generator,
        "createdAtEpochMs" to epoch,
        "counts" to counts,
        "problems" to collection(problemsEntity),
        "attempts" to collection(attemptsEntity),
        "notes" to collection(notesEntity),
        "reviews" to collection(reviewsEntity),
        "sessions" to collection(sessionsEntity),
    )
}

private val PATH_UNSAFE_RE = Regex("""(^[A-Za-z]:\\)|^/|\.\./|^\.\.$""")
private val UTF_8 = StandardCharsets.UTF_8

/**
 * Validates and classifies portable packages. Stateless; safe to call from a
 * preview (read-only) or from an apply (before any store write).
 */
class PortableCodec {

    /** Canonical bytes of a parsed value (delegates to the frozen writer). */
    fun canonicalBytes(value: PortableValue): ByteArray = PortableJson.write(value)

    /** SHA-256 hex identity of canonical bytes. */
    fun identity(bytes: ByteArray): String = PortableJson.sha256Hex(bytes)

    /**
     * Validate one package. Throws [PortableContractException] with exactly one
     * category on the first failure. Performs NO writes of any kind.
     */
    fun validate(bytes: ByteArray): ValidatedPackage {
        // 1. SIZE_LIMIT (raw size, before decode).
        if (bytes.size > PortableSpec.MAX_PACKAGE_BYTES) {
            fail(
                PortableRejectCategory.SIZE_LIMIT,
                "package is ${bytes.size} bytes, limit is ${PortableSpec.MAX_PACKAGE_BYTES}",
                "$",
            )
        }
        // 2. INVALID_INPUT (strict decode).
        val parsed = PortableJson.parse(bytes)
        if (parsed !is PortableValue.Obj) {
            fail(
                PortableRejectCategory.INVALID_INPUT,
                "package root must be a JSON object, got ${typeName(parsed)}",
                "$",
            )
        }
        val root = parsed

        // 3. INVALID_INPUT / UNSUPPORTED_VERSION (version gate, before schema).
        val fv = root.entries["formatVersion"]
            ?: fail(
                PortableRejectCategory.INVALID_INPUT,
                "missing required manifest key \"formatVersion\"",
                "$",
            )
        if (fv !is PortableValue.Num || !isIntegerLiteral(fv.literal)) {
            fail(
                PortableRejectCategory.INVALID_INPUT,
                "formatVersion must be the integer 1, got ${jsonRepr(fv)}",
                "$.formatVersion",
            )
        }
        if (fv.literal.toLongOrNull() != 1L) {
            fail(
                PortableRejectCategory.UNSUPPORTED_VERSION,
                "formatVersion ${fv.literal} is not supported by this contract (expected 1); " +
                    "future majors are rejected whole-package, never best-effort decoded",
                "$.formatVersion",
            )
        }

        // 4. UNKNOWN_FIELD (draft-07 subset) + credential-shaped keys.
        validateSchema(root, SCHEMA, "$")
        checkCredentials(root, "$")

        // 5-9. Contract rules beyond the schema, in documented precedence order.
        checkExtraDepth(root, "$")
        checkCollectionSizes(root)
        checkStringSizes(root, "$")
        checkPathShapes(root, "$")
        checkDuplicates(root)
        checkReferences(root)
        checkCounts(root)

        return buildValidated(root)
    }

    /** Capability tiers for a validated package. Phase 132 has no live domain UI. */
    fun classify(vp: ValidatedPackage): List<TierDecision> {
        val out = ArrayList<TierDecision>()
        for (c in PortableSpec.COLLECTIONS) {
            for (r in vp.records.getValue(c)) {
                out.add(
                    TierDecision(
                        c,
                        r.stableId,
                        PortableTier.STORED_ONLY,
                        PortableTierReason.BOUNDED_ARCHIVE_NO_DOMAIN_UI,
                    ),
                )
            }
        }
        return out
    }

    /** Read-only preview: assemble counts, sources, decisions and conflicts. */
    fun preview(bytes: ByteArray): PortablePreview {
        val vp = validate(bytes)
        val decisions = classify(vp)
        val bySource = LinkedHashMap<String, Int>()
        for (c in PortableSpec.COLLECTIONS) {
            for (r in vp.records.getValue(c)) {
                bySource[r.sourceScopeId] = (bySource[r.sourceScopeId] ?: 0) + 1
            }
        }
        val sources = bySource.entries.sortedBy { it.key }.map { SourceBreakdown(it.key, it.value) }
        val editable = decisions.count { it.tier == PortableTier.EDITABLE }
        val storedOnly = decisions.count { it.tier == PortableTier.STORED_ONLY }
        val rejected = decisions.count { it.tier == PortableTier.REJECTED }
        return PortablePreview(
            scopeId = vp.scopeId,
            sourcePlatform = vp.sourcePlatform,
            generator = vp.generator,
            createdAtEpochMs = vp.createdAtEpochMs,
            counts = vp.counts,
            sources = sources,
            decisions = decisions,
            conflicts = emptyList(),
            skipped = decisions.filter { it.tier == PortableTier.REJECTED },
            editableCount = editable,
            storedOnlyCount = storedOnly,
            rejectedCount = rejected,
            acceptAll = rejected == 0,
            canonicalBytes = vp.canonicalBytes,
            identity = vp.identity,
        )
    }

    /** Build the bounded archive (manifest + records) for one validated package. */
    fun toArchive(vp: ValidatedPackage, importedAtEpochMs: Long): PortableArchive {
        val manifest = ArchivedManifest(
            identity = vp.identity,
            formatVersion = PortableSpec.FORMAT_VERSION,
            scopeId = vp.scopeId,
            sourcePlatform = vp.sourcePlatform,
            generatorJson = vp.generatorJson,
            createdAtEpochMs = vp.createdAtEpochMs,
            canonicalBytes = vp.canonicalBytes,
            sourceTag = vp.sourceTag,
            importedAtEpochMs = importedAtEpochMs,
        )
        val records = ArrayList<ArchivedRecord>()
        for (c in PortableSpec.COLLECTIONS) {
            for (r in vp.records.getValue(c)) {
                records.add(
                    ArchivedRecord(
                        collection = c,
                        stableId = r.stableId,
                        sourceScopeId = r.sourceScopeId,
                        origin = r.origin,
                        canonicalJson = r.canonicalJson,
                        tier = PortableTier.STORED_ONLY,
                        tierReason = PortableTierReason.BOUNDED_ARCHIVE_NO_DOMAIN_UI,
                        synthetic = vp.synthetic || r.synthetic,
                        sourceTag = vp.sourceTag,
                        packageIdentity = vp.identity,
                        ordinal = r.ordinal,
                    ),
                )
            }
        }
        return PortableArchive(manifest, records)
    }

    /**
     * Rebuild the whole package from archived rows + manifest metadata. The
     * repository asserts the SHA-256 of these bytes equals the stored identity;
     * this proves no accepted record was lost instead of replaying a cached blob.
     */
    fun rebuild(archive: PortableArchive): ByteArray {
        val m = archive.manifest
            ?: throw IllegalStateException("portable archive is empty; nothing to export")
        val root = LinkedHashMap<String, PortableValue>()
        root["formatVersion"] = PortableValue.Num(m.formatVersion.toString())
        root["scopeId"] = PortableValue.Str(m.scopeId)
        root["sourcePlatform"] = PortableValue.Str(m.sourcePlatform)
        root["generator"] = PortableJson.parse(m.generatorJson.toByteArray(UTF_8))
        root["createdAtEpochMs"] = PortableValue.Num(m.createdAtEpochMs.toString())

        val countsObj = LinkedHashMap<String, PortableValue>()
        val arrays = LinkedHashMap<String, PortableValue.Arr>()
        for (c in PortableSpec.COLLECTIONS) {
            val recs = archive.records.filter { it.collection == c }.sortedBy { it.ordinal }
            countsObj[c] = PortableValue.Num(recs.size.toString())
            val items = ArrayList<PortableValue>(recs.size)
            for (r in recs) {
                items.add(PortableJson.parse(r.canonicalJson.toByteArray(UTF_8)))
            }
            arrays[c] = PortableValue.Arr(items)
        }
        root["counts"] = PortableValue.Obj(countsObj)
        for (c in PortableSpec.COLLECTIONS) {
            root[c] = arrays.getValue(c)
        }
        return PortableJson.write(PortableValue.Obj(root))
    }

    // --- schema subset validation (find-first, deterministic order) ---

    private fun validateSchema(v: PortableValue, rule: Rule, path: String) {
        when (rule) {
            is Rule.TObj -> {
                if (v !is PortableValue.Obj) failType("object", v, path)
                for (k in rule.required) {
                    if (!v.entries.containsKey(k)) {
                        fail(PortableRejectCategory.UNKNOWN_FIELD, "required: missing required key \"$k\"", path)
                    }
                }
                if (rule.closed) {
                    for (k in v.entries.keys) {
                        if (!rule.props.containsKey(k)) {
                            fail(
                                PortableRejectCategory.UNKNOWN_FIELD,
                                "additionalProperties: unknown key \"$k\" (additionalProperties: false; " +
                                    "nothing is silently ignored)",
                                child(path, k),
                            )
                        }
                    }
                }
                for ((k, r) in rule.props) {
                    val childValue = v.entries[k] ?: continue
                    validateSchema(childValue, r, child(path, k))
                }
            }
            is Rule.TArr -> {
                if (v !is PortableValue.Arr) failType("array", v, path)
                rule.items?.let { items ->
                    for ((i, item) in v.items.withIndex()) {
                        validateSchema(item, items, "$path[$i]")
                    }
                }
                rule.maxItems?.let { m ->
                    if (v.items.size > m) {
                        fail(
                            PortableRejectCategory.SIZE_LIMIT,
                            "maxItems: array must have at most $m items, got ${v.items.size}",
                            path,
                        )
                    }
                }
            }
            is Rule.TStr -> {
                if (v !is PortableValue.Str) failType("string", v, path)
                val s = v.value
                rule.pattern?.let { p ->
                    if (!p.containsMatchIn(s)) {
                        fail(
                            PortableRejectCategory.UNKNOWN_FIELD,
                            "pattern: value does not match pattern ${p.pattern}",
                            path,
                        )
                    }
                }
                rule.minLen?.let { m ->
                    if (s.length < m) {
                        fail(
                            PortableRejectCategory.UNKNOWN_FIELD,
                            "minLength: string must have at least $m characters, got ${s.length}",
                            path,
                        )
                    }
                }
                rule.maxLen?.let { m ->
                    if (s.length > m) {
                        fail(
                            PortableRejectCategory.SIZE_LIMIT,
                            "maxLength: string must have at most $m characters, got ${s.length}",
                            path,
                        )
                    }
                }
                rule.enum?.let { e ->
                    if (!e.contains(s)) {
                        fail(
                            PortableRejectCategory.UNKNOWN_FIELD,
                            "enum: value is not one of the admitted values",
                            path,
                        )
                    }
                }
            }
            is Rule.TInt -> {
                if (v !is PortableValue.Num || !isIntegerLiteral(v.literal)) {
                    failType("integer", v, path)
                }
                val n = v.literal.toLongOrNull()
                rule.min?.let { m ->
                    if (n != null && n < m) {
                        fail(PortableRejectCategory.UNKNOWN_FIELD, "minimum: value must be >= $m", path)
                    }
                }
                rule.enum?.let { e ->
                    if (n == null || !e.contains(n)) {
                        fail(
                            PortableRejectCategory.UNKNOWN_FIELD,
                            "enum: value is not one of the admitted values",
                            path,
                        )
                    }
                }
            }
            Rule.TBool -> {
                if (v !is PortableValue.Bool) failType("boolean", v, path)
            }
        }
    }

    private fun failType(expected: String, v: PortableValue, path: String): Nothing =
        fail(
            PortableRejectCategory.UNKNOWN_FIELD,
            "type: expected type $expected, got ${typeName(v)}",
            path,
        )

    // --- contract rules beyond the schema subset ---

    private fun checkCredentials(v: PortableValue, path: String) {
        when (v) {
            is PortableValue.Obj -> {
                for ((k, child) in v.entries) {
                    val kp = child(path, k)
                    if (isCredentialShaped(k)) {
                        fail(
                            PortableRejectCategory.UNKNOWN_FIELD,
                            "credential-shaped key \"$k\" — credentials are never portable",
                            kp,
                        )
                    }
                    checkCredentials(child, kp)
                }
            }
            is PortableValue.Arr -> {
                for ((i, item) in v.items.withIndex()) checkCredentials(item, "$path[$i]")
            }
            else -> Unit
        }
    }

    private fun isCredentialShaped(key: String): Boolean {
        val normalized = key.replace(Regex("[\\s._\\-]+"), "").lowercase()
        return PortableSpec.CREDENTIAL_TOKENS.any { normalized.contains(it) }
    }

    private fun checkExtraDepth(v: PortableValue, path: String) {
        when (v) {
            is PortableValue.Obj -> {
                for ((k, child) in v.entries) {
                    val kp = child(path, k)
                    if (k == "extra" && child is PortableValue.Obj) {
                        for ((ek, ev) in child.entries) {
                            val ep = child(kp, ek)
                            if (ev is PortableValue.Obj) {
                                extraDepthFail(ep)
                            }
                            if (ev is PortableValue.Arr) {
                                for ((i, item) in ev.items.withIndex()) {
                                    if (item is PortableValue.Obj || item is PortableValue.Arr) {
                                        extraDepthFail("$ep[$i]")
                                    }
                                }
                            }
                        }
                    } else {
                        checkExtraDepth(child, kp)
                    }
                }
            }
            is PortableValue.Arr -> {
                for ((i, item) in v.items.withIndex()) checkExtraDepth(item, "$path[$i]")
            }
            else -> Unit
        }
    }

    private fun extraDepthFail(path: String): Nothing = fail(
        PortableRejectCategory.UNKNOWN_FIELD,
        "extra values may be scalars or arrays of scalars; the 2-level depth limit is exceeded",
        path,
    )

    private fun checkCollectionSizes(root: PortableValue.Obj) {
        for (c in PortableSpec.COLLECTIONS) {
            val arr = root.entries[c] as? PortableValue.Arr ?: continue
            if (arr.items.size > PortableSpec.MAX_RECORDS) {
                fail(
                    PortableRejectCategory.SIZE_LIMIT,
                    "collection $c holds ${arr.items.size} records, limit is ${PortableSpec.MAX_RECORDS}",
                    "$.$c",
                )
            }
        }
    }

    private fun checkStringSizes(v: PortableValue, path: String) {
        when (v) {
            is PortableValue.Obj -> {
                for ((k, child) in v.entries) {
                    val kp = child(path, k)
                    val size = k.toByteArray(UTF_8).size
                    if (size > PortableSpec.MAX_STRING_BYTES) {
                        fail(
                            PortableRejectCategory.SIZE_LIMIT,
                            "object key is $size UTF-8 bytes, limit is ${PortableSpec.MAX_STRING_BYTES}",
                            kp,
                        )
                    }
                    checkStringSizes(child, kp)
                }
            }
            is PortableValue.Arr -> {
                for ((i, item) in v.items.withIndex()) checkStringSizes(item, "$path[$i]")
            }
            is PortableValue.Str -> {
                val size = v.value.toByteArray(UTF_8).size
                if (size > PortableSpec.MAX_STRING_BYTES) {
                    fail(
                        PortableRejectCategory.SIZE_LIMIT,
                        "string is $size UTF-8 bytes, limit is ${PortableSpec.MAX_STRING_BYTES}",
                        path,
                    )
                }
            }
            else -> Unit
        }
    }

    private fun checkPathShapes(v: PortableValue, path: String) {
        when (v) {
            is PortableValue.Obj -> {
                for ((k, child) in v.entries) checkPathShapes(child, child(path, k))
            }
            is PortableValue.Arr -> {
                for ((i, item) in v.items.withIndex()) checkPathShapes(item, "$path[$i]")
            }
            is PortableValue.Str -> {
                if (PATH_UNSAFE_RE.containsMatchIn(v.value)) {
                    fail(
                        PortableRejectCategory.PATH_UNSAFE,
                        "string value looks like a filesystem path",
                        path,
                    )
                }
            }
            else -> Unit
        }
    }

    private fun checkDuplicates(root: PortableValue.Obj) {
        for (c in PortableSpec.COLLECTIONS) {
            val arr = root.entries[c] as? PortableValue.Arr ?: continue
            val seenStable = HashMap<String, Int>()
            val seenProblem = HashMap<String, Int>()
            for ((i, rec) in arr.items.withIndex()) {
                if (rec !is PortableValue.Obj) continue
                val stableId = (rec.entries["stableId"] as? PortableValue.Str)?.value
                if (stableId != null && seenStable.put(stableId, i) != null) {
                    fail(
                        PortableRejectCategory.DUPLICATE_ID,
                        "duplicate stableId \"$stableId\" in collection $c",
                        "$.$c[$i].stableId",
                    )
                }
                if (c == "notes") {
                    val problemId = (rec.entries["problemId"] as? PortableValue.Str)?.value
                    if (problemId != null && seenProblem.put(problemId, i) != null) {
                        fail(
                            PortableRejectCategory.DUPLICATE_ID,
                            "duplicate problemId \"$problemId\" in notes[] (one note per problem per package)",
                            "$.$c[$i].problemId",
                        )
                    }
                }
            }
        }
    }

    private fun checkReferences(root: PortableValue.Obj) {
        val problemIds = HashSet<String>()
        (root.entries["problems"] as? PortableValue.Arr)?.items?.forEach { rec ->
            if (rec is PortableValue.Obj) {
                (rec.entries["stableId"] as? PortableValue.Str)?.let { problemIds.add(it.value) }
            }
        }
        for (c in listOf("attempts", "notes", "reviews")) {
            val arr = root.entries[c] as? PortableValue.Arr ?: continue
            for ((i, rec) in arr.items.withIndex()) {
                if (rec !is PortableValue.Obj) continue
                val problemId = (rec.entries["problemId"] as? PortableValue.Str)?.value ?: continue
                if (problemId !in problemIds) {
                    fail(
                        PortableRejectCategory.MISSING_REFERENCE,
                        "$c[$i] references problemId \"$problemId\" which does not exist in problems[].stableId",
                        "$.$c[$i].problemId",
                    )
                }
            }
        }
        val sessions = root.entries["sessions"] as? PortableValue.Arr ?: return
        for ((i, rec) in sessions.items.withIndex()) {
            if (rec !is PortableValue.Obj) continue
            val refs = rec.entries["problemIds"] as? PortableValue.Arr ?: continue
            for ((p, item) in refs.items.withIndex()) {
                val problemId = (item as? PortableValue.Str)?.value ?: continue
                if (problemId !in problemIds) {
                    fail(
                        PortableRejectCategory.MISSING_REFERENCE,
                        "sessions[$i].problemIds[$p] references \"$problemId\" which does not exist in problems[].stableId",
                        "$.sessions[$i].problemIds[$p]",
                    )
                }
            }
        }
    }

    private fun checkCounts(root: PortableValue.Obj) {
        val counts = root.entries["counts"] as? PortableValue.Obj ?: return
        for (c in PortableSpec.COLLECTIONS) {
            val declaredValue = counts.entries[c] as? PortableValue.Num ?: continue
            if (!isIntegerLiteral(declaredValue.literal)) continue
            val declared = declaredValue.literal.toLongOrNull() ?: continue
            val arr = root.entries[c] as? PortableValue.Arr
            val actual = arr?.items?.size ?: 0
            if (declared != actual.toLong()) {
                fail(
                    PortableRejectCategory.COUNTS_MISMATCH,
                    "counts.$c declares $declared but the $c array holds $actual records",
                    "$.counts.$c",
                )
            }
        }
    }

    // --- ValidatedPackage assembly ---

    private fun buildValidated(root: PortableValue.Obj): ValidatedPackage {
        val canonicalBytes = PortableJson.write(root)
        val identity = PortableJson.sha256Hex(canonicalBytes)
        val scopeId = (root.entries["scopeId"] as PortableValue.Str).value
        val sourcePlatform = (root.entries["sourcePlatform"] as PortableValue.Str).value
        val generatorObj = root.entries["generator"] as PortableValue.Obj
        val genName = (generatorObj.entries["name"] as PortableValue.Str).value
        val genVersion = (generatorObj.entries["version"] as PortableValue.Str).value
        val generator = "$genName $genVersion"
        val generatorJson = String(PortableJson.write(generatorObj), UTF_8).trimEnd('\n')
        val createdAtEpochMs = (root.entries["createdAtEpochMs"] as PortableValue.Num).literal.toLong()
        val countsObj = root.entries["counts"] as PortableValue.Obj
        val counts = PortableSpec.COLLECTIONS.associateWith { c ->
            (countsObj.entries[c] as PortableValue.Num).literal.toInt()
        }

        var packageSynthetic = genName == "synthetic-fixture-gen"
        val records = LinkedHashMap<String, List<ValidatedRecord>>()
        for (c in PortableSpec.COLLECTIONS) {
            val arr = root.entries[c] as PortableValue.Arr
            val list = ArrayList<ValidatedRecord>(arr.items.size)
            for ((i, item) in arr.items.withIndex()) {
                val o = item as PortableValue.Obj
                val recSynthetic = isSynthetic(o)
                if (recSynthetic) packageSynthetic = true
                val canonicalJson = String(PortableJson.write(o), UTF_8).trimEnd('\n')
                list.add(
                    ValidatedRecord(
                        collection = c,
                        ordinal = i,
                        canonicalJson = canonicalJson,
                        stableId = (o.entries["stableId"] as PortableValue.Str).value,
                        sourceScopeId = (o.entries["sourceScopeId"] as PortableValue.Str).value,
                        origin = (o.entries["origin"] as PortableValue.Str).value,
                        synthetic = recSynthetic,
                    ),
                )
            }
            records[c] = list
        }
        val sourceTag = if (packageSynthetic) "SYNTHETIC-TEST-FIXTURE" else "PORTABLE-V1"
        return ValidatedPackage(
            root = root,
            canonicalBytes = canonicalBytes,
            identity = identity,
            scopeId = scopeId,
            sourcePlatform = sourcePlatform,
            generator = generator,
            generatorJson = generatorJson,
            createdAtEpochMs = createdAtEpochMs,
            counts = counts,
            sourceTag = sourceTag,
            synthetic = packageSynthetic,
            records = records,
        )
    }

    private fun isSynthetic(o: PortableValue.Obj): Boolean {
        val extra = o.entries["extra"] as? PortableValue.Obj ?: return false
        return (extra.entries["synthetic"] as? PortableValue.Bool)?.value == true
    }

    // --- small helpers ---

    private fun fail(category: PortableRejectCategory, detail: String, location: String): Nothing =
        throw PortableContractException(PortableReject(category, detail, location))

    private fun child(path: String, key: String): String = "$path.$key"

    private fun isIntegerLiteral(literal: String): Boolean =
        literal.matches(Regex("-?(0|[1-9][0-9]*)"))

    private fun typeName(v: PortableValue): String = when (v) {
        is PortableValue.Obj -> "object"
        is PortableValue.Arr -> "array"
        is PortableValue.Str -> "string"
        is PortableValue.Num -> if (isIntegerLiteral(v.literal)) "integer" else "number"
        is PortableValue.Bool -> "boolean"
        PortableValue.Null -> "null"
    }

    private fun jsonRepr(v: PortableValue): String = when (v) {
        is PortableValue.Str -> "\"${v.value}\""
        is PortableValue.Num -> v.literal
        is PortableValue.Bool -> v.value.toString()
        PortableValue.Null -> "null"
        is PortableValue.Obj -> "object"
        is PortableValue.Arr -> "array"
    }
}
