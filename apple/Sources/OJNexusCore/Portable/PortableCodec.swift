import CryptoKit
import Foundation

/// One validated record in document order, with its canonical JSON text.
public struct PortableValidatedRecord: Sendable {
    public let collection: String
    public let ordinal: Int
    public let canonicalJson: String
    public let stableId: String
    public let sourceScopeId: String
    public let origin: String
    public let synthetic: Bool

    public init(
        collection: String,
        ordinal: Int,
        canonicalJson: String,
        stableId: String,
        sourceScopeId: String,
        origin: String,
        synthetic: Bool
    ) {
        self.collection = collection
        self.ordinal = ordinal
        self.canonicalJson = canonicalJson
        self.stableId = stableId
        self.sourceScopeId = sourceScopeId
        self.origin = origin
        self.synthetic = synthetic
    }
}

/// A fully validated package: manifest data plus per-collection records.
public struct PortableValidatedPackage: Sendable {
    public let root: PortableValue
    public let canonicalBytes: Data
    public let identity: String
    public let scopeId: String
    public let sourcePlatform: String
    public let generator: String
    public let generatorJson: String
    public let createdAtEpochMs: Int
    public let counts: [String: Int]
    public let sourceTag: String
    public let synthetic: Bool
    public let records: [String: [PortableValidatedRecord]]

    public init(
        root: PortableValue,
        canonicalBytes: Data,
        identity: String,
        scopeId: String,
        sourcePlatform: String,
        generator: String,
        generatorJson: String,
        createdAtEpochMs: Int,
        counts: [String: Int],
        sourceTag: String,
        synthetic: Bool,
        records: [String: [PortableValidatedRecord]]
    ) {
        self.root = root
        self.canonicalBytes = canonicalBytes
        self.identity = identity
        self.scopeId = scopeId
        self.sourcePlatform = sourcePlatform
        self.generator = generator
        self.generatorJson = generatorJson
        self.createdAtEpochMs = createdAtEpochMs
        self.counts = counts
        self.sourceTag = sourceTag
        self.synthetic = synthetic
        self.records = records
    }
}

/// Validates and classifies portable packages. Stateless; safe to call from a
/// preview (read-only) or from an apply (before any store write).
public enum PortableCodec {
    static let maxLen = 16_384 // schema maxLength counts characters, not bytes

    // MARK: frozen pattern validators (hand-coded, no regex engine)

    /// `^[A-Za-z0-9._:-]{1,128}$` — the frozen charset is ASCII, so scalars,
    /// bytes and C# UTF-16 units all agree here; hand-coded to avoid any
    /// regex-engine anchoring divergence.
    static func matchesStableId(_ value: String) -> Bool {
        matchesIdCharset(value, max: PortableSpec.stableIdMaxLength)
    }

    /// `^[A-Za-z0-9._:-]{1,64}$`
    static func matchesExternalId(_ value: String) -> Bool {
        matchesIdCharset(value, max: PortableSpec.externalIdMaxLength)
    }

    private static func matchesIdCharset(_ value: String, max: Int) -> Bool {
        var count = 0
        for scalar in value.unicodeScalars {
            switch scalar.value {
            case 0x41...0x5A, 0x61...0x7A, 0x30...0x39, 0x2E, 0x5F, 0x3A, 0x2D:
                count += 1
                if count > max { return false }
            default:
                return false
            }
        }
        return count >= 1 && count <= max
    }

    /// `^\d{4}-\d{2}-\d{2}$` after the frozen `\d` -> `[0-9]` translation.
    static func matchesDayKey(_ value: String) -> Bool {
        let scalars = Array(value.unicodeScalars)
        guard scalars.count == 10 else { return false }
        let digits = [0, 1, 3, 4, 6, 7, 8, 9]
        for position in digits where !(scalars[position].value >= 0x30 && scalars[position].value <= 0x39) {
            return false
        }
        return scalars[2] == "-" && scalars[5] == "-"
    }

    /// `^https?://` prefix check (the frozen sourceUrl schema pattern).
    static func matchesSourceUrlPrefix(_ value: String) -> Bool {
        let bytes = Array(value.utf8)
        if bytes.starts(with: Array("https://".utf8)) { return true }
        return bytes.starts(with: Array("http://".utf8))
    }

    /// PATH_UNSAFE uses the RAW pattern `(^[A-Za-z]:\\)|^/|\.\./|^\.\.$` — no
    /// `$` translation: .NET/Python `$` matches before a final line terminator,
    /// so "…\n" spellings must classify exactly like the references do.
    static func isPathUnsafe(_ value: String) -> Bool {
        let scalars = Array(value.unicodeScalars)
        // ^[A-Za-z]:\\
        if scalars.count >= 3,
           let first = scalars.first,
           (65...90).contains(first.value) || (97...122).contains(first.value),
           scalars[1] == ":",
           scalars[2] == "\\" {
            return true
        }
        // ^/
        if scalars.first == "/" { return true }
        // \.\./ contained anywhere
        if scalars.count >= 3 {
            for offset in 0...(scalars.count - 3) {
                if scalars[offset] == ".", scalars[offset + 1] == ".", scalars[offset + 2] == "/" {
                    return true
                }
            }
        }
        // ^\.\.$ — `$` also matches before one final newline, so exactly
        // ".." and "..\n" match (but not "..\n\n").
        if value == ".." || value == "..\n" { return true }
        return false
    }

    /// `\A-?(0|[1-9][0-9]*)\z` — anchored full-string classification, so
    /// "1.5", "1e5" and "01" are all non-integers (Android `matches` parity).
    static func isIntegerLiteral(_ literal: String) -> Bool {
        let bytes = Array(literal.utf8)
        var index = 0
        if index < bytes.count, bytes[index] == Byte.dash { index += 1 }
        guard index < bytes.count else { return false }
        if bytes[index] == Byte.zero {
            index += 1
            return index == bytes.count
        }
        guard (Byte.one...Byte.nine).contains(bytes[index]) else { return false }
        index += 1
        while index < bytes.count {
            guard (Byte.zero...Byte.nine).contains(bytes[index]) else { return false }
            index += 1
        }
        return true
    }

    // MARK: rule tree (the frozen draft-07 subset)

    indirect enum Rule {
        /// `required` and `props` keep declaration order so the find-first
        /// rejection is deterministic (matches the C#/Android walkers).
        case obj(required: [String], props: [(String, Rule)], closed: Bool)
        case arr(items: Rule?, maxItems: Int?)
        case str(pattern: Pattern?, minLen: Int?, maxLen: Int?, enumSet: Set<String>?)
        case int(min: Int?, enumSet: [Int]?)
        case bool
    }

    /// The frozen schema patterns, hand-coded (see the validators above).
    enum Pattern {
        case stableId
        case externalId
        case dayKey
        case sourceUrlPrefix

        func matches(_ value: String) -> Bool {
            switch self {
            case .stableId: return matchesStableId(value)
            case .externalId: return matchesExternalId(value)
            case .dayKey: return matchesDayKey(value)
            case .sourceUrlPrefix: return matchesSourceUrlPrefix(value)
            }
        }

        var display: String {
            switch self {
            case .stableId: return "^[A-Za-z0-9._:-]{1,128}$"
            case .externalId: return "^[A-Za-z0-9._:-]{1,64}$"
            case .dayKey: return "^\\d{4}-\\d{2}-\\d{2}$"
            case .sourceUrlPrefix: return "^https?://"
            }
        }
    }

    static func str(
        pattern: Pattern? = nil,
        minLen: Int? = nil,
        maxLen: Int? = nil,
        enumSet: Set<String>? = nil
    ) -> Rule {
        .str(pattern: pattern, minLen: minLen, maxLen: maxLen, enumSet: enumSet)
    }

    static func intRule(min: Int? = nil, enumSet: [Int]? = nil) -> Rule {
        .int(min: min, enumSet: enumSet)
    }

    static func obj(_ required: [String], _ props: [(String, Rule)]) -> Rule {
        .obj(required: required, props: props, closed: true)
    }

    static let generatorSchema: Rule = obj(
        ["name", "version"],
        [("name", str(minLen: 1, maxLen: maxLen)), ("version", str(minLen: 1, maxLen: maxLen))]
    )

    static let countsSchema: Rule = obj(
        PortableSpec.collections,
        PortableSpec.collections.map { ($0, intRule(min: 0)) }
    )

    static func entityBase() -> [(String, Rule)] {
        let extra: Rule = .obj(required: [], props: [], closed: false)
        return [
            ("stableId", str(pattern: .stableId, maxLen: maxLen)),
            ("sourceScopeId", str(pattern: .stableId, maxLen: maxLen)),
            ("origin", str(enumSet: PortableSpec.origins)),
            ("extra", extra),
        ]
    }

    static func entitySchemas() -> [String: Rule] {
        let plainStr = str(maxLen: maxLen)
        let epoch = intRule(min: 0)
        let stableId = str(pattern: .stableId, maxLen: maxLen)
        let dayKey = str(pattern: .dayKey, maxLen: maxLen)
        let origin = str(enumSet: PortableSpec.origins)

        let problems = obj(
            ["stableId", "sourceScopeId", "origin", "judge", "externalId", "title", "solved", "dayKey"],
            entityBase() + [
                ("judge", str(enumSet: PortableSpec.judges)),
                ("externalId", str(pattern: .externalId, maxLen: maxLen)),
                ("title", plainStr),
                ("sourceUrl", str(pattern: .sourceUrlPrefix, maxLen: maxLen)),
                ("solved", .bool),
                ("firstSolvedAtEpochMs", epoch),
                ("difficulty", intRule()), // absent stays absent; never coerced to 0
                ("tags", .arr(items: plainStr, maxItems: nil)),
                ("dayKey", dayKey),
            ]
        )
        let attempts = obj(
            ["stableId", "sourceScopeId", "origin", "problemId", "timestampEpochMs", "verdict", "dayKey"],
            entityBase() + [
                ("problemId", stableId),
                ("timestampEpochMs", epoch),
                ("dayKey", dayKey),
                ("verdict", str(enumSet: PortableSpec.verdicts)),
                ("rawVerdict", plainStr),
                ("language", plainStr),
                ("durationMin", epoch),
                ("externalSubmissionId", plainStr),
                ("note", plainStr),
            ]
        )
        let notes = obj(
            ["stableId", "sourceScopeId", "origin", "problemId", "keyInsight", "implementationNotes", "complexity", "general", "updatedEpochMs", "dayKey"],
            entityBase() + [
                ("problemId", stableId),
                ("keyInsight", plainStr),
                ("implementationNotes", plainStr),
                ("complexity", plainStr),
                ("general", plainStr),
                ("updatedEpochMs", epoch),
                ("dayKey", dayKey),
            ]
        )
        let reviews = obj(
            ["stableId", "sourceScopeId", "origin", "problemId", "stage", "dueEpochMs", "dueDayKey", "createdEpochMs", "dayKey"],
            entityBase() + [
                ("problemId", stableId),
                ("stage", intRule(min: 1)),
                ("dueEpochMs", epoch),
                ("dueDayKey", dayKey),
                ("lastResult", str(enumSet: PortableSpec.lastResults)),
                ("lastReviewedEpochMs", epoch),
                ("createdEpochMs", epoch),
                ("dayKey", dayKey),
            ]
        )
        let sessions = obj(
            ["stableId", "sourceScopeId", "origin", "type", "state", "startedEpochMs", "totalPausedMs", "dayKey"],
            entityBase() + [
                ("type", str(enumSet: PortableSpec.sessionTypes)),
                ("state", str(enumSet: PortableSpec.sessionStates)),
                ("startedEpochMs", epoch),
                ("finishedEpochMs", epoch),
                ("totalPausedMs", epoch),
                ("targetDurationMin", epoch),
                ("problemIds", .arr(items: str(pattern: .stableId, maxLen: maxLen), maxItems: nil)),
                ("dayKey", dayKey),
            ]
        )
        return [
            "problems": problems,
            "attempts": attempts,
            "notes": notes,
            "reviews": reviews,
            "sessions": sessions,
        ]
    }

    static func rootSchema() -> Rule {
        let entities = entitySchemas()
        var props = PortableSpec.collections.map {
            ($0, Rule.arr(items: entities[$0], maxItems: PortableSpec.maxRecords))
        }
        props.append(contentsOf: [
            ("formatVersion", intRule(enumSet: [1])),
            ("scopeId", str(pattern: .stableId, maxLen: maxLen)),
            ("sourcePlatform", str(enumSet: PortableSpec.platforms)),
            ("generator", generatorSchema),
            ("createdAtEpochMs", intRule(min: 0)),
            ("counts", countsSchema),
        ])
        return obj(
            ["formatVersion", "scopeId", "sourcePlatform", "generator", "createdAtEpochMs",
             "counts", "problems", "attempts", "notes", "reviews", "sessions"],
            props
        )
    }

    // MARK: validation

    /// Validate one package. Throws `PortableContractError` with exactly one
    /// category on the first failure. Performs NO writes of any kind.
    public static func validate(_ bytes: Data) throws -> PortableValidatedPackage {
        // 1. SIZE_LIMIT (raw size, before decode).
        if bytes.count > PortableSpec.maxPackageBytes {
            throw buildReject(
                .sizeLimit,
                "package is \(bytes.count) bytes, limit is \(PortableSpec.maxPackageBytes)",
                "$"
            )
        }

        // 2. INVALID_INPUT (strict decode).
        let parsed = try PortableJson.parse(bytes)
        guard case .obj(let rootEntries) = parsed else {
            throw buildReject(.invalidInput, "package root must be a JSON object, got \(typeName(parsed))", "$")
        }
        let root = PortableValue.obj(rootEntries)

        // 3. INVALID_INPUT / UNSUPPORTED_VERSION (version gate, before schema).
        guard let formatVersion = root.get("formatVersion") else {
            throw buildReject(.invalidInput, "missing required manifest key \"formatVersion\"", "$")
        }
        guard case .num(let versionLiteral) = formatVersion, isIntegerLiteral(versionLiteral) else {
            throw buildReject(
                .invalidInput,
                "formatVersion must be the integer 1, got \(jsonRepr(formatVersion))",
                "$.formatVersion"
            )
        }
        if versionLiteral != "1" {
            throw buildReject(
                .unsupportedVersion,
                "formatVersion \(versionLiteral) is not supported by this contract (expected 1); " +
                    "future majors are rejected whole-package, never best-effort decoded",
                "$.formatVersion"
            )
        }

        // 4. UNKNOWN_FIELD (draft-07 subset) + credential-shaped keys.
        try validateSchema(root, rule: rootSchema(), path: "$")
        try checkCredentials(root, path: "$")

        // 5-9. Contract rules beyond the schema, in documented precedence order.
        try checkExtraDepth(root, path: "$")
        try checkCollectionSizes(root)
        try checkStringSizes(root, path: "$")
        try checkPathShapes(root, path: "$")
        try checkDuplicates(root)
        try checkReferences(root)
        try checkCounts(root)

        return try buildValidated(root)
    }

    /// Capability tiers for a validated package. Apple has no live domain UI
    /// for any collection yet, so every record is stored-only behind the
    /// bounded archive and reported as such (roadmap deliverable). All records
    /// are archived either way — the tier is a projection promise, not a
    /// storage decision.
    public static func classify(_ package: PortableValidatedPackage) -> [PortableTierDecision] {
        var decisions = [PortableTierDecision]()
        for collection in PortableSpec.collections {
            guard let records = package.records[collection] else { continue }
            for record in records {
                decisions.append(PortableTierDecision(
                    collection: collection,
                    stableId: record.stableId,
                    tier: .storedOnly,
                    reason: .boundedArchiveNoDomainUi
                ))
            }
        }
        return decisions
    }

    /// Read-only preview: assemble counts, sources, decisions and conflicts.
    public static func preview(_ bytes: Data) throws -> PortablePreview {
        let package = try validate(bytes)
        let decisions = classify(package)
        var bySource = [String: Int]()
        for collection in PortableSpec.collections {
            for record in package.records[collection] ?? [] {
                bySource[record.sourceScopeId, default: 0] += 1
            }
        }
        let sources = bySource
            .map { PortableSourceBreakdown(sourceScopeId: $0.key, recordCount: $0.value) }
            .sorted { PortableJson.compareCodePoints($0.sourceScopeId, $1.sourceScopeId) < 0 }
        let editable = decisions.filter { $0.tier == .editable }.count
        let storedOnly = decisions.filter { $0.tier == .storedOnly }.count
        let rejected = decisions.filter { $0.tier == .rejected }.count
        return PortablePreview(
            scopeId: package.scopeId,
            sourcePlatform: package.sourcePlatform,
            generator: package.generator,
            createdAtEpochMs: package.createdAtEpochMs,
            counts: package.counts,
            sources: sources,
            decisions: decisions,
            conflicts: [],
            skipped: decisions.filter { $0.tier == .rejected },
            editableCount: editable,
            storedOnlyCount: storedOnly,
            rejectedCount: rejected,
            acceptAll: rejected == 0,
            canonicalBytes: package.canonicalBytes,
            identity: package.identity
        )
    }

    // MARK: schema subset validation (find-first, deterministic order)

    static func validateSchema(_ value: PortableValue, rule: Rule, path: String) throws {
        guard case .obj(let required, let props, let closed) = rule else { return }
        guard case .obj(let entries) = value else {
            throw typeReject("object", value, path)
        }
        let probe = PortableValue.obj(entries)

        for requiredKey in required {
            if probe.get(requiredKey) == nil {
                throw buildReject(
                    .unknownField,
                    "required: missing required key \"\(requiredKey)\"",
                    path
                )
            }
        }
        if closed {
            for entry in entries where props.first(where: { $0.0 == entry.key }) == nil {
                throw buildReject(
                    .unknownField,
                    "additionalProperties: unknown key \"\(entry.key)\" (additionalProperties: false; " +
                        "nothing is silently ignored)",
                    child(path, entry.key)
                )
            }
        }
        for (key, childRule) in props {
            guard let childValue = probe.get(key) else { continue }
            try validateSchemaValue(childValue, rule: childRule, path: child(path, key))
        }
    }

    static func validateSchemaValue(_ value: PortableValue, rule: Rule, path: String) throws {
        switch rule {
        case .obj:
            try validateSchema(value, rule: rule, path: path)
        case .arr(let items, let maxItems):
            guard case .arr(let arrayItems) = value else {
                throw typeReject("array", value, path)
            }
            if let items = items {
                for (index, item) in arrayItems.enumerated() {
                    try validateSchemaValue(item, rule: items, path: "\(path)[\(index)]")
                }
            }
            if let maxItems = maxItems, arrayItems.count > maxItems {
                throw buildReject(
                    .sizeLimit,
                    "maxItems: array must have at most \(maxItems) items, got \(arrayItems.count)",
                    path
                )
            }
        case .str(let pattern, let minLen, let maxLen, let enumSet):
            guard case .str(let text) = value else {
                throw typeReject("string", value, path)
            }
            if let pattern = pattern, !pattern.matches(text) {
                throw buildReject(
                    .unknownField,
                    "pattern: value does not match pattern \(pattern.display)",
                    path
                )
            }
            // Character count uses code points (the Python oracle's `len`),
            // not UTF-16 units.
            let charCount = text.unicodeScalars.count
            if let minLen = minLen, charCount < minLen {
                throw buildReject(
                    .unknownField,
                    "minLength: string must have at least \(minLen) characters, got \(charCount)",
                    path
                )
            }
            if let maxLen = maxLen, charCount > maxLen {
                throw buildReject(
                    .sizeLimit,
                    "maxLength: string must have at most \(maxLen) characters, got \(charCount)",
                    path
                )
            }
            if let enumSet = enumSet, !stringMatchesAny(enumSet, text) {
                throw buildReject(.unknownField, "enum: value is not one of the admitted values", path)
            }
        case .int(let min, let enumSet):
            guard case .num(let literal) = value, isIntegerLiteral(literal) else {
                throw typeReject("integer", value, path)
            }
            let parsed = Int(literal)
            if let min = min, let parsed = parsed, parsed < min {
                throw buildReject(.unknownField, "minimum: value must be >= \(min)", path)
            }
            if let enumSet = enumSet {
                guard let parsed = parsed, enumSet.contains(parsed) else {
                    throw buildReject(.unknownField, "enum: value is not one of the admitted values", path)
                }
            }
        case .bool:
            guard case .bool = value else {
                throw typeReject("boolean", value, path)
            }
        }
    }

    /// Byte-exact membership: enum sets are compared by UTF-8 bytes so a
    /// canonically-equivalent spelling never matches (Python/C# parity).
    static func stringMatchesAny(_ set: Set<String>, _ value: String) -> Bool {
        let bytes = Array(value.utf8)
        for candidate in set where Array(candidate.utf8) == bytes {
            return true
        }
        return false
    }

    static func typeReject(_ expected: String, _ value: PortableValue, _ path: String) -> PortableContractError {
        buildReject(.unknownField, "type: expected type \(expected), got \(typeName(value))", path)
    }

    // MARK: contract rules beyond the schema subset

    static func checkCredentials(_ value: PortableValue, path: String) throws {
        switch value {
        case .obj(let entries):
            for entry in entries {
                let childPath = child(path, entry.key)
                if isCredentialShaped(entry.key) {
                    throw buildReject(
                        .unknownField,
                        "credential-shaped key \"\(entry.key)\" — credentials are never portable",
                        childPath
                    )
                }
                try checkCredentials(entry.value, path: childPath)
            }
        case .arr(let items):
            for (index, item) in items.enumerated() {
                try checkCredentials(item, path: "\(path)[\(index)]")
            }
        default:
            break
        }
    }

    static func isCredentialShaped(_ key: String) -> Bool {
        var normalized = [UInt8]()
        for scalar in key.unicodeScalars {
            switch scalar.value {
            case 0x20, 0x09, 0x0A, 0x0D, 0x2E, 0x5F, 0x2D:
                continue
            default:
                PortableJson.appendScalar(simpleLower(scalar.value), to: &normalized)
            }
        }
        for candidate in PortableSpec.credentialKeys {
            if containsBytes(normalized, Array(candidate.utf8)) {
                return true
            }
        }
        return false
    }

    /// Unicode simple lowercase (Python `str.lower()` parity for the ranges
    /// that matter to key normalization).
    static func simpleLower(_ value: UInt32) -> UInt32 {
        if (65...90).contains(value) { return value + 32 }
        return value
    }

    static func containsBytes(_ haystack: [UInt8], _ needle: [UInt8]) -> Bool {
        guard !needle.isEmpty, haystack.count >= needle.count else { return false }
        for start in 0...(haystack.count - needle.count) {
            if haystack[start..<(start + needle.count)].elementsEqual(needle) {
                return true
            }
        }
        return false
    }

    static func checkExtraDepth(_ value: PortableValue, path: String) throws {
        switch value {
        case .obj(let entries):
            for entry in entries {
                let childPath = child(path, entry.key)
                if entry.key == "extra", case .obj(let extra) = entry.value {
                    for extraEntry in extra {
                        let extraPath = child(childPath, extraEntry.key)
                        if case .obj = extraEntry.value {
                            throw buildReject(
                                .unknownField,
                                "extra values may be scalars or arrays of scalars; the 2-level depth limit is exceeded",
                                extraPath
                            )
                        }
                        if case .arr(let extraArray) = extraEntry.value {
                            for (index, item) in extraArray.enumerated() {
                                if case .obj = item {
                                    throw buildReject(
                                        .unknownField,
                                        "extra values may be scalars or arrays of scalars; the 2-level depth limit is exceeded",
                                        "\(extraPath)[\(index)]"
                                    )
                                }
                                if case .arr = item {
                                    throw buildReject(
                                        .unknownField,
                                        "extra values may be scalars or arrays of scalars; the 2-level depth limit is exceeded",
                                        "\(extraPath)[\(index)]"
                                    )
                                }
                            }
                        }
                    }
                } else {
                    try checkExtraDepth(entry.value, path: childPath)
                }
            }
        case .arr(let items):
            for (index, item) in items.enumerated() {
                try checkExtraDepth(item, path: "\(path)[\(index)]")
            }
        default:
            break
        }
    }

    static func checkCollectionSizes(_ root: PortableValue) throws {
        for collection in PortableSpec.collections {
            guard case .arr(let items) = root.get(collection) ?? .null else { continue }
            if items.count > PortableSpec.maxRecords {
                throw buildReject(
                    .sizeLimit,
                    "collection \(collection) holds \(items.count) records, limit is \(PortableSpec.maxRecords)",
                    "$.\(collection)"
                )
            }
        }
    }

    static func checkStringSizes(_ value: PortableValue, path: String) throws {
        switch value {
        case .obj(let entries):
            for entry in entries {
                let childPath = child(path, entry.key)
                let keySize = entry.key.utf8.count
                if keySize > PortableSpec.maxStringBytes {
                    throw buildReject(
                        .sizeLimit,
                        "object key is \(keySize) UTF-8 bytes, limit is \(PortableSpec.maxStringBytes)",
                        childPath
                    )
                }
                try checkStringSizes(entry.value, path: childPath)
            }
        case .arr(let items):
            for (index, item) in items.enumerated() {
                try checkStringSizes(item, path: "\(path)[\(index)]")
            }
        case .str(let text):
            let size = text.utf8.count
            if size > PortableSpec.maxStringBytes {
                throw buildReject(
                    .sizeLimit,
                    "string is \(size) UTF-8 bytes, limit is \(PortableSpec.maxStringBytes)",
                    path
                )
            }
        default:
            break
        }
    }

    static func checkPathShapes(_ value: PortableValue, path: String) throws {
        switch value {
        case .obj(let entries):
            for entry in entries {
                try checkPathShapes(entry.value, path: child(path, entry.key))
            }
        case .arr(let items):
            for (index, item) in items.enumerated() {
                try checkPathShapes(item, path: "\(path)[\(index)]")
            }
        case .str(let text):
            if isPathUnsafe(text) {
                throw buildReject(.pathUnsafe, "string value looks like a filesystem path", path)
            }
        default:
            break
        }
    }

    static func checkDuplicates(_ root: PortableValue) throws {
        for collection in PortableSpec.collections {
            guard case .arr(let items) = root.get(collection) ?? .null else { continue }
            var seenStable = Set<[UInt8]>()
            var seenProblem = Set<[UInt8]>()
            for (index, item) in items.enumerated() {
                guard case .obj(let entries) = item else { continue }
                let record = PortableValue.obj(entries)
                if case .str(let stableId) = record.get("stableId"),
                   !seenStable.insert(Array(stableId.utf8)).inserted {
                    throw buildReject(
                        .duplicateId,
                        "duplicate stableId \"\(stableId)\" in collection \(collection)",
                        "$.\(collection)[\(index)].stableId"
                    )
                }
                if collection == "notes",
                   case .str(let problemId) = record.get("problemId"),
                   !seenProblem.insert(Array(problemId.utf8)).inserted {
                    throw buildReject(
                        .duplicateId,
                        "duplicate problemId \"\(problemId)\" in notes[] (one note per problem per package)",
                        "$.\(collection)[\(index)].problemId"
                    )
                }
            }
        }
    }

    static func checkReferences(_ root: PortableValue) throws {
        var problemIds = Set<[UInt8]>()
        if case .arr(let problems) = root.get("problems") ?? .null {
            for record in problems {
                if case .obj(let entries) = record {
                    let probe = PortableValue.obj(entries)
                    if case .str(let stableId) = probe.get("stableId") {
                        problemIds.insert(Array(stableId.utf8))
                    }
                }
            }
        }

        for collection in ["attempts", "notes", "reviews"] {
            guard case .arr(let items) = root.get(collection) ?? .null else { continue }
            for (index, item) in items.enumerated() {
                guard case .obj(let entries) = item else { continue }
                let probe = PortableValue.obj(entries)
                guard case .str(let problemId) = probe.get("problemId") else { continue }
                if !problemIds.contains(Array(problemId.utf8)) {
                    throw buildReject(
                        .missingReference,
                        "\(collection)[\(index)] references problemId \"\(problemId)\" which does not exist in problems[].stableId",
                        "$.\(collection)[\(index)].problemId"
                    )
                }
            }
        }

        guard case .arr(let sessions) = root.get("sessions") ?? .null else { return }
        for (index, item) in sessions.enumerated() {
            guard case .obj(let entries) = item else { continue }
            let probe = PortableValue.obj(entries)
            guard case .arr(let references) = probe.get("problemIds") ?? .null else { continue }
            for (reference, item2) in references.enumerated() {
                guard case .str(let problemId) = item2 else { continue }
                if !problemIds.contains(Array(problemId.utf8)) {
                    throw buildReject(
                        .missingReference,
                        "sessions[\(index)].problemIds[\(reference)] references \"\(problemId)\" which does not exist in problems[].stableId",
                        "$.sessions[\(index)].problemIds[\(reference)]"
                    )
                }
            }
        }
    }

    static func checkCounts(_ root: PortableValue) throws {
        guard case .obj(let countEntries) = root.get("counts") ?? .null else { return }
        let counts = PortableValue.obj(countEntries)
        for collection in PortableSpec.collections {
            guard case .num(let declared) = counts.get(collection) ?? .null,
                  isIntegerLiteral(declared) else { continue }
            // Overflow of the platform integer is rejected INVALID_INPUT here
            // (recorded deviation: the C#/Android references crash on the same
            // input; Swift refuses to crash on hostile manifests).
            guard let declaredValue = Int(declared) else {
                throw buildReject(
                    .invalidInput,
                    "counts.\(collection) value does not fit the platform integer",
                    "$.counts.\(collection)"
                )
            }
            let actual: Int
            if case .arr(let items) = root.get(collection) ?? .null {
                actual = items.count
            } else {
                actual = 0
            }
            if declaredValue != actual {
                throw buildReject(
                    .countsMismatch,
                    "counts.\(collection) declares \(declaredValue) but the \(collection) array holds \(actual) records",
                    "$.counts.\(collection)"
                )
            }
        }
    }

    // MARK: validated package assembly

    static func buildValidated(_ root: PortableValue) throws -> PortableValidatedPackage {
        let canonicalBytes = PortableJson.canonicalBytes(root)
        let identity = sha256Hex(canonicalBytes)
        guard case .str(let scopeId) = root.get("scopeId"),
              case .str(let sourcePlatform) = root.get("sourcePlatform"),
              case .obj(let generatorEntries) = root.get("generator"),
              case .num(let createdAtLiteral) = root.get("createdAtEpochMs"),
              case .obj(let countEntries) = root.get("counts") else {
            throw buildReject(.invalidInput, "manifest keys missing after schema validation", "$")
        }
        let generatorObj = PortableValue.obj(generatorEntries)
        guard case .str(let generatorName) = generatorObj.get("name"),
              case .str(let generatorVersion) = generatorObj.get("version") else {
            throw buildReject(.invalidInput, "generator keys missing after schema validation", "$.generator")
        }
        let generator = "\(generatorName) \(generatorVersion)"
        let generatorJson = String(
            decoding: PortableJson.canonicalBytes(generatorObj).dropLast(1),
            as: UTF8.self
        )
        guard let createdAtEpochMs = Int(createdAtLiteral) else {
            throw buildReject(.invalidInput, "createdAtEpochMs does not fit the platform integer", "$.createdAtEpochMs")
        }
        let countsObj = PortableValue.obj(countEntries)
        var counts = [String: Int]()
        for collection in PortableSpec.collections {
            guard case .num(let literal) = countsObj.get(collection) ?? .null,
                  let parsed = Int(literal) else {
                throw buildReject(.invalidInput, "counts.\(collection) unreadable", "$.counts.\(collection)")
            }
            counts[collection] = parsed
        }

        var packageSynthetic = generatorName == "synthetic-fixture-gen"
        var records = [String: [PortableValidatedRecord]]()
        for collection in PortableSpec.collections {
            guard case .arr(let items) = root.get(collection) ?? .null else { continue }
            var list = [PortableValidatedRecord]()
            list.reserveCapacity(items.count)
            for (index, item) in items.enumerated() {
                guard case .obj(let entries) = item else { continue }
                let record = PortableValue.obj(entries)
                let recordSynthetic = isSynthetic(record)
                packageSynthetic = packageSynthetic || recordSynthetic
                let canonicalJson = String(
                    decoding: PortableJson.canonicalBytes(record).dropLast(1),
                    as: UTF8.self
                )
                guard case .str(let stableId) = record.get("stableId"),
                      case .str(let sourceScopeId) = record.get("sourceScopeId"),
                      case .str(let origin) = record.get("origin") else {
                    throw buildReject(.invalidInput, "entity base keys missing after schema validation", "$.\(collection)[\(index)]")
                }
                list.append(PortableValidatedRecord(
                    collection: collection,
                    ordinal: index,
                    canonicalJson: canonicalJson,
                    stableId: stableId,
                    sourceScopeId: sourceScopeId,
                    origin: origin,
                    synthetic: recordSynthetic
                ))
            }
            records[collection] = list
        }

        let sourceTag = packageSynthetic ? "SYNTHETIC-TEST-FIXTURE" : "PORTABLE-V1"
        return PortableValidatedPackage(
            root: root,
            canonicalBytes: canonicalBytes,
            identity: identity,
            scopeId: scopeId,
            sourcePlatform: sourcePlatform,
            generator: generator,
            generatorJson: generatorJson,
            createdAtEpochMs: createdAtEpochMs,
            counts: counts,
            sourceTag: sourceTag,
            synthetic: packageSynthetic,
            records: records
        )
    }

    static func isSynthetic(_ record: PortableValue) -> Bool {
        guard case .obj(let extraEntries) = record.get("extra") ?? .null else { return false }
        let extra = PortableValue.obj(extraEntries)
        guard case .bool(let synthetic) = extra.get("synthetic") ?? .null else { return false }
        return synthetic
    }

    // MARK: helpers

    static func sha256Hex(_ data: Data) -> String {
        SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
    }

    static func buildReject(_ category: PortableRejectCategory, _ detail: String, _ location: String) -> PortableContractError {
        PortableContractError(PortableReject(category: category, detail: detail, location: location))
    }

    static func child(_ path: String, _ key: String) -> String {
        "\(path).\(key)"
    }

    static func typeName(_ value: PortableValue) -> String {
        switch value {
        case .obj: return "object"
        case .arr: return "array"
        case .str: return "string"
        case .num(let literal): return isIntegerLiteral(literal) ? "integer" : "number"
        case .bool: return "boolean"
        case .null: return "null"
        }
    }

    static func jsonRepr(_ value: PortableValue) -> String {
        String(decoding: PortableJson.canonicalBytes(value).dropLast(1), as: UTF8.self)
    }
}
