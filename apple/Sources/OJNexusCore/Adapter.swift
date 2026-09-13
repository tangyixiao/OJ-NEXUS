import Foundation

#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

public enum AdapterError: Error, Equatable, Sendable {
    case apiFailure
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
        judge = try container.decode(JudgeID.self, forKey: .judge)
        handle = try container.decode(String.self, forKey: .handle)
        displayName = try container.decodeIfPresent(String.self, forKey: .displayName)
        rating = try container.decodeIfPresent(Int.self, forKey: .rating)
        rank = try container.decodeIfPresent(String.self, forKey: .rank)
        fetchedAt = try container.decodeIfPresent(Date.self, forKey: .fetchedAt)
    }
}

public protocol JudgeAdapter: Sendable {
    var judge: JudgeID { get }

    func profileURL(for handle: String) throws -> URL
    func decodeProfile(_ data: Data, requestedHandle: String) throws -> PublicProfile
}

public protocol HTTPClient: Sendable {
    func get(_ url: URL) async throws -> Data
}

public struct URLSessionHTTPClient: HTTPClient, Sendable {
    public init() {}

    public func get(_ url: URL) async throws -> Data {
        do {
            let (data, response) = try await URLSession.shared.data(from: url)
            guard let httpResponse = response as? HTTPURLResponse,
                  (200..<300).contains(httpResponse.statusCode) else {
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
            case .apiFailure, .unsupported: syncError = .api
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
