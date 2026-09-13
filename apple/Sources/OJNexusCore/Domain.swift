import Foundation

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

public struct JudgeAccount: Codable, Equatable, Identifiable, Sendable {
    public let judge: JudgeID
    public let handle: String
    public var enabled: Bool

    public var id: String { "\(judge.rawValue):\(handle)" }

    public init?(judge: JudgeID, handle rawHandle: String, enabled: Bool = true) {
        let normalized = rawHandle.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !normalized.isEmpty else { return nil }
        if judge == .atcoder {
            let isSafe = normalized.unicodeScalars.allSatisfy {
                ($0.value >= 48 && $0.value <= 57) ||
                ($0.value >= 65 && $0.value <= 90) ||
                ($0.value >= 97 && $0.value <= 122) ||
                $0.value == 45 || $0.value == 95
            }
            guard isSafe else { return nil }
        }
        if judge == .luogu {
            let uid = normalized.split(separator: ":", maxSplits: 1).last
            guard let uid, !uid.isEmpty, uid.allSatisfy({ $0.wholeNumberValue != nil }) else { return nil }
        }
        self.judge = judge
        self.handle = normalized
        self.enabled = enabled
    }
}

public enum SyncStatus: String, Codable, Sendable {
    case running = "RUNNING"
    case success = "SUCCESS"
    case partial = "PARTIAL"
    case offline = "OFFLINE"
    case error = "ERROR"
    case cancelled = "CANCELLED"
}

public enum SyncError: String, Codable, Sendable {
    case cancelled = "CANCELLED"
    case offline = "OFFLINE"
    case network = "NETWORK"
    case api = "API"
    case parse = "PARSE"
    case storage = "STORAGE"
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
