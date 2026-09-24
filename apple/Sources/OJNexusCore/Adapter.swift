import Foundation

#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

public enum AdapterError: Error, Equatable, Sendable {
    case apiFailure
    case authentication
    case invalidPayload
    case offline
    case network
    case unsupported
}

public struct PublicProfile: Codable, Equatable, Sendable {
    public let judge: JudgeID
    public let handle: String
    public let displayName: String?
    public let rating: Int?
    public let rank: String?
    public let fetchedAt: Date?

    public init(judge: JudgeID, handle: String, rating: Int? = nil, rank: String? = nil,
                displayName: String? = nil, fetchedAt: Date? = nil) {
        self.judge = judge
        self.handle = handle
        self.displayName = displayName
        self.rating = rating
        self.rank = rank
        self.fetchedAt = fetchedAt
    }

    public func withFetchedAt(_ date: Date) -> Self {
        Self(judge: judge, handle: handle, rating: rating, rank: rank,
             displayName: displayName, fetchedAt: date)
    }

    private enum CodingKeys: String, CodingKey {
        case judge
        case handle
        case displayName
        case rating
        case rank
        case fetchedAt
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let decodedJudge = try container.decode(JudgeID.self, forKey: .judge)
        let decodedHandle = try container.decode(String.self, forKey: .handle)
        guard let account = JudgeAccount(judge: decodedJudge, handle: decodedHandle) else {
            throw DecodingError.dataCorruptedError(
                forKey: .handle,
                in: container,
                debugDescription: "INVALID PUBLIC HANDLE")
        }
        judge = account.judge
        handle = account.handle
        displayName = try container.decodeIfPresent(String.self, forKey: .displayName)
        rating = try container.decodeIfPresent(Int.self, forKey: .rating)
        rank = try container.decodeIfPresent(String.self, forKey: .rank)
        fetchedAt = try container.decodeIfPresent(Date.self, forKey: .fetchedAt)
    }
}

public protocol JudgeAdapter: Sendable {
    var judge: JudgeID { get }
    var reliability: String { get }
    var capabilities: [String] { get }

    func profileURL(for handle: String) throws -> URL
    func decodeProfile(_ data: Data, requestedHandle: String) throws -> PublicProfile
    func ratingURL(for handle: String) throws -> URL
    func decodeRating(_ data: Data, requestedHandle: String) throws -> [RatingChange]
    func submissionsURL(for handle: String) throws -> URL
    func decodeSubmissions(_ data: Data, requestedHandle: String) throws -> [SubmissionRecord]
}

public extension JudgeAdapter {
    func supports(_ capability: String) -> Bool {
        capabilities.contains(capability)
    }

    func ratingURL(for handle: String) throws -> URL {
        throw AdapterError.unsupported
    }

    func decodeRating(_ data: Data, requestedHandle: String) throws -> [RatingChange] {
        throw AdapterError.unsupported
    }

    func submissionsURL(for handle: String) throws -> URL {
        throw AdapterError.unsupported
    }

    func decodeSubmissions(_ data: Data, requestedHandle: String) throws -> [SubmissionRecord] {
        throw AdapterError.unsupported
    }
}

public struct JudgeDescriptor: Identifiable, Equatable, Sendable {
    public let judge: JudgeID
    public let reliability: String
    public let capabilities: [String]

    public var id: JudgeID { judge }

    public var summary: String {
        ([reliability] + capabilities).joined(separator: " / ")
    }

    public init(judge: JudgeID, reliability: String, capabilities: [String]) {
        self.judge = judge
        self.reliability = reliability
        self.capabilities = capabilities
    }
}

public enum JudgeCatalog {
    public static let descriptors: [JudgeDescriptor] = [
        JudgeDescriptor(judge: .codeforces, reliability: CodeforcesAdapter().reliability,
                        capabilities: CodeforcesAdapter().capabilities),
        JudgeDescriptor(judge: .atcoder, reliability: AtCoderAdapter().reliability,
                        capabilities: AtCoderAdapter().capabilities),
        JudgeDescriptor(judge: .luogu, reliability: LuoguAdapter().reliability,
                        capabilities: LuoguAdapter().capabilities),
    ]
}

public protocol HTTPClient: Sendable {
    func get(_ url: URL) async throws -> Data
}

public struct URLSessionHTTPClient: HTTPClient, Sendable {
    private let session: URLSession

    public static let defaultTimeoutInterval: TimeInterval = 30

    public static func defaultSessionConfiguration() -> URLSessionConfiguration {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = defaultTimeoutInterval
        configuration.timeoutIntervalForResource = defaultTimeoutInterval
        configuration.httpShouldSetCookies = false
        configuration.httpCookieStorage = nil
        return configuration
    }

    public init(session: URLSession? = nil) {
        self.session = session ?? URLSession(configuration: Self.defaultSessionConfiguration())
    }

    public func get(_ url: URL) async throws -> Data {
        do {
            var request = URLRequest(url: url)
            request.httpShouldHandleCookies = false
            request.setValue("application/json, text/html;q=0.9", forHTTPHeaderField: "Accept")
            request.setValue("OJ-NEXUS/0.1", forHTTPHeaderField: "User-Agent")
            let (data, response) = try await session.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse else {
                throw AdapterError.apiFailure
            }
            if httpResponse.statusCode == 401 || httpResponse.statusCode == 403 {
                throw AdapterError.authentication
            }
            guard (200..<300).contains(httpResponse.statusCode) else {
                throw AdapterError.apiFailure
            }
            return data
        } catch let error as AdapterError {
            throw error
        } catch let error as URLError {
            switch error.code {
            case .cancelled:
                throw CancellationError()
            case .notConnectedToInternet, .networkConnectionLost:
                throw AdapterError.offline
            default:
                throw AdapterError.network
            }
        } catch {
            throw AdapterError.network
        }
    }
}

public struct ProfileSyncService: Sendable {
    public init() {}

    public func syncProfile(for account: JudgeAccount, adapter: any JudgeAdapter,
                            client: any HTTPClient, ledger: SyncLedger, generation: String,
                            startedAt: Date, finishedAt: Date? = nil) async -> ProfileSyncResult {
        var updatedLedger = ledger
        let operationID = updatedLedger.open(account: account, generation: generation, at: startedAt)
        do {
            try Task.checkCancellation()
            let profile = try await fetchProfile(for: account, adapter: adapter, client: client)
            let completionTime = finishedAt ?? Date()
            let completedProfile = profile.withFetchedAt(completionTime)
            try Task.checkCancellation()
            let module = SyncModuleOutcome(stage: "PROFILE", status: .success, attempted: 1, imported: 1)
            updatedLedger.close(id: operationID, status: .success, modules: [module], at: completionTime)
            return ProfileSyncResult(profile: completedProfile,
                                     operation: updatedLedger.operation(id: operationID)!,
                                     ledger: updatedLedger)
        } catch is CancellationError {
            let completionTime = finishedAt ?? Date()
            return failureResult(operationID: operationID, ledger: &updatedLedger,
                                 error: .cancelled, status: .cancelled, at: completionTime)
        } catch let error as AdapterError {
            let completionTime = finishedAt ?? Date()
            let syncError: SyncError
            switch error {
            case .offline: syncError = .offline
            case .network: syncError = .network
            case .authentication: syncError = .authentication
            case .apiFailure: syncError = .api
            case .unsupported: syncError = .unsupported
            case .invalidPayload: syncError = .parse
            }
            let status: SyncStatus = syncError == .offline ? .offline : .error
            return failureResult(operationID: operationID, ledger: &updatedLedger,
                                 error: syncError, status: status, at: completionTime)
        } catch {
            let completionTime = finishedAt ?? Date()
            return failureResult(operationID: operationID, ledger: &updatedLedger,
                                 error: .network, status: .error, at: completionTime)
        }
    }

    public func fetchProfile(for account: JudgeAccount, adapter: any JudgeAdapter,
                             client: any HTTPClient) async throws -> PublicProfile {
        guard adapter.judge == account.judge else { throw AdapterError.invalidPayload }
        guard adapter.supports("PROFILE") else { throw AdapterError.unsupported }
        let data = try await client.get(try adapter.profileURL(for: account.handle))
        return try adapter.decodeProfile(data, requestedHandle: account.handle)
    }

    private func failureResult(operationID: UUID, ledger: inout SyncLedger,
                               error: SyncError, status: SyncStatus, at date: Date) -> ProfileSyncResult {
        let module = SyncModuleOutcome(stage: "PROFILE", status: status, attempted: 1, failure: error)
        ledger.close(id: operationID, status: status, error: error, modules: [module], at: date)
        return ProfileSyncResult(profile: nil, operation: ledger.operation(id: operationID)!, ledger: ledger)
    }
}

public struct ProfileSyncResult: Equatable, Sendable {
    public let profile: PublicProfile?
    public let operation: SyncOperation
    public let ledger: SyncLedger

    public init(profile: PublicProfile?, operation: SyncOperation, ledger: SyncLedger) {
        self.profile = profile
        self.operation = operation
        self.ledger = ledger
    }
}

public struct RatingSyncService: Sendable {
    public init() {}

    public func syncRating(for account: JudgeAccount, adapter: any JudgeAdapter,
                           client: any HTTPClient, ledger: SyncLedger, generation: String,
                           startedAt: Date, finishedAt: Date? = nil) async -> RatingSyncResult {
        var updatedLedger = ledger
        let operationID = updatedLedger.open(account: account, generation: generation, at: startedAt)
        do {
            try Task.checkCancellation()
            let changes = try await fetchRating(for: account, adapter: adapter, client: client)
            let completionTime = finishedAt ?? Date()
            try Task.checkCancellation()
            let module = SyncModuleOutcome(
                stage: "RATING", status: .success, attempted: changes.count, imported: changes.count)
            updatedLedger.close(id: operationID, status: .success, modules: [module], at: completionTime)
            return RatingSyncResult(
                changes: changes,
                operation: updatedLedger.operation(id: operationID)!,
                ledger: updatedLedger)
        } catch is CancellationError {
            let completionTime = finishedAt ?? Date()
            return failureResult(operationID: operationID, ledger: &updatedLedger,
                                 error: .cancelled, status: .cancelled, at: completionTime)
        } catch let error as AdapterError {
            let completionTime = finishedAt ?? Date()
            let syncError: SyncError
            switch error {
            case .offline: syncError = .offline
            case .network: syncError = .network
            case .authentication: syncError = .authentication
            case .apiFailure: syncError = .api
            case .unsupported: syncError = .unsupported
            case .invalidPayload: syncError = .parse
            }
            let status: SyncStatus = syncError == .offline ? .offline : .error
            return failureResult(operationID: operationID, ledger: &updatedLedger,
                                 error: syncError, status: status, at: completionTime)
        } catch {
            let completionTime = finishedAt ?? Date()
            return failureResult(operationID: operationID, ledger: &updatedLedger,
                                 error: .network, status: .error, at: completionTime)
        }
    }

    public func fetchRating(for account: JudgeAccount, adapter: any JudgeAdapter,
                            client: any HTTPClient) async throws -> [RatingChange] {
        guard adapter.judge == account.judge else { throw AdapterError.invalidPayload }
        guard adapter.supports("RATING") else { throw AdapterError.unsupported }
        let data = try await client.get(try adapter.ratingURL(for: account.handle))
        return try adapter.decodeRating(data, requestedHandle: account.handle)
    }

    private func failureResult(operationID: UUID, ledger: inout SyncLedger,
                               error: SyncError, status: SyncStatus, at date: Date) -> RatingSyncResult {
        let module = SyncModuleOutcome(stage: "RATING", status: status, attempted: 1, failure: error)
        ledger.close(id: operationID, status: status, error: error, modules: [module], at: date)
        return RatingSyncResult(changes: [], operation: ledger.operation(id: operationID)!, ledger: ledger)
    }
}

public struct RatingSyncResult: Equatable, Sendable {
    public let changes: [RatingChange]
    public let operation: SyncOperation
    public let ledger: SyncLedger

    public init(changes: [RatingChange], operation: SyncOperation, ledger: SyncLedger) {
        self.changes = changes
        self.operation = operation
        self.ledger = ledger
    }
}

public struct SubmissionSyncService: Sendable {
    public init() {}

    public func syncSubmissions(for account: JudgeAccount, adapter: any JudgeAdapter,
                                client: any HTTPClient, ledger: SyncLedger, generation: String,
                                startedAt: Date, finishedAt: Date? = nil) async -> SubmissionSyncResult {
        var updatedLedger = ledger
        let operationID = updatedLedger.open(account: account, generation: generation, at: startedAt)
        do {
            try Task.checkCancellation()
            let submissions = try await fetchSubmissions(for: account, adapter: adapter, client: client)
            let completionTime = finishedAt ?? Date()
            try Task.checkCancellation()
            let module = SyncModuleOutcome(
                stage: "SUBMISSIONS", status: .success,
                attempted: submissions.count, imported: submissions.count)
            updatedLedger.close(id: operationID, status: .success, modules: [module], at: completionTime)
            return SubmissionSyncResult(
                submissions: submissions,
                operation: updatedLedger.operation(id: operationID)!,
                ledger: updatedLedger)
        } catch is CancellationError {
            let completionTime = finishedAt ?? Date()
            return failureResult(operationID: operationID, ledger: &updatedLedger,
                                 error: .cancelled, status: .cancelled, at: completionTime)
        } catch let error as AdapterError {
            let completionTime = finishedAt ?? Date()
            let syncError: SyncError
            switch error {
            case .offline: syncError = .offline
            case .network: syncError = .network
            case .authentication: syncError = .authentication
            case .apiFailure: syncError = .api
            case .unsupported: syncError = .unsupported
            case .invalidPayload: syncError = .parse
            }
            let status: SyncStatus = syncError == .offline ? .offline : .error
            return failureResult(operationID: operationID, ledger: &updatedLedger,
                                 error: syncError, status: status, at: completionTime)
        } catch {
            let completionTime = finishedAt ?? Date()
            return failureResult(operationID: operationID, ledger: &updatedLedger,
                                 error: .network, status: .error, at: completionTime)
        }
    }

    public func fetchSubmissions(for account: JudgeAccount, adapter: any JudgeAdapter,
                                 client: any HTTPClient) async throws -> [SubmissionRecord] {
        guard adapter.judge == account.judge else { throw AdapterError.invalidPayload }
        guard adapter.supports("SUBMISSIONS") else { throw AdapterError.unsupported }
        let data = try await client.get(try adapter.submissionsURL(for: account.handle))
        return try adapter.decodeSubmissions(data, requestedHandle: account.handle)
    }

    private func failureResult(operationID: UUID, ledger: inout SyncLedger,
                               error: SyncError, status: SyncStatus, at date: Date) -> SubmissionSyncResult {
        let module = SyncModuleOutcome(stage: "SUBMISSIONS", status: status, attempted: 1, failure: error)
        ledger.close(id: operationID, status: status, error: error, modules: [module], at: date)
        return SubmissionSyncResult(
            submissions: [], operation: ledger.operation(id: operationID)!, ledger: ledger)
    }
}

public struct SubmissionSyncResult: Equatable, Sendable {
    public let submissions: [SubmissionRecord]
    public let operation: SyncOperation
    public let ledger: SyncLedger

    public init(submissions: [SubmissionRecord], operation: SyncOperation, ledger: SyncLedger) {
        self.submissions = submissions
        self.operation = operation
        self.ledger = ledger
    }
}
