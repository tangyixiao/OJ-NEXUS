import Foundation

private func isSafeCodeforcesHandle(_ handle: String) -> Bool {
    handle.unicodeScalars.allSatisfy {
        ($0.value >= 48 && $0.value <= 57) ||
        ($0.value >= 65 && $0.value <= 90) ||
        ($0.value >= 97 && $0.value <= 122) ||
        $0.value == 45 || $0.value == 46 || $0.value == 95
    }
}

private func isSafeAtCoderHandle(_ handle: String) -> Bool {
    handle.unicodeScalars.allSatisfy {
        ($0.value >= 48 && $0.value <= 57) ||
        ($0.value >= 65 && $0.value <= 90) ||
        ($0.value >= 97 && $0.value <= 122) ||
        $0.value == 45 || $0.value == 95
    }
}

public enum JudgeID: String, CaseIterable, Codable, Hashable, Identifiable, Sendable {
    case codeforces
    case atcoder
    case luogu

    public var id: String { rawValue }

    public var displayName: String { rawValue.uppercased() }

    public static func parse(_ rawValue: String) -> Self? {
        let normalized = rawValue.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        switch normalized {
        case "codeforces", "cf": return .codeforces
        case "atcoder", "ac": return .atcoder
        case "luogu", "lg": return .luogu
        default: return nil
        }
    }
}

public func judgeHandlesMatch(_ judge: JudgeID, _ lhs: String, _ rhs: String) -> Bool {
    let normalized: (String) -> String = { value in
        value.trimmingCharacters(in: .whitespacesAndNewlines)
    }
    switch judge {
    case .codeforces:
        return normalized(lhs).caseInsensitiveCompare(normalized(rhs)) == .orderedSame
    case .atcoder:
        return normalized(lhs) == normalized(rhs)
    case .luogu:
        let normalizeUID: (String) -> String = { value in
            let trimmed = normalized(value)
            return trimmed.lowercased().hasPrefix("uid:")
                ? String(trimmed.dropFirst(4))
                : trimmed
        }
        return normalizeUID(lhs) == normalizeUID(rhs)
    }
}

public struct JudgeAccount: Codable, Equatable, Identifiable, Sendable {
    public let judge: JudgeID
    public let handle: String
    public var enabled: Bool

    public var id: String { "\(judge.rawValue):\(handle)" }

    public init?(judge: JudgeID, handle rawHandle: String, enabled: Bool = true) {
        let normalized = rawHandle.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !normalized.isEmpty else { return nil }
        if judge == .codeforces {
            guard isSafeCodeforcesHandle(normalized) else { return nil }
        }
        if judge == .atcoder {
            guard isSafeAtCoderHandle(normalized) else { return nil }
        }
        if judge == .luogu {
            let uid = normalized.lowercased().hasPrefix("uid:")
                ? normalized.dropFirst(4)
                : Substring(normalized)
            guard !uid.isEmpty, uid.allSatisfy({ $0.wholeNumberValue != nil }) else { return nil }
        }
        self.judge = judge
        self.handle = normalized
        self.enabled = enabled
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let judge = try container.decode(JudgeID.self, forKey: .judge)
        let handle = try container.decode(String.self, forKey: .handle)
        let enabled = try container.decodeIfPresent(Bool.self, forKey: .enabled) ?? true
        guard let account = Self(judge: judge, handle: handle, enabled: enabled) else {
            throw DecodingError.dataCorruptedError(
                forKey: .handle,
                in: container,
                debugDescription: "INVALID PUBLIC HANDLE")
        }
        self = account
    }

    private enum CodingKeys: String, CodingKey {
        case judge
        case handle
        case enabled
    }
}

public struct RatingChange: Codable, Equatable, Identifiable, Sendable {
    public let id: String
    public let judge: JudgeID
    public let handle: String
    public let contestID: String
    public let contestName: String?
    public let oldRating: Int?
    public let newRating: Int
    public let rank: Int?
    public let occurredAt: Date?

    public init?(id: String? = nil, judge: JudgeID, handle: String, contestID: String,
                contestName: String? = nil, oldRating: Int? = nil, newRating: Int,
                rank: Int? = nil, occurredAt: Date? = nil) {
        guard let account = JudgeAccount(judge: judge, handle: handle) else { return nil }
        let normalizedContestID = contestID.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !normalizedContestID.isEmpty else { return nil }
        self.judge = account.judge
        self.handle = account.handle
        self.contestID = normalizedContestID
        let normalizedName = contestName?.trimmingCharacters(in: .whitespacesAndNewlines)
        self.contestName = normalizedName?.isEmpty == true ? nil : normalizedName
        self.oldRating = oldRating
        self.newRating = newRating
        self.rank = rank
        self.occurredAt = occurredAt
        if let id, !id.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            self.id = id
        } else {
            let timeKey = occurredAt.map { String(Int($0.timeIntervalSince1970)) } ?? "unknown"
            self.id = "\(account.judge.rawValue):\(account.handle):\(normalizedContestID):\(timeKey)"
        }
    }

    private enum CodingKeys: String, CodingKey {
        case id
        case judge
        case handle
        case contestID
        case contestName
        case oldRating
        case newRating
        case rank
        case occurredAt
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let judge = try container.decode(JudgeID.self, forKey: .judge)
        let handle = try container.decode(String.self, forKey: .handle)
        let contestID = try container.decode(String.self, forKey: .contestID)
        let newRating = try container.decode(Int.self, forKey: .newRating)
        guard let change = Self(
            id: try container.decodeIfPresent(String.self, forKey: .id),
            judge: judge,
            handle: handle,
            contestID: contestID,
            contestName: try container.decodeIfPresent(String.self, forKey: .contestName),
            oldRating: try container.decodeIfPresent(Int.self, forKey: .oldRating),
            newRating: newRating,
            rank: try container.decodeIfPresent(Int.self, forKey: .rank),
            occurredAt: try container.decodeIfPresent(Date.self, forKey: .occurredAt)) else {
            throw DecodingError.dataCorruptedError(
                forKey: .handle,
                in: container,
                debugDescription: "INVALID RATING IDENTITY")
        }
        self = change
    }
}

public struct SubmissionRecord: Codable, Equatable, Identifiable, Sendable {
    public let id: String
    public let judge: JudgeID
    public let handle: String
    public let externalID: String
    public let verdict: String
    public let problemID: String?
    public let problemName: String?
    public let language: String?
    public let submittedAt: Date?

    public init?(id: String? = nil, judge: JudgeID, handle: String, externalID: String,
                verdict: String, problemID: String? = nil, problemName: String? = nil,
                language: String? = nil, submittedAt: Date? = nil) {
        guard let account = JudgeAccount(judge: judge, handle: handle) else { return nil }
        let normalizedExternalID = externalID.trimmingCharacters(in: .whitespacesAndNewlines)
        let normalizedVerdict = verdict.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        guard !normalizedExternalID.isEmpty, !normalizedVerdict.isEmpty else { return nil }
        self.judge = account.judge
        self.handle = account.handle
        self.externalID = normalizedExternalID
        self.verdict = normalizedVerdict
        let normalizedProblemID = problemID?.trimmingCharacters(in: .whitespacesAndNewlines)
        self.problemID = normalizedProblemID?.isEmpty == true ? nil : normalizedProblemID
        let normalizedProblemName = problemName?.trimmingCharacters(in: .whitespacesAndNewlines)
        self.problemName = normalizedProblemName?.isEmpty == true ? nil : normalizedProblemName
        let normalizedLanguage = language?.trimmingCharacters(in: .whitespacesAndNewlines)
        self.language = normalizedLanguage?.isEmpty == true ? nil : normalizedLanguage
        self.submittedAt = submittedAt
        if let id, !id.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            self.id = id
        } else {
            self.id = "\(account.judge.rawValue):\(account.handle):\(normalizedExternalID)"
        }
    }

    private enum CodingKeys: String, CodingKey {
        case id
        case judge
        case handle
        case externalID
        case verdict
        case problemID
        case problemName
        case language
        case submittedAt
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let judge = try container.decode(JudgeID.self, forKey: .judge)
        let handle = try container.decode(String.self, forKey: .handle)
        let persistedID = try container.decodeIfPresent(String.self, forKey: .id)
        let externalID: String
        if let decoded = try container.decodeIfPresent(String.self, forKey: .externalID) {
            externalID = decoded
        } else {
            externalID = try container.decode(String.self, forKey: .id)
        }
        let verdict = try container.decode(String.self, forKey: .verdict)
        guard let submission = Self(
            id: persistedID,
            judge: judge,
            handle: handle,
            externalID: externalID,
            verdict: verdict,
            problemID: try container.decodeIfPresent(String.self, forKey: .problemID),
            problemName: try container.decodeIfPresent(String.self, forKey: .problemName),
            language: try container.decodeIfPresent(String.self, forKey: .language),
            submittedAt: try container.decodeIfPresent(Date.self, forKey: .submittedAt)) else {
            throw DecodingError.dataCorruptedError(
                forKey: .handle,
                in: container,
                debugDescription: "INVALID SUBMISSION IDENTITY")
        }
        self = submission
    }
}

public enum SyncStatus: String, Codable, Equatable, Sendable {
    case running = "RUNNING"
    case success = "SUCCESS"
    case partial = "PARTIAL"
    case offline = "OFFLINE"
    case error = "ERROR"
    case cancelled = "CANCELLED"
}

public enum SyncError: String, Codable, Equatable, Sendable {
    case authentication = "AUTHENTICATION"
    case cancelled = "CANCELLED"
    case offline = "OFFLINE"
    case network = "NETWORK"
    case api = "API"
    case parse = "PARSE"
    case storage = "STORAGE"
    case unsupported = "UNSUPPORTED"
}

public struct SyncModuleOutcome: Codable, Equatable, Sendable {
    public let stage: String
    public let status: SyncStatus
    public let attempted: Int
    public let imported: Int
    public let updated: Int
    public let failure: SyncError?

    public init(stage: String, status: SyncStatus, attempted: Int = 0, imported: Int = 0,
                updated: Int = 0, failure: SyncError? = nil) {
        self.stage = stage
        self.status = status
        self.attempted = attempted
        self.imported = imported
        self.updated = updated
        self.failure = failure
    }
}

public struct SyncOperation: Codable, Equatable, Identifiable, Sendable {
    public let id: UUID
    public let account: JudgeAccount
    public let generation: String
    public let startedAt: Date
    public var finishedAt: Date?
    public var status: SyncStatus
    public var error: SyncError?
    public var modules: [SyncModuleOutcome]

    public var moduleSummary: String {
        guard !modules.isEmpty else { return "NO MODULES" }
        return modules.map { module in
            let summary = "\(module.stage):\(module.status.rawValue)"
            guard let failure = module.failure else { return summary }
            return "\(summary) (\(failure.rawValue))"
        }.joined(separator: " / ")
    }

    public init(id: UUID = UUID(), account: JudgeAccount, generation: String, startedAt: Date,
                finishedAt: Date? = nil, status: SyncStatus = .running, error: SyncError? = nil,
                modules: [SyncModuleOutcome] = []) {
        self.id = id
        self.account = account
        self.generation = generation
        self.startedAt = startedAt
        self.finishedAt = finishedAt
        self.status = status
        self.error = error
        self.modules = modules
    }
}

public struct SyncLedger: Codable, Equatable, Sendable {
    public static let retentionLimit = 40
    public private(set) var operations: [SyncOperation]

    public init(operations: [SyncOperation] = []) {
        self.operations = operations
    }

    @discardableResult
    public mutating func open(account: JudgeAccount, generation: String, at date: Date) -> UUID {
        let operation = SyncOperation(account: account, generation: generation, startedAt: date)
        operations.insert(operation, at: 0)
        return operation.id
    }

    public mutating func close(id: UUID, status: SyncStatus, error: SyncError? = nil,
                               modules: [SyncModuleOutcome] = [], at date: Date) {
        guard let index = operations.firstIndex(where: { $0.id == id }) else { return }
        operations[index].status = status
        operations[index].error = error
        operations[index].finishedAt = date
        operations[index].modules = modules
        pruneCompletedHistory()
    }

    public mutating func recoverInterrupted(at date: Date) {
        for index in operations.indices where operations[index].status == .running {
            operations[index].status = .cancelled
            operations[index].error = .cancelled
            operations[index].finishedAt = date
        }
        pruneCompletedHistory()
    }

    public func operation(id: UUID) -> SyncOperation? {
        operations.first(where: { $0.id == id })
    }

    private mutating func pruneCompletedHistory() {
        while operations.count > Self.retentionLimit {
            guard let index = operations.lastIndex(where: { $0.status != .running }) else { return }
            operations.remove(at: index)
        }
    }
}
