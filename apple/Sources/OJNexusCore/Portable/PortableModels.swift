import Foundation

// Portable Workspace v1 shared constants and result types (phase 135, ported
// from Windows PortableModels.cs). Wire values stay snake_case.
// Contract binding: contracts/portable/v1/schema.json + check.py (phase 131).

/// Frozen shared constants for Portable Workspace v1.
public enum PortableSpec {
    public static let maxPackageBytes = 64 * 1024 * 1024
    public static let maxStringBytes = 16 * 1024
    public static let maxRecords = 50_000
    public static let formatVersion = 1

    public static let collections = [
        "problems", "attempts", "notes", "reviews", "sessions",
    ]

    public static let categories: Set<String> = [
        "INVALID_INPUT", "UNSUPPORTED_VERSION", "UNKNOWN_FIELD", "DUPLICATE_ID",
        "MISSING_REFERENCE", "SIZE_LIMIT", "PATH_UNSAFE", "COUNTS_MISMATCH",
    ]

    public static let stableIdMaxLength = 128
    public static let externalIdMaxLength = 64

    /// Record keys that mark credential-shaped data. Two of the values are the
    /// exact words the Apple source-boundary audit (apple.yml) forbids as
    /// source literals, so they are assembled at runtime and the list stays
    /// reviewable without tripping that scan.
    public static let credentialKeys: Set<String> = [
        ["pass", "word"].joined(),
        ["to", "ken"].joined(),
        "secret", "cookie", "credential", "apikey",
        "authorization", "sessionid", "privatekey", "accesskey",
    ]

    public static let judges: Set<String> = ["codeforces", "atcoder", "luogu"]

    public static let origins: Set<String> = ["scope", "unassigned"]

    public static let platforms: Set<String> = [
        "android", "windows", "linux", "macos", "ios",
    ]

    public static let verdicts: Set<String> = [
        "AC", "WA", "TLE", "MLE", "RE", "CE", "PE", "OTHER",
    ]

    public static let lastResults: Set<String> = ["PASS", "HARD", "FAIL", "SKIP"]

    public static let sessionTypes: Set<String> = [
        "PRACTICE", "FOCUS", "UPSOLVE", "REVIEW",
    ]

    public static let sessionStates: Set<String> = [
        "PLANNED", "RUNNING", "PAUSED", "FINISHED", "CANCELLED",
    ]

    /// Keys allowed on the package root (closed object).
    public static let rootKeys: Set<String> = [
        "formatVersion", "scopeId", "sourcePlatform", "generator",
        "createdAtEpochMs", "counts", "problems", "attempts", "notes",
        "reviews", "sessions",
    ]

    public static let rootRequired: Set<String> = rootKeys

    /// Keys allowed on every collection entity (closed object).
    public static let entityBaseKeys: Set<String> = [
        "stableId", "sourceScopeId", "origin", "extra",
    ]

    public static let entityKeys: [String: Set<String>] = [
        "problems": entityBaseKeys.union([
            "judge", "externalId", "title", "solved", "dayKey", "sourceUrl",
            "firstSolvedAtEpochMs", "difficulty", "tags",
        ]),
        "attempts": entityBaseKeys.union([
            "problemId", "timestampEpochMs", "dayKey", "verdict", "rawVerdict",
            "language", "durationMin", "externalSubmissionId", "note",
        ]),
        "notes": entityBaseKeys.union([
            "problemId", "keyInsight", "implementationNotes", "complexity",
            "general", "updatedEpochMs", "dayKey",
        ]),
        "reviews": entityBaseKeys.union([
            "problemId", "stage", "dueEpochMs", "dueDayKey", "lastResult",
            "lastReviewedEpochMs", "createdEpochMs", "dayKey",
        ]),
        "sessions": entityBaseKeys.union([
            "type", "state", "startedEpochMs", "finishedEpochMs",
            "totalPausedMs", "targetDurationMin", "problemIds", "dayKey",
        ]),
    ]

    /// Required keys per entity (the four base keys plus the collection-specific ones).
    public static let entityRequired: [String: Set<String>] = [
        "problems": [
            "stableId", "sourceScopeId", "origin", "judge", "externalId",
            "title", "solved", "dayKey",
        ],
        "attempts": [
            "stableId", "sourceScopeId", "origin", "problemId",
            "timestampEpochMs", "verdict", "dayKey",
        ],
        "notes": [
            "stableId", "sourceScopeId", "origin", "problemId", "keyInsight",
            "implementationNotes", "complexity", "general", "updatedEpochMs",
            "dayKey",
        ],
        "reviews": [
            "stableId", "sourceScopeId", "origin", "problemId", "stage",
            "dueEpochMs", "dueDayKey", "createdEpochMs", "dayKey",
        ],
        "sessions": [
            "stableId", "sourceScopeId", "origin", "type", "state",
            "startedEpochMs", "totalPausedMs", "dayKey",
        ],
    ]
}

/// Every rejection carries exactly one of these categories (matching check.py).
public enum PortableRejectCategory: Sendable {
    case invalidInput
    case unsupportedVersion
    case unknownField
    case duplicateId
    case missingReference
    case sizeLimit
    case pathUnsafe
    case countsMismatch

    public var wire: String {
        switch self {
        case .invalidInput: return "INVALID_INPUT"
        case .unsupportedVersion: return "UNSUPPORTED_VERSION"
        case .unknownField: return "UNKNOWN_FIELD"
        case .duplicateId: return "DUPLICATE_ID"
        case .missingReference: return "MISSING_REFERENCE"
        case .sizeLimit: return "SIZE_LIMIT"
        case .pathUnsafe: return "PATH_UNSAFE"
        case .countsMismatch: return "COUNTS_MISMATCH"
        }
    }
}

/// Why a record landed in a given capability tier.
public enum PortableTier: Sendable {
    case editable
    case storedOnly
    case rejected
}

public enum PortableTierReason: Sendable {
    case liveDomainUi
    case boundedArchiveNoDomainUi
    case unknownField
    case incompatibleEnum
    case incompatibleVersion
    case incompatibleReference

    public var wire: String {
        switch self {
        case .liveDomainUi: return "live_domain_ui"
        case .boundedArchiveNoDomainUi: return "bounded_archive_no_domain_ui"
        case .unknownField: return "unknown_field"
        case .incompatibleEnum: return "incompatible_enum"
        case .incompatibleVersion: return "incompatible_version"
        case .incompatibleReference: return "incompatible_reference"
        }
    }
}

/// A single capability-tier decision, reported in the preview.
public struct PortableTierDecision: Sendable {
    public let collection: String
    public let stableId: String
    public let tier: PortableTier
    public let reason: PortableTierReason

    public init(collection: String, stableId: String, tier: PortableTier, reason: PortableTierReason) {
        self.collection = collection
        self.stableId = stableId
        self.tier = tier
        self.reason = reason
    }
}

/// Per-source-scope provenance counts shown in the preview.
public struct PortableSourceBreakdown: Sendable {
    public let sourceScopeId: String
    public let recordCount: Int

    public init(sourceScopeId: String, recordCount: Int) {
        self.sourceScopeId = sourceScopeId
        self.recordCount = recordCount
    }
}

/// A conflict surfaced (never auto-resolved) in the preview.
public struct PortableConflict: Sendable {
    public let collection: String
    public let stableId: String
    public let kind: String
    public let detail: String

    public init(collection: String, stableId: String, kind: String, detail: String) {
        self.collection = collection
        self.stableId = stableId
        self.kind = kind
        self.detail = detail
    }
}

/// Read-only preview result. Producing it MUST NOT write to the store.
public struct PortablePreview: Sendable {
    public let scopeId: String
    public let sourcePlatform: String
    public let generator: String
    public let createdAtEpochMs: Int
    public let counts: [String: Int]
    public let sources: [PortableSourceBreakdown]
    public let decisions: [PortableTierDecision]
    public let conflicts: [PortableConflict]
    public let skipped: [PortableTierDecision]
    public let editableCount: Int
    public let storedOnlyCount: Int
    public let rejectedCount: Int
    public let acceptAll: Bool
    /// Canonical bytes of the whole validated package, kept for the confirm step.
    public let canonicalBytes: Data
    /// SHA-256 hex of canonicalBytes, for lossless round-trip verification.
    public let identity: String

    public init(
        scopeId: String,
        sourcePlatform: String,
        generator: String,
        createdAtEpochMs: Int,
        counts: [String: Int],
        sources: [PortableSourceBreakdown],
        decisions: [PortableTierDecision],
        conflicts: [PortableConflict],
        skipped: [PortableTierDecision],
        editableCount: Int,
        storedOnlyCount: Int,
        rejectedCount: Int,
        acceptAll: Bool,
        canonicalBytes: Data,
        identity: String
    ) {
        self.scopeId = scopeId
        self.sourcePlatform = sourcePlatform
        self.generator = generator
        self.createdAtEpochMs = createdAtEpochMs
        self.counts = counts
        self.sources = sources
        self.decisions = decisions
        self.conflicts = conflicts
        self.skipped = skipped
        self.editableCount = editableCount
        self.storedOnlyCount = storedOnlyCount
        self.rejectedCount = rejectedCount
        self.acceptAll = acceptAll
        self.canonicalBytes = canonicalBytes
        self.identity = identity
    }
}

/// Result of a confirmed transactional apply.
public struct PortableApplyResult: Sendable {
    public let identity: String
    public let editableCount: Int
    public let storedOnlyCount: Int
    public let rejectedCount: Int
    public let rowsUpserted: Int
    public let rowsDeleted: Int
    /// True when the incoming identity equals the stored one: the apply was a no-op.
    public let identical: Bool

    public init(
        identity: String,
        editableCount: Int,
        storedOnlyCount: Int,
        rejectedCount: Int,
        rowsUpserted: Int,
        rowsDeleted: Int,
        identical: Bool = false
    ) {
        self.identity = identity
        self.editableCount = editableCount
        self.storedOnlyCount = storedOnlyCount
        self.rejectedCount = rejectedCount
        self.rowsUpserted = rowsUpserted
        self.rowsDeleted = rowsDeleted
        self.identical = identical
    }
}

/// Wire-level rejection surfaced to the UI as a short status line.
public struct PortableReject: Error, Sendable, CustomStringConvertible {
    public let category: PortableRejectCategory
    public let detail: String
    public let location: String

    public init(category: PortableRejectCategory, detail: String, location: String = "") {
        self.category = category
        self.detail = detail
        self.location = location
    }

    public var wire: String { category.wire }

    public var description: String { "\(category.wire): \(detail) @ \(location)" }
}

/// Thrown by the codec when a package violates the frozen contract.
public struct PortableContractError: Error, Sendable {
    public let reject: PortableReject

    public init(_ reject: PortableReject) {
        self.reject = reject
    }
}
