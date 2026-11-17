using System.Diagnostics.CodeAnalysis;
using System.Security.Cryptography;
using System.Text;
using System.Text.RegularExpressions;

namespace OjNexus.Windows.Core.Portable;

/// <summary>One validated record in document order, with its canonical JSON payload.</summary>
public sealed record ValidatedRecord(
    string Collection,
    int Ordinal,
    string CanonicalJson,
    string StableId,
    string SourceScopeId,
    string Origin,
    bool Synthetic);

/// <summary>A fully validated package: manifest data plus per-collection records.</summary>
public sealed record ValidatedPackage(
    PortableValue.Obj Root,
    byte[] CanonicalBytes,
    string Identity,
    string ScopeId,
    string SourcePlatform,
    string Generator,
    string GeneratorJson,
    long CreatedAtEpochMs,
    IReadOnlyDictionary<string, int> Counts,
    string SourceTag,
    bool Synthetic,
    IReadOnlyDictionary<string, IReadOnlyList<ValidatedRecord>> Records);

/// <summary>
/// Validates and classifies portable packages. Stateless; safe to call from a
/// preview (read-only) or from an apply (before any store write).
/// </summary>
public sealed class PortableCodec
{
    private const int MaxLen = 16_384; // schema maxLength counts characters, not bytes
    private static readonly Regex PathUnsafeRegex = CompilePattern("""(^[A-Za-z]:\\)|^/|\.\./|^\.\.$""");
    private static readonly Dictionary<string, Rule.TObj> EntitySchemas;
    private static readonly Rule.TObj RootSchema;
    private static readonly Rule.TObj GeneratorSchema;
    private static readonly Rule.TObj CountsSchema;

    static PortableCodec()
    {
        var stableId = Str(PortableSpec.StableIdPattern, maxLen: MaxLen);
        var dayKey = Str(PortableSpec.DayKeyPattern, maxLen: MaxLen);
        var origin = Str(enumSet: PortableSpec.Origins);
        // `extra` is declared as `{"type":"object"}` only: value shapes and depth
        // are enforced by the checker beyond the schema subset, not here.
        var extra = new Rule.TObj(new string[0], new Dictionary<string, Rule>(), false);
        var plainStr = Str(maxLen: MaxLen);
        var epoch = Int(min: 0);

        (string, Rule)[] EntityBase() =>
        [
            ("stableId", stableId),
            ("sourceScopeId", stableId),
            ("origin", origin),
            ("extra", extra),
        ];

        var problemsEntity = Obj(
            ["stableId", "sourceScopeId", "origin", "judge", "externalId", "title", "solved", "dayKey"],
            EntityBase().Concat(new (string, Rule)[]
            {
                ("judge", Str(enumSet: PortableSpec.Judges)),
                ("externalId", Str(PortableSpec.ExternalIdPattern, maxLen: MaxLen)),
                ("title", plainStr),
                ("sourceUrl", Str("^https?://", maxLen: MaxLen)),
                ("solved", new Rule.TBool()),
                ("firstSolvedAtEpochMs", epoch),
                ("difficulty", Int()), // absent stays absent; never coerced to 0
                ("tags", new Rule.TArr(plainStr, null)),
                ("dayKey", dayKey),
            }).ToArray());

        var attemptsEntity = Obj(
            ["stableId", "sourceScopeId", "origin", "problemId", "timestampEpochMs", "verdict", "dayKey"],
            EntityBase().Concat(new (string, Rule)[]
            {
                ("problemId", stableId),
                ("timestampEpochMs", epoch),
                ("dayKey", dayKey),
                ("verdict", Str(enumSet: PortableSpec.Verdicts)),
                ("rawVerdict", plainStr),
                ("language", plainStr),
                ("durationMin", epoch),
                ("externalSubmissionId", plainStr),
                ("note", plainStr),
            }).ToArray());

        var notesEntity = Obj(
            ["stableId", "sourceScopeId", "origin", "problemId", "keyInsight", "implementationNotes", "complexity", "general", "updatedEpochMs", "dayKey"],
            EntityBase().Concat(new (string, Rule)[]
            {
                ("problemId", stableId),
                ("keyInsight", plainStr),
                ("implementationNotes", plainStr),
                ("complexity", plainStr),
                ("general", plainStr),
                ("updatedEpochMs", epoch),
                ("dayKey", dayKey),
            }).ToArray());

        var reviewsEntity = Obj(
            ["stableId", "sourceScopeId", "origin", "problemId", "stage", "dueEpochMs", "dueDayKey", "createdEpochMs", "dayKey"],
            EntityBase().Concat(new (string, Rule)[]
            {
                ("problemId", stableId),
                ("stage", Int(min: 1)),
                ("dueEpochMs", epoch),
                ("dueDayKey", dayKey),
                ("lastResult", Str(enumSet: PortableSpec.LastResults)),
                ("lastReviewedEpochMs", epoch),
                ("createdEpochMs", epoch),
                ("dayKey", dayKey),
            }).ToArray());

        var sessionsEntity = Obj(
            ["stableId", "sourceScopeId", "origin", "type", "state", "startedEpochMs", "totalPausedMs", "dayKey"],
            EntityBase().Concat(new (string, Rule)[]
            {
                ("type", Str(enumSet: PortableSpec.SessionTypes)),
                ("state", Str(enumSet: PortableSpec.SessionStates)),
                ("startedEpochMs", epoch),
                ("finishedEpochMs", epoch),
                ("totalPausedMs", epoch),
                ("targetDurationMin", epoch),
                ("problemIds", new Rule.TArr(Str(PortableSpec.StableIdPattern, maxLen: MaxLen), null)),
                ("dayKey", dayKey),
            }).ToArray());

        GeneratorSchema = Obj(
            ["name", "version"],
            ("name", Str(minLen: 1, maxLen: MaxLen)),
            ("version", Str(minLen: 1, maxLen: MaxLen)));

        CountsSchema = Obj(
            ["problems", "attempts", "notes", "reviews", "sessions"],
            ("problems", epoch),
            ("attempts", epoch),
            ("notes", epoch),
            ("reviews", epoch),
            ("sessions", epoch));

        static Rule.TArr Collection(Rule.TObj entity) => new(entity, PortableSpec.MaxRecords);

        EntitySchemas = new Dictionary<string, Rule.TObj>(StringComparer.Ordinal)
        {
            ["problems"] = problemsEntity,
            ["attempts"] = attemptsEntity,
            ["notes"] = notesEntity,
            ["reviews"] = reviewsEntity,
            ["sessions"] = sessionsEntity,
        };

        RootSchema = Obj(
            [
                "formatVersion", "scopeId", "sourcePlatform", "generator", "createdAtEpochMs",
                "counts", "problems", "attempts", "notes", "reviews", "sessions",
            ],
            ("formatVersion", Int(enumSet: new long[] { 1 })),
            ("scopeId", Str(PortableSpec.StableIdPattern, maxLen: MaxLen)),
            ("sourcePlatform", Str(enumSet: PortableSpec.Platforms)),
            ("generator", GeneratorSchema),
            ("createdAtEpochMs", epoch),
            ("counts", CountsSchema),
            ("problems", Collection(problemsEntity)),
            ("attempts", Collection(attemptsEntity)),
            ("notes", Collection(notesEntity)),
            ("reviews", Collection(reviewsEntity)),
            ("sessions", Collection(sessionsEntity)));
    }

    private abstract record Rule
    {
        public sealed record TObj(IReadOnlyList<string> Required, IReadOnlyDictionary<string, Rule> Props, bool Closed) : Rule;

        public sealed record TArr(Rule? Items, int? MaxItems) : Rule;

        public sealed record TStr(Regex? Pattern, int? MinLen, int? MaxLen, IReadOnlySet<string>? EnumSet) : Rule;

        public sealed record TInt(long? Min, long[]? EnumSet) : Rule;

        public sealed record TBool : Rule;
    }

    private static Rule.TStr Str(string? pattern = null, int? minLen = null, int? maxLen = null, IReadOnlySet<string>? enumSet = null) =>
        new(pattern is null ? null : CompilePattern(pattern), minLen, maxLen, enumSet);

    private static Rule.TInt Int(long? min = null, long[]? enumSet = null) => new(min, enumSet);

    private static Rule.TObj Obj(IReadOnlyList<string> required, params (string Key, Rule Rule)[] props)
    {
        var map = new Dictionary<string, Rule>(StringComparer.Ordinal);
        foreach (var (key, rule) in props)
        {
            map[key] = rule;
        }

        return new Rule.TObj(required, map, true);
    }

    /// <summary>
    /// Mirrors the Android codec's pattern handling: a terminal unescaped `$`
    /// becomes the absolute-end anchor (Android compiles to `\z` because Java's
    /// `$` is not absolute-end), and `\d` becomes `[0-9]` because Java's `\d`
    /// is ASCII-only while .NET's matches Unicode digits.
    /// </summary>
    private static Regex CompilePattern(string pattern)
    {
        var effective = pattern;
        if (effective.EndsWith("$") && !effective.EndsWith("\\$"))
        {
            effective = effective[..^1] + "\\z";
        }

        effective = effective.Replace(@"\d", "[0-9]", StringComparison.Ordinal);
        return new Regex(effective, RegexOptions.None, TimeSpan.FromSeconds(1));
    }

    /// <summary>Canonical bytes of a parsed value (delegates to the frozen writer).</summary>
    public byte[] CanonicalBytes(PortableValue value) => PortableJson.CanonicalBytes(value);

    /// <summary>SHA-256 hex identity of canonical bytes.</summary>
    public string Identity(byte[] canonicalBytes) =>
        Convert.ToHexString(SHA256.HashData(canonicalBytes)).ToLowerInvariant();

    /// <summary>
    /// Validate one package. Throws <see cref="PortableContractException"/> with
    /// exactly one category on the first failure. Performs NO writes of any kind.
    /// </summary>
    public ValidatedPackage Validate(byte[] bytes)
    {
        // 1. SIZE_LIMIT (raw size, before decode).
        if (bytes.Length > PortableSpec.MaxPackageBytes)
        {
            throw BuildReject(
                PortableRejectCategory.SizeLimit,
                $"package is {bytes.Length} bytes, limit is {PortableSpec.MaxPackageBytes}",
                "$");
        }

        // 2. INVALID_INPUT (strict decode).
        PortableValue parsed;
        try
        {
            parsed = PortableJson.Parse(bytes);
        }
        catch (PortableContractException)
        {
            throw;
        }
        catch (Exception exception)
        {
            throw new PortableContractException(
                new PortableReject(PortableRejectCategory.InvalidInput, exception.Message, "$"));
        }

        if (parsed is not PortableValue.Obj root)
        {
            throw BuildReject(
                PortableRejectCategory.InvalidInput,
                $"package root must be a JSON object, got {TypeName(parsed)}",
                "$");
        }

        // 3. INVALID_INPUT / UNSUPPORTED_VERSION (version gate, before schema).
        var formatVersion = root.Get("formatVersion");
        if (formatVersion is null)
        {
            throw BuildReject(
                PortableRejectCategory.InvalidInput,
                "missing required manifest key \"formatVersion\"",
                "$");
        }
        if (formatVersion is not PortableValue.Num versionNumber || !IsIntegerLiteral(versionNumber.Literal))
        {
            throw BuildReject(
                PortableRejectCategory.InvalidInput,
                $"formatVersion must be the integer 1, got {JsonRepr(formatVersion)}",
                "$.formatVersion");
        }

        if (versionNumber.Literal != "1")
        {
            throw BuildReject(
                PortableRejectCategory.UnsupportedVersion,
                $"formatVersion {versionNumber.Literal} is not supported by this contract (expected 1); " +
                    "future majors are rejected whole-package, never best-effort decoded",
                "$.formatVersion");
        }

        // 4. UNKNOWN_FIELD (draft-07 subset) + credential-shaped keys.
        ValidateSchema(root, RootSchema, "$");
        CheckCredentials(root, "$");

        // 5-9. Contract rules beyond the schema, in documented precedence order.
        CheckExtraDepth(root, "$");
        CheckCollectionSizes(root);
        CheckStringSizes(root, "$");
        CheckPathShapes(root, "$");
        CheckDuplicates(root);
        CheckReferences(root);
        CheckCounts(root);

        return BuildValidated(root);
    }

    /// <summary>
    /// Capability tiers for a validated package. Windows has live domain UI for
    /// problems and reviews, so those records are EDITABLE; every other
    /// collection is stored-only behind the bounded archive. All records are
    /// archived either way â€?the tier is a projection promise, not a storage
    /// decision.
    /// </summary>
    public IReadOnlyList<TierDecision> Classify(ValidatedPackage package)
    {
        var decisions = new List<TierDecision>();
        foreach (var collection in PortableSpec.Collections)
        {
            var editable = collection is "problems" or "reviews";
            foreach (var record in package.Records[collection])
            {
                decisions.Add(new TierDecision(
                    collection,
                    record.StableId,
                    editable ? PortableTier.Editable : PortableTier.StoredOnly,
                    editable ? PortableTierReason.LiveDomainUi : PortableTierReason.BoundedArchiveNoDomainUi));
            }
        }

        return decisions;
    }

    /// <summary>Read-only preview: assemble counts, sources, decisions and conflicts.</summary>
    public PortablePreview Preview(byte[] bytes)
    {
        var package = Validate(bytes);
        var decisions = Classify(package);
        var bySource = new SortedDictionary<string, int>(StringComparer.Ordinal);
        foreach (var collection in PortableSpec.Collections)
        {
            foreach (var record in package.Records[collection])
            {
                bySource.TryGetValue(record.SourceScopeId, out var current);
                bySource[record.SourceScopeId] = current + 1;
            }
        }

        var sources = bySource.Select(pair => new SourceBreakdown(pair.Key, pair.Value)).ToList();
        var editable = decisions.Count(d => d.Tier == PortableTier.Editable);
        var storedOnly = decisions.Count(d => d.Tier == PortableTier.StoredOnly);
        var rejected = decisions.Count(d => d.Tier == PortableTier.Rejected);
        return new PortablePreview(
            ScopeId: package.ScopeId,
            SourcePlatform: package.SourcePlatform,
            Generator: package.Generator,
            CreatedAtEpochMs: package.CreatedAtEpochMs,
            Counts: package.Counts,
            Sources: sources,
            Decisions: decisions,
            Conflicts: Array.Empty<PortableConflict>(),
            Skipped: decisions.Where(d => d.Tier == PortableTier.Rejected).ToList(),
            EditableCount: editable,
            StoredOnlyCount: storedOnly,
            RejectedCount: rejected,
            AcceptAll: rejected == 0,
            CanonicalBytes: package.CanonicalBytes,
            Identity: package.Identity);
    }

    // --- schema subset validation (find-first, deterministic order) ---

    private static void ValidateSchema(PortableValue value, Rule.TObj rule, string path)
    {
        if (value is not PortableValue.Obj obj)
        {
            throw TypeReject("object", value, path);
        }

        foreach (var requiredKey in rule.Required)
        {
            if (obj.Get(requiredKey) is null)
            {
                throw BuildReject(PortableRejectCategory.UnknownField, $"required: missing required key \"{requiredKey}\"", path);
            }
        }

        if (rule.Closed)
        {
            foreach (var key in obj.Entries.Select(entry => entry.Key))
            {
                if (!rule.Props.ContainsKey(key))
                {
                    throw BuildReject(
                        PortableRejectCategory.UnknownField,
                        $"additionalProperties: unknown key \"{key}\" (additionalProperties: false; " +
                            "nothing is silently ignored)",
                        Child(path, key));
                }
            }
        }

        foreach (var prop in rule.Props)
        {
            var childValue = obj.Get(prop.Key);
            if (childValue is null)
            {
                continue;
            }

            ValidateSchemaValue(childValue, prop.Value, Child(path, prop.Key));
        }
    }

    private static void ValidateSchemaValue(PortableValue value, Rule rule, string path)
    {
        switch (rule)
        {
            case Rule.TObj objectRule:
                ValidateSchema(value, objectRule, path);
                break;
            case Rule.TArr arrayRule:
            {
                if (value is not PortableValue.Arr array)
                {
                    throw TypeReject("array", value, path);
                }

                if (arrayRule.Items is not null)
                {
                    for (var index = 0; index < array.Items.Count; index++)
                    {
                        ValidateSchemaValue(array.Items[index], arrayRule.Items, $"{path}[{index}]");
                    }
                }

                if (arrayRule.MaxItems is { } maxItems && array.Items.Count > maxItems)
                {
                    throw BuildReject(
                        PortableRejectCategory.SizeLimit,
                        $"maxItems: array must have at most {maxItems} items, got {array.Items.Count}",
                        path);
                }

                break;
            }
            case Rule.TStr stringRule:
            {
                if (value is not PortableValue.Str text)
                {
                    throw TypeReject("string", value, path);
                }

                if (stringRule.Pattern is { } pattern && !pattern.IsMatch(text.Value))
                {
                    throw BuildReject(
                        PortableRejectCategory.UnknownField,
                        $"pattern: value does not match pattern {pattern}",
                        path);
                }

                if (stringRule.MinLen is { } minLen && text.Value.Length < minLen)
                {
                    throw BuildReject(
                        PortableRejectCategory.UnknownField,
                        $"minLength: string must have at least {minLen} characters, got {text.Value.Length}",
                        path);
                }

                if (stringRule.MaxLen is { } maxLen && text.Value.Length > maxLen)
                {
                    throw BuildReject(
                        PortableRejectCategory.SizeLimit,
                        $"maxLength: string must have at most {maxLen} characters, got {text.Value.Length}",
                        path);
                }

                if (stringRule.EnumSet is { } enumSet && !enumSet.Contains(text.Value))
                {
                    throw BuildReject(
                        PortableRejectCategory.UnknownField,
                        "enum: value is not one of the admitted values",
                        path);
                }

                break;
            }
            case Rule.TInt intRule:
            {
                if (value is not PortableValue.Num number || !IsIntegerLiteral(number.Literal))
                {
                    throw TypeReject("integer", value, path);
                }

                long? parsed = long.TryParse(
                    number.Literal,
                    System.Globalization.CultureInfo.InvariantCulture,
                    out var literalValue) ? literalValue : null;
                if (intRule.Min is { } min && parsed is { } value1 && value1 < min)
                {
                    throw BuildReject(PortableRejectCategory.UnknownField, $"minimum: value must be >= {min}", path);
                }

                if (intRule.EnumSet is { } enumValues && (parsed is null || !enumValues.Contains(parsed.Value)))
                {
                    throw BuildReject(
                        PortableRejectCategory.UnknownField,
                        "enum: value is not one of the admitted values",
                        path);
                }

                break;
            }
            case Rule.TBool:
                if (value is not PortableValue.Bool)
                {
                    throw TypeReject("boolean", value, path);
                }

                break;
        }
    }

    private static PortableContractException TypeReject(string expected, PortableValue value, string path) =>
        BuildReject(
            PortableRejectCategory.UnknownField,
            $"type: expected type {expected}, got {TypeName(value)}",
            path);

    // --- contract rules beyond the schema subset ---

    private static void CheckCredentials(PortableValue value, string path)
    {
        switch (value)
        {
            case PortableValue.Obj obj:
                foreach (var entry in obj.Entries)
                {
                    var childPath = Child(path, entry.Key);
                    if (IsCredentialShaped(entry.Key))
                    {
                        throw BuildReject(
                            PortableRejectCategory.UnknownField,
                            $"credential-shaped key \"{entry.Key}\" â€?credentials are never portable",
                            childPath);
                    }

                    CheckCredentials(entry.Value, childPath);
                }

                break;
            case PortableValue.Arr array:
                for (var index = 0; index < array.Items.Count; index++)
                {
                    CheckCredentials(array.Items[index], $"{path}[{index}]");
                }

                break;
        }
    }

    private static bool IsCredentialShaped(string key)
    {
        var normalized = Regex.Replace(key, @"[\s._\-]+", string.Empty).ToLowerInvariant();
        return PortableSpec.CredentialTokens.Any(normalized.Contains);
    }

    private static void CheckExtraDepth(PortableValue value, string path)
    {
        switch (value)
        {
            case PortableValue.Obj obj:
                foreach (var entry in obj.Entries)
                {
                    var childPath = Child(path, entry.Key);
                    if (entry.Key == "extra" && entry.Value is PortableValue.Obj extra)
                    {
                        foreach (var extraEntry in extra.Entries)
                        {
                            var extraPath = Child(childPath, extraEntry.Key);
                            if (extraEntry.Value is PortableValue.Obj)
                            {
                                throw BuildReject(PortableRejectCategory.UnknownField, "extra values may be scalars or arrays of scalars; the 2-level depth limit is exceeded", extraPath);
                            }

                            if (extraEntry.Value is PortableValue.Arr extraArray)
                            {
                                for (var index = 0; index < extraArray.Items.Count; index++)
                                {
                                    if (extraArray.Items[index] is PortableValue.Obj or PortableValue.Arr)
                                    {
                                        throw BuildReject(PortableRejectCategory.UnknownField, "extra values may be scalars or arrays of scalars; the 2-level depth limit is exceeded", $"{extraPath}[{index}]");
                                    }
                                }
                            }
                        }
                    }
                    else
                    {
                        CheckExtraDepth(entry.Value, childPath);
                    }
                }

                break;
            case PortableValue.Arr array:
                for (var index = 0; index < array.Items.Count; index++)
                {
                    CheckExtraDepth(array.Items[index], $"{path}[{index}]");
                }

                break;
        }
    }

    private static void CheckCollectionSizes(PortableValue.Obj root)
    {
        foreach (var collection in PortableSpec.Collections)
        {
            if (root.Get(collection) is not PortableValue.Arr array)
            {
                continue;
            }

            if (array.Items.Count > PortableSpec.MaxRecords)
            {
                throw BuildReject(
                    PortableRejectCategory.SizeLimit,
                    $"collection {collection} holds {array.Items.Count} records, limit is {PortableSpec.MaxRecords}",
                    $"$.{collection}");
            }
        }
    }

    private static void CheckStringSizes(PortableValue value, string path)
    {
        switch (value)
        {
            case PortableValue.Obj obj:
                foreach (var entry in obj.Entries)
                {
                    var childPath = Child(path, entry.Key);
                    var keySize = Encoding.UTF8.GetByteCount(entry.Key);
                    if (keySize > PortableSpec.MaxStringBytes)
                    {
                        throw BuildReject(
                            PortableRejectCategory.SizeLimit,
                            $"object key is {keySize} UTF-8 bytes, limit is {PortableSpec.MaxStringBytes}",
                            childPath);
                    }

                    CheckStringSizes(entry.Value, childPath);
                }

                break;
            case PortableValue.Arr array:
                for (var index = 0; index < array.Items.Count; index++)
                {
                    CheckStringSizes(array.Items[index], $"{path}[{index}]");
                }

                break;
            case PortableValue.Str text:
                var size = Encoding.UTF8.GetByteCount(text.Value);
                if (size > PortableSpec.MaxStringBytes)
                {
                    throw BuildReject(
                        PortableRejectCategory.SizeLimit,
                        $"string is {size} UTF-8 bytes, limit is {PortableSpec.MaxStringBytes}",
                        path);
                }

                break;
        }
    }

    private static void CheckPathShapes(PortableValue value, string path)
    {
        switch (value)
        {
            case PortableValue.Obj obj:
                foreach (var entry in obj.Entries)
                {
                    CheckPathShapes(entry.Value, Child(path, entry.Key));
                }

                break;
            case PortableValue.Arr array:
                for (var index = 0; index < array.Items.Count; index++)
                {
                    CheckPathShapes(array.Items[index], $"{path}[{index}]");
                }

                break;
            case PortableValue.Str text:
                if (PathUnsafeRegex.IsMatch(text.Value))
                {
                    throw BuildReject(
                        PortableRejectCategory.PathUnsafe,
                        "string value looks like a filesystem path",
                        path);
                }

                break;
        }
    }

    private static void CheckDuplicates(PortableValue.Obj root)
    {
        foreach (var collection in PortableSpec.Collections)
        {
            if (root.Get(collection) is not PortableValue.Arr array)
            {
                continue;
            }

            var seenStable = new Dictionary<string, int>(StringComparer.Ordinal);
            var seenProblem = new Dictionary<string, int>(StringComparer.Ordinal);
            for (var index = 0; index < array.Items.Count; index++)
            {
                if (array.Items[index] is not PortableValue.Obj record)
                {
                    continue;
                }

                if (record.Get("stableId") is PortableValue.Str stableId
                    && !seenStable.TryAdd(stableId.Value, index))
                {
                    throw BuildReject(
                        PortableRejectCategory.DuplicateId,
                        $"duplicate stableId \"{stableId.Value}\" in collection {collection}",
                        $"$.{collection}[{index}].stableId");
                }

                if (collection == "notes"
                    && record.Get("problemId") is PortableValue.Str problemId
                    && !seenProblem.TryAdd(problemId.Value, index))
                {
                    throw BuildReject(
                        PortableRejectCategory.DuplicateId,
                        $"duplicate problemId \"{problemId.Value}\" in notes[] (one note per problem per package)",
                        $"$.{collection}[{index}].problemId");
                }
            }
        }
    }

    private static void CheckReferences(PortableValue.Obj root)
    {
        var problemIds = new HashSet<string>(StringComparer.Ordinal);
        if (root.Get("problems") is PortableValue.Arr problems)
        {
            foreach (var record in problems.Items)
            {
                if (record is PortableValue.Obj obj && obj.Get("stableId") is PortableValue.Str stableId)
                {
                    problemIds.Add(stableId.Value);
                }
            }
        }

        foreach (var collection in new[] { "attempts", "notes", "reviews" })
        {
            if (root.Get(collection) is not PortableValue.Arr array)
            {
                continue;
            }

            for (var index = 0; index < array.Items.Count; index++)
            {
                if (array.Items[index] is not PortableValue.Obj record)
                {
                    continue;
                }

                if (record.Get("problemId") is not PortableValue.Str problemId)
                {
                    continue;
                }

                if (!problemIds.Contains(problemId.Value))
                {
                    throw BuildReject(
                        PortableRejectCategory.MissingReference,
                        $"{collection}[{index}] references problemId \"{problemId.Value}\" which does not exist in problems[].stableId",
                        $"$.{collection}[{index}].problemId");
                }
            }
        }

        if (root.Get("sessions") is not PortableValue.Arr sessions)
        {
            return;
        }

        for (var index = 0; index < sessions.Items.Count; index++)
        {
            if (sessions.Items[index] is not PortableValue.Obj record)
            {
                continue;
            }

            if (record.Get("problemIds") is not PortableValue.Arr references)
            {
                continue;
            }

            for (var reference = 0; reference < references.Items.Count; reference++)
            {
                if (references.Items[reference] is not PortableValue.Str problemId)
                {
                    continue;
                }

                if (!problemIds.Contains(problemId.Value))
                {
                    throw BuildReject(
                        PortableRejectCategory.MissingReference,
                        $"sessions[{index}].problemIds[{reference}] references \"{problemId.Value}\" which does not exist in problems[].stableId",
                        $"$.sessions[{index}].problemIds[{reference}]");
                }
            }
        }
    }

    private static void CheckCounts(PortableValue.Obj root)
    {
        if (root.Get("counts") is not PortableValue.Obj counts)
        {
            return;
        }

        foreach (var collection in PortableSpec.Collections)
        {
            if (counts.Get(collection) is not PortableValue.Num declared
                || !IsIntegerLiteral(declared.Literal))
            {
                continue;
            }

            long declaredValue = long.Parse(
                declared.Literal,
                System.Globalization.CultureInfo.InvariantCulture);
            var actual = root.Get(collection) is PortableValue.Arr array ? array.Items.Count : 0;
            if (declaredValue != actual)
            {
                throw BuildReject(
                    PortableRejectCategory.CountsMismatch,
                    $"counts.{collection} declares {declaredValue} but the {collection} array holds {actual} records",
                    $"$.counts.{collection}");
            }
        }
    }

    // --- ValidatedPackage assembly ---

    private static ValidatedPackage BuildValidated(PortableValue.Obj root)
    {
        var canonicalBytes = PortableJson.CanonicalBytes(root);
        var identity = Convert.ToHexString(SHA256.HashData(canonicalBytes)).ToLowerInvariant();
        var scopeId = ((PortableValue.Str)root.Get("scopeId")!).Value;
        var sourcePlatform = ((PortableValue.Str)root.Get("sourcePlatform")!).Value;
        var generatorObj = (PortableValue.Obj)root.Get("generator")!;
        var generatorName = ((PortableValue.Str)generatorObj.Get("name")!).Value;
        var generatorVersion = ((PortableValue.Str)generatorObj.Get("version")!).Value;
        var generator = $"{generatorName} {generatorVersion}";
        var generatorJson = Encoding.UTF8.GetString(PortableJson.CanonicalBytes(generatorObj)).TrimEnd('\n');
        var createdAtEpochMs = long.Parse(
            ((PortableValue.Num)root.Get("createdAtEpochMs")!).Literal,
            System.Globalization.CultureInfo.InvariantCulture);
        var countsObj = (PortableValue.Obj)root.Get("counts")!;
        var counts = new Dictionary<string, int>(StringComparer.Ordinal);
        foreach (var collection in PortableSpec.Collections)
        {
            counts[collection] = int.Parse(
                ((PortableValue.Num)countsObj.Get(collection)!).Literal,
                System.Globalization.CultureInfo.InvariantCulture);
        }

        var packageSynthetic = generatorName == "synthetic-fixture-gen";
        var records = new Dictionary<string, IReadOnlyList<ValidatedRecord>>(StringComparer.Ordinal);
        foreach (var collection in PortableSpec.Collections)
        {
            var array = (PortableValue.Arr)root.Get(collection)!;
            var list = new List<ValidatedRecord>(array.Items.Count);
            for (var index = 0; index < array.Items.Count; index++)
            {
                var record = (PortableValue.Obj)array.Items[index];
                var recordSynthetic = IsSynthetic(record);
                packageSynthetic |= recordSynthetic;
                var canonicalJson = Encoding.UTF8.GetString(PortableJson.CanonicalBytes(record)).TrimEnd('\n');
                list.Add(new ValidatedRecord(
                    Collection: collection,
                    Ordinal: index,
                    CanonicalJson: canonicalJson,
                    StableId: ((PortableValue.Str)record.Get("stableId")!).Value,
                    SourceScopeId: ((PortableValue.Str)record.Get("sourceScopeId")!).Value,
                    Origin: ((PortableValue.Str)record.Get("origin")!).Value,
                    Synthetic: recordSynthetic));
            }

            records[collection] = list;
        }

        var sourceTag = packageSynthetic ? "SYNTHETIC-TEST-FIXTURE" : "PORTABLE-V1";
        return new ValidatedPackage(
            Root: root,
            CanonicalBytes: canonicalBytes,
            Identity: identity,
            ScopeId: scopeId,
            SourcePlatform: sourcePlatform,
            Generator: generator,
            GeneratorJson: generatorJson,
            CreatedAtEpochMs: createdAtEpochMs,
            Counts: counts,
            SourceTag: sourceTag,
            Synthetic: packageSynthetic,
            Records: records);
    }

    private static bool IsSynthetic(PortableValue.Obj record)
    {
        if (record.Get("extra") is not PortableValue.Obj extra)
        {
            return false;
        }

        return extra.Get("synthetic") is PortableValue.Bool synthetic && synthetic.Value;
    }

    // --- small helpers ---

    private static PortableContractException BuildReject(PortableRejectCategory category, string detail, string location) =>
        new(new PortableReject(category, detail, location));

    private static string Child(string path, string key) => $"{path}.{key}";

    private static bool IsIntegerLiteral(string literal) =>
        Regex.IsMatch(literal, """-?(0|[1-9][0-9]*)""");

    private static string TypeName(PortableValue value) => value switch
    {
        PortableValue.Obj => "object",
        PortableValue.Arr => "array",
        PortableValue.Str => "string",
        PortableValue.Num number => IsIntegerLiteral(number.Literal) ? "integer" : "number",
        PortableValue.Bool => "boolean",
        PortableValue.Null => "null",
        _ => "unknown",
    };

    private static string JsonRepr(PortableValue value) => value switch
    {
        PortableValue.Str text => $"\"{text.Value}\"",
        PortableValue.Num number => number.Literal,
        PortableValue.Bool boolean => boolean.Value.ToString(),
        PortableValue.Null => "null",
        PortableValue.Obj => "object",
        PortableValue.Arr => "array",
        _ => "unknown",
    };
}
