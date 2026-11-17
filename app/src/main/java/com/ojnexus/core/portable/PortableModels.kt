package com.ojnexus.core.portable

/**
 * Frozen shared models for Portable Workspace v1 (phase 132).
 *
 * These types are the single source of truth for every portable task. They are
 * pure Kotlin (no Room, no Android, no Compose) so the codec, store, repository,
 * UI and tests can be developed against one stable contract.
 *
 * Contract binding: contracts/portable/v1/schema.json + check.py (phase 131).
 */
object PortableSpec {
    const val MAX_PACKAGE_BYTES: Int = 64 * 1024 * 1024
    const val MAX_STRING_BYTES: Int = 16 * 1024
    const val MAX_RECORDS: Int = 50_000
    const val FORMAT_VERSION: Int = 1
    const val SCHEMA_RESOURCE: String = "/portable/v1/schema.json"

    val COLLECTIONS: List<String> = listOf(
        "problems", "attempts", "notes", "reviews", "sessions",
    )

    val CATEGORIES: Set<String> = setOf(
        "INVALID_INPUT",
        "UNSUPPORTED_VERSION",
        "UNKNOWN_FIELD",
        "DUPLICATE_ID",
        "MISSING_REFERENCE",
        "SIZE_LIMIT",
        "PATH_UNSAFE",
        "COUNTS_MISMATCH",
    )

    const val STABLE_ID_PATTERN: String = "^[A-Za-z0-9._:-]{1,128}$"
    const val EXTERNAL_ID_PATTERN: String = "^[A-Za-z0-9._:-]{1,64}$"
    const val DAY_KEY_PATTERN: String = "^\\d{4}-\\d{2}-\\d{2}$"
    const val SOURCE_URL_PREFIX: String = "https?://.*"

    val CREDENTIAL_TOKENS: Set<String> = setOf(
        "token", "password", "secret", "cookie", "credential", "apikey",
        "authorization", "sessionid", "privatekey", "accesskey",
    )

    val JUDGES: Set<String> = setOf("codeforces", "atcoder", "luogu")
    val ORIGINS: Set<String> = setOf("scope", "unassigned")
    val PLATFORMS: Set<String> = setOf("android", "windows", "linux", "macos", "ios")
    val VERDICTS: Set<String> = setOf("AC", "WA", "TLE", "MLE", "RE", "CE", "PE", "OTHER")
    val LAST_RESULTS: Set<String> = setOf("PASS", "HARD", "FAIL", "SKIP")
    val SESSION_TYPES: Set<String> = setOf("PRACTICE", "FOCUS", "UPSOLVE", "REVIEW")
    val SESSION_STATES: Set<String> =
        setOf("PLANNED", "RUNNING", "PAUSED", "FINISHED", "CANCELLED")

    /** Keys allowed on the package root (closed object). */
    val ROOT_KEYS: Set<String> = setOf(
        "formatVersion", "scopeId", "sourcePlatform", "generator",
        "createdAtEpochMs", "counts", "problems", "attempts", "notes",
        "reviews", "sessions",
    )
    val ROOT_REQUIRED: Set<String> = ROOT_KEYS

    /** Keys allowed on every collection entity (closed object). */
    val ENTITY_BASE_KEYS: Set<String> =
        setOf("stableId", "sourceScopeId", "origin", "extra")

    val ENTITY_KEYS: Map<String, Set<String>> = mapOf(
        "problems" to ENTITY_BASE_KEYS + setOf(
            "judge", "externalId", "title", "solved", "dayKey", "sourceUrl",
            "firstSolvedAtEpochMs", "difficulty", "tags",
        ),
        "attempts" to ENTITY_BASE_KEYS + setOf(
            "problemId", "timestampEpochMs", "dayKey", "verdict", "rawVerdict",
            "language", "durationMin", "externalSubmissionId", "note",
        ),
        "notes" to ENTITY_BASE_KEYS + setOf(
            "problemId", "keyInsight", "implementationNotes", "complexity",
            "general", "updatedEpochMs", "dayKey",
        ),
        "reviews" to ENTITY_BASE_KEYS + setOf(
            "problemId", "stage", "dueEpochMs", "dueDayKey", "lastResult",
            "lastReviewedEpochMs", "createdEpochMs", "dayKey",
        ),
        "sessions" to ENTITY_BASE_KEYS + setOf(
            "type", "state", "startedEpochMs", "finishedEpochMs",
            "totalPausedMs", "targetDurationMin", "problemIds", "dayKey",
        ),
    )

    /** Required keys per entity (the four base keys plus the collection-specific ones). */
    val ENTITY_REQUIRED: Map<String, Set<String>> = mapOf(
        "problems" to setOf(
            "stableId", "sourceScopeId", "origin", "judge", "externalId",
            "title", "solved", "dayKey",
        ),
        "attempts" to setOf(
            "stableId", "sourceScopeId", "origin", "problemId",
            "timestampEpochMs", "verdict", "dayKey",
        ),
        "notes" to setOf(
            "stableId", "sourceScopeId", "origin", "problemId", "keyInsight",
            "implementationNotes", "complexity", "general", "updatedEpochMs",
            "dayKey",
        ),
        "reviews" to setOf(
            "stableId", "sourceScopeId", "origin", "problemId", "stage",
            "dueEpochMs", "dueDayKey", "createdEpochMs", "dayKey",
        ),
        "sessions" to setOf(
            "stableId", "sourceScopeId", "origin", "type", "state",
            "startedEpochMs", "totalPausedMs", "dayKey",
        ),
    )
}

/** Every rejection carries exactly one of these categories (matching check.py). */
enum class PortableRejectCategory(val wire: String) {
    INVALID_INPUT("INVALID_INPUT"),
    UNSUPPORTED_VERSION("UNSUPPORTED_VERSION"),
    UNKNOWN_FIELD("UNKNOWN_FIELD"),
    DUPLICATE_ID("DUPLICATE_ID"),
    MISSING_REFERENCE("MISSING_REFERENCE"),
    SIZE_LIMIT("SIZE_LIMIT"),
    PATH_UNSAFE("PATH_UNSAFE"),
    COUNTS_MISMATCH("COUNTS_MISMATCH"),
}

/** Why a record landed in a given capability tier. */
enum class PortableTier { EDITABLE, STORED_ONLY, REJECTED }

enum class PortableTierReason(val wire: String) {
    LIVE_DOMAIN_UI("live_domain_ui"),
    BOUNDED_ARCHIVE_NO_DOMAIN_UI("bounded_archive_no_domain_ui"),
    UNKNOWN_FIELD("unknown_field"),
    INCOMPATIBLE_ENUM("incompatible_enum"),
    INCOMPATIBLE_VERSION("incompatible_version"),
    INCOMPATIBLE_REFERENCE("incompatible_reference"),
}

/** A single capability-tier decision, reported in the preview. */
data class TierDecision(
    val collection: String,
    val stableId: String,
    val tier: PortableTier,
    val reason: PortableTierReason,
)

/** Per-source-scope provenance counts shown in the preview. */
data class SourceBreakdown(
    val sourceScopeId: String,
    val recordCount: Int,
)

/** A conflict surfaced (never auto-resolved) in the preview. */
data class PortableConflict(
    val collection: String,
    val stableId: String,
    val kind: String,
    val detail: String,
)

/** Read-only preview result. Producing it MUST NOT write to the database. */
data class PortablePreview(
    val scopeId: String,
    val sourcePlatform: String,
    val generator: String,
    val createdAtEpochMs: Long,
    val counts: Map<String, Int>,
    val sources: List<SourceBreakdown>,
    val decisions: List<TierDecision>,
    val conflicts: List<PortableConflict>,
    val skipped: List<TierDecision>,
    val editableCount: Int,
    val storedOnlyCount: Int,
    val rejectedCount: Int,
    val acceptAll: Boolean,
    /** Canonical bytes of the whole validated package, kept for the confirm step. */
    val canonicalBytes: ByteArray,
    /** SHA-256 hex of canonicalBytes, for lossless round-trip verification. */
    val identity: String,
)

/** Result of a confirmed transactional apply. */
data class PortableApplyResult(
    val identity: String,
    val editableCount: Int,
    val storedOnlyCount: Int,
    val rejectedCount: Int,
    val rowsUpserted: Int,
    val rowsDeleted: Int,
    /** True when the incoming identity equals the stored one: the apply was a no-op. */
    val identical: Boolean = false,
)

/** Wire-level rejection surfaced to the UI as a short status line. */
data class PortableReject(
    val category: PortableRejectCategory,
    val detail: String,
    val location: String = "",
) {
    val wire: String get() = category.wire
}

class PortableContractException(val reject: PortableReject) :
    IllegalArgumentException("${reject.category.wire}: ${reject.detail} @ ${reject.location}")
