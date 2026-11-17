namespace OjNexus.Windows.Core.Portable;

using System.Collections.Frozen;

/// <summary>
/// Frozen shared constants for Portable Workspace v1 (phase 132, ported from
/// Android <c>PortableModels.kt</c>). Wire values stay snake_case.
/// Contract binding: contracts/portable/v1/schema.json + check.py (phase 131).
/// </summary>
public static class PortableSpec
{
    public const int MaxPackageBytes = 64 * 1024 * 1024;
    public const int MaxStringBytes = 16 * 1024;
    public const int MaxRecords = 50_000;
    public const int FormatVersion = 1;
    public const string SchemaResource = "/portable/v1/schema.json";

    public static readonly IReadOnlyList<string> Collections = new[]
    {
        "problems", "attempts", "notes", "reviews", "sessions",
    };

    public static readonly FrozenSet<string> Categories = new[]
    {
        "INVALID_INPUT",
        "UNSUPPORTED_VERSION",
        "UNKNOWN_FIELD",
        "DUPLICATE_ID",
        "MISSING_REFERENCE",
        "SIZE_LIMIT",
        "PATH_UNSAFE",
        "COUNTS_MISMATCH",
    }.ToFrozenSet(StringComparer.Ordinal);

    public const string StableIdPattern = @"^[A-Za-z0-9._:-]{1,128}$";
    public const string ExternalIdPattern = @"^[A-Za-z0-9._:-]{1,64}$";
    public const string DayKeyPattern = @"^\d{4}-\d{2}-\d{2}$";
    public const string SourceUrlPrefix = "https?://.*";

    public static readonly FrozenSet<string> CredentialTokens = new[]
    {
        "token", "password", "secret", "cookie", "credential", "apikey",
        "authorization", "sessionid", "privatekey", "accesskey",
    }.ToFrozenSet(StringComparer.Ordinal);

    public static readonly FrozenSet<string> Judges = new[]
    {
        "codeforces", "atcoder", "luogu",
    }.ToFrozenSet(StringComparer.Ordinal);

    public static readonly FrozenSet<string> Origins = new[]
    {
        "scope", "unassigned",
    }.ToFrozenSet(StringComparer.Ordinal);

    public static readonly FrozenSet<string> Platforms = new[]
    {
        "android", "windows", "linux", "macos", "ios",
    }.ToFrozenSet(StringComparer.Ordinal);

    public static readonly FrozenSet<string> Verdicts = new[]
    {
        "AC", "WA", "TLE", "MLE", "RE", "CE", "PE", "OTHER",
    }.ToFrozenSet(StringComparer.Ordinal);

    public static readonly FrozenSet<string> LastResults = new[]
    {
        "PASS", "HARD", "FAIL", "SKIP",
    }.ToFrozenSet(StringComparer.Ordinal);

    public static readonly FrozenSet<string> SessionTypes = new[]
    {
        "PRACTICE", "FOCUS", "UPSOLVE", "REVIEW",
    }.ToFrozenSet(StringComparer.Ordinal);

    public static readonly FrozenSet<string> SessionStates = new[]
    {
        "PLANNED", "RUNNING", "PAUSED", "FINISHED", "CANCELLED",
    }.ToFrozenSet(StringComparer.Ordinal);

    /// <summary>Keys allowed on the package root (closed object).</summary>
    public static readonly FrozenSet<string> RootKeys = new[]
    {
        "formatVersion", "scopeId", "sourcePlatform", "generator",
        "createdAtEpochMs", "counts", "problems", "attempts", "notes",
        "reviews", "sessions",
    }.ToFrozenSet(StringComparer.Ordinal);

    public static readonly FrozenSet<string> RootRequired = RootKeys;

    /// <summary>Keys allowed on every collection entity (closed object).</summary>
    public static readonly FrozenSet<string> EntityBaseKeys = new[]
    {
        "stableId", "sourceScopeId", "origin", "extra",
    }.ToFrozenSet(StringComparer.Ordinal);

    public static readonly FrozenDictionary<string, FrozenSet<string>> EntityKeys =
        new Dictionary<string, FrozenSet<string>>(StringComparer.Ordinal)
        {
            ["problems"] = EntityBaseKeys.Union(new[]
            {
                "judge", "externalId", "title", "solved", "dayKey", "sourceUrl",
                "firstSolvedAtEpochMs", "difficulty", "tags",
            }).ToFrozenSet(StringComparer.Ordinal),
            ["attempts"] = EntityBaseKeys.Union(new[]
            {
                "problemId", "timestampEpochMs", "dayKey", "verdict", "rawVerdict",
                "language", "durationMin", "externalSubmissionId", "note",
            }).ToFrozenSet(StringComparer.Ordinal),
            ["notes"] = EntityBaseKeys.Union(new[]
            {
                "problemId", "keyInsight", "implementationNotes", "complexity",
                "general", "updatedEpochMs", "dayKey",
            }).ToFrozenSet(StringComparer.Ordinal),
            ["reviews"] = EntityBaseKeys.Union(new[]
            {
                "problemId", "stage", "dueEpochMs", "dueDayKey", "lastResult",
                "lastReviewedEpochMs", "createdEpochMs", "dayKey",
            }).ToFrozenSet(StringComparer.Ordinal),
            ["sessions"] = EntityBaseKeys.Union(new[]
            {
                "type", "state", "startedEpochMs", "finishedEpochMs",
                "totalPausedMs", "targetDurationMin", "problemIds", "dayKey",
            }).ToFrozenSet(StringComparer.Ordinal),
        }.ToFrozenDictionary(StringComparer.Ordinal);

    /// <summary>Required keys per entity (the four base keys plus the collection-specific ones).</summary>
    public static readonly FrozenDictionary<string, FrozenSet<string>> EntityRequired =
        new Dictionary<string, FrozenSet<string>>(StringComparer.Ordinal)
        {
            ["problems"] = new[]
            {
                "stableId", "sourceScopeId", "origin", "judge", "externalId",
                "title", "solved", "dayKey",
            }.ToFrozenSet(StringComparer.Ordinal),
            ["attempts"] = new[]
            {
                "stableId", "sourceScopeId", "origin", "problemId",
                "timestampEpochMs", "verdict", "dayKey",
            }.ToFrozenSet(StringComparer.Ordinal),
            ["notes"] = new[]
            {
                "stableId", "sourceScopeId", "origin", "problemId", "keyInsight",
                "implementationNotes", "complexity", "general", "updatedEpochMs",
                "dayKey",
            }.ToFrozenSet(StringComparer.Ordinal),
            ["reviews"] = new[]
            {
                "stableId", "sourceScopeId", "origin", "problemId", "stage",
                "dueEpochMs", "dueDayKey", "createdEpochMs", "dayKey",
            }.ToFrozenSet(StringComparer.Ordinal),
            ["sessions"] = new[]
            {
                "stableId", "sourceScopeId", "origin", "type", "state",
                "startedEpochMs", "totalPausedMs", "dayKey",
            }.ToFrozenSet(StringComparer.Ordinal),
        }.ToFrozenDictionary(StringComparer.Ordinal);
}

/// <summary>Every rejection carries exactly one of these categories (matching check.py).</summary>
public enum PortableRejectCategory
{
    InvalidInput,
    UnsupportedVersion,
    UnknownField,
    DuplicateId,
    MissingReference,
    SizeLimit,
    PathUnsafe,
    CountsMismatch,
}

public static class PortableRejectCategoryWire
{
    public static string Wire(this PortableRejectCategory category) => category switch
    {
        PortableRejectCategory.InvalidInput => "INVALID_INPUT",
        PortableRejectCategory.UnsupportedVersion => "UNSUPPORTED_VERSION",
        PortableRejectCategory.UnknownField => "UNKNOWN_FIELD",
        PortableRejectCategory.DuplicateId => "DUPLICATE_ID",
        PortableRejectCategory.MissingReference => "MISSING_REFERENCE",
        PortableRejectCategory.SizeLimit => "SIZE_LIMIT",
        PortableRejectCategory.PathUnsafe => "PATH_UNSAFE",
        PortableRejectCategory.CountsMismatch => "COUNTS_MISMATCH",
        _ => throw new ArgumentOutOfRangeException(nameof(category), category, null),
    };
}

/// <summary>Why a record landed in a given capability tier.</summary>
public enum PortableTier
{
    Editable,
    StoredOnly,
    Rejected,
}

public enum PortableTierReason
{
    LiveDomainUi,
    BoundedArchiveNoDomainUi,
    UnknownField,
    IncompatibleEnum,
    IncompatibleVersion,
    IncompatibleReference,
}

public static class PortableTierReasonWire
{
    public static string Wire(this PortableTierReason reason) => reason switch
    {
        PortableTierReason.LiveDomainUi => "live_domain_ui",
        PortableTierReason.BoundedArchiveNoDomainUi => "bounded_archive_no_domain_ui",
        PortableTierReason.UnknownField => "unknown_field",
        PortableTierReason.IncompatibleEnum => "incompatible_enum",
        PortableTierReason.IncompatibleVersion => "incompatible_version",
        PortableTierReason.IncompatibleReference => "incompatible_reference",
        _ => throw new ArgumentOutOfRangeException(nameof(reason), reason, null),
    };
}

/// <summary>A single capability-tier decision, reported in the preview.</summary>
public sealed record TierDecision(
    string Collection,
    string StableId,
    PortableTier Tier,
    PortableTierReason Reason);

/// <summary>Per-source-scope provenance counts shown in the preview.</summary>
public sealed record SourceBreakdown(
    string SourceScopeId,
    int RecordCount);

/// <summary>A conflict surfaced (never auto-resolved) in the preview.</summary>
public sealed record PortableConflict(
    string Collection,
    string StableId,
    string Kind,
    string Detail);

/// <summary>Read-only preview result. Producing it MUST NOT write to the database.</summary>
public sealed record PortablePreview(
    string ScopeId,
    string SourcePlatform,
    string Generator,
    long CreatedAtEpochMs,
    IReadOnlyDictionary<string, int> Counts,
    IReadOnlyList<SourceBreakdown> Sources,
    IReadOnlyList<TierDecision> Decisions,
    IReadOnlyList<PortableConflict> Conflicts,
    IReadOnlyList<TierDecision> Skipped,
    int EditableCount,
    int StoredOnlyCount,
    int RejectedCount,
    bool AcceptAll,
    /// <summary>Canonical bytes of the whole validated package, kept for the confirm step.</summary>
    byte[] CanonicalBytes,
    /// <summary>SHA-256 hex of CanonicalBytes, for lossless round-trip verification.</summary>
    string Identity);

/// <summary>Result of a confirmed transactional apply.</summary>
public sealed record PortableApplyResult(
    string Identity,
    int EditableCount,
    int StoredOnlyCount,
    int RejectedCount,
    int RowsUpserted,
    int RowsDeleted,
    /// <summary>True when the incoming identity equals the stored one: the apply was a no-op.</summary>
    bool Identical = false);

/// <summary>Wire-level rejection surfaced to the UI as a short status line.</summary>
public sealed record PortableReject(
    PortableRejectCategory Category,
    string Detail,
    string Location = "")
{
    public string Wire => Category.Wire();

    public override string ToString() => $"{Category.Wire()}: {Detail} @ {Location}";
}

public sealed class PortableContractException(PortableReject reject)
    : ArgumentException($"{reject.Category.Wire()}: {reject.Detail} @ {reject.Location}")
{
    public PortableReject Reject { get; } = reject;
}
