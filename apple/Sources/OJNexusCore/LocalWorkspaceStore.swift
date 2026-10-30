import Foundation

public struct LocalWorkspaceState: Codable, Equatable, Sendable {
    public var accounts: [JudgeAccount]
    public var ledger: SyncLedger
    public var profiles: [PublicProfile]
    public var ratingChanges: [RatingChange]
    public var submissions: [SubmissionRecord]

    public init(accounts: [JudgeAccount] = [], ledger: SyncLedger = SyncLedger(), profiles: [PublicProfile] = [],
                ratingChanges: [RatingChange] = [], submissions: [SubmissionRecord] = []) {
        self.accounts = accounts
        self.ledger = ledger
        self.profiles = profiles
        self.ratingChanges = ratingChanges
        self.submissions = submissions
    }

    private enum CodingKeys: String, CodingKey {
        case accounts, ledger, profiles, ratingChanges, submissions
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        accounts = try container.decode([JudgeAccount].self, forKey: .accounts)
        ledger = try container.decode(SyncLedger.self, forKey: .ledger)
        profiles = try container.decodeIfPresent([PublicProfile].self, forKey: .profiles) ?? []
        ratingChanges = try container.decodeIfPresent([RatingChange].self, forKey: .ratingChanges) ?? []
        submissions = try container.decodeIfPresent([SubmissionRecord].self, forKey: .submissions) ?? []
    }
}

public actor LocalWorkspaceStore {
    private let url: URL?
    private var locationFailure: WorkspaceStorageFailure?
    private var status: WorkspaceStorageStatus = .unloaded
    private var baseline: Data??
    private var observedMain: Data?
    private var observedBackup: Data?

    public static func defaultURL(fileManager: FileManager = .default) throws -> URL {
        guard let base = fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask).first else {
            throw WorkspaceStorageFailure.unavailable
        }
        return base.appendingPathComponent("OJ-NEXUS", isDirectory: true).appendingPathComponent("state.json")
    }

    public init() {
        do {
            self.url = try Self.defaultURL()
        } catch {
            self.url = nil
            self.locationFailure = .unavailable
        }
    }

    public init(url: URL) {
        self.url = url
    }

    public func storageStatus() -> WorkspaceStorageStatus { status }

    public func load() throws -> LocalWorkspaceState {
        guard let url else {
            status = .blocked(locationFailure ?? .unavailable)
            throw locationFailure ?? WorkspaceStorageFailure.unavailable
        }
        do {
            guard let bytes = try WorkspaceFileIO.read(url) else {
                let backup = try WorkspaceFileIO.read(Self.backupURL(for: url))
                if backup != nil || observedMain != nil {
                    observedBackup = backup
                    baseline = nil
                    status = backup.flatMap { try? WorkspaceFileCodec.decode($0) } != nil
                        ? .recoverable(.missingEstablished) : .blocked(.missingEstablished)
                    throw WorkspaceStorageFailure.missingEstablished
                }
                observedMain = nil
                observedBackup = nil
                baseline = .some(nil)
                status = .ready
                return LocalWorkspaceState()
            }
            do {
                let state = try WorkspaceFileCodec.decode(bytes)
                observedMain = bytes
                observedBackup = try WorkspaceFileIO.read(Self.backupURL(for: url))
                baseline = .some(bytes)
                status = .ready
                return state
            } catch let failure as WorkspaceStorageFailure {
                observedMain = bytes
                observedBackup = try? WorkspaceFileIO.read(Self.backupURL(for: url))
                baseline = nil
                if failure == .corrupt,
                   let backup = observedBackup,
                   (try? WorkspaceFileCodec.decode(backup)) != nil {
                    status = .recoverable(.corrupt)
                } else {
                    status = .blocked(failure)
                }
                throw failure
            }
        } catch let failure as WorkspaceStorageFailure {
            if case .recoverable = status { } else { status = .blocked(failure) }
            throw failure
        } catch {
            status = .blocked(.unavailable)
            throw WorkspaceStorageFailure.unavailable
        }
    }

    public func save(_ state: LocalWorkspaceState) throws {
        guard case .ready = status, let baseline, let url else {
            let failure: WorkspaceStorageFailure
            switch status {
            case .blocked(let reason), .recoverable(let reason): failure = reason
            default: failure = locationFailure ?? .notLoaded
            }
            status = .blocked(failure)
            throw failure
        }
        let intended = try WorkspaceFileCodec.encode(state)
        var installingMain = false
        do {
            try WorkspaceFileIO.withLock(url: url) {
            do {
                let current = try WorkspaceFileIO.read(url)
                guard current == baseline else { throw WorkspaceStorageFailure.conflict }
                if let current {
                    _ = try WorkspaceFileCodec.decode(current)
                    do { try WorkspaceFileIO.install(current, at: Self.backupURL(for: url), replace: true) }
                    catch { throw WorkspaceStorageFailure.backupUnavailable }
                } else {
                    guard try WorkspaceFileIO.read(Self.backupURL(for: url)) == nil else {
                        throw WorkspaceStorageFailure.conflict
                    }
                    do { try WorkspaceFileIO.install(intended, at: Self.backupURL(for: url), replace: false) }
                    catch { throw WorkspaceStorageFailure.backupUnavailable }
                }
                installingMain = true
                try WorkspaceFileIO.install(intended, at: url, replace: current != nil)
                self.baseline = .some(intended)
                self.observedMain = intended
                self.observedBackup = current ?? intended
                self.status = .ready
            } catch {
                if (try? WorkspaceFileIO.read(url)) == intended {
                    self.baseline = .some(intended)
                    self.observedMain = intended
                    self.status = .ready
                    return
                }
                let failure = installingMain ? WorkspaceStorageFailure.uncertainOutcome : WorkspaceFileIO.failure(error)
                self.status = .blocked(failure)
                throw failure
            }
            }
        } catch {
            let failure = WorkspaceFileIO.failure(error)
            status = .blocked(failure)
            throw failure
        }
    }

    public func restorePreviousSnapshot() throws -> LocalWorkspaceState {
        guard let url else { throw locationFailure ?? WorkspaceStorageFailure.unavailable }
        guard case .recoverable(let reason) = status,
              reason == .corrupt || reason == .missingEstablished,
              let backupBytes = observedBackup,
              let state = try? WorkspaceFileCodec.decode(backupBytes) else {
            status = .blocked(.recoveryUnavailable)
            throw WorkspaceStorageFailure.recoveryUnavailable
        }
        do {
            try WorkspaceFileIO.withLock(url: url) {
                let current = try WorkspaceFileIO.read(url)
                let backup = try WorkspaceFileIO.read(Self.backupURL(for: url))
                guard current == observedMain, backup == observedBackup else { throw WorkspaceStorageFailure.conflict }
                guard let backup, let current else {
                    guard current == nil, let backup else { throw WorkspaceStorageFailure.recoveryUnavailable }
                    try WorkspaceFileIO.install(backup, at: url, replace: false)
                    return
                }
                _ = try WorkspaceFileCodec.decode(backup)
                if (try? WorkspaceFileCodec.decode(current)) == nil {
                    let quarantine = url.deletingLastPathComponent()
                        .appendingPathComponent("state.json.quarantine-\(UUID().uuidString).json")
                    try WorkspaceFileIO.install(current, at: quarantine, replace: false)
                } else {
                    throw WorkspaceStorageFailure.recoveryUnavailable
                }
                try WorkspaceFileIO.install(backup, at: url, replace: true)
            }
            baseline = .some(backupBytes)
            observedMain = backupBytes
            status = .ready
            return state
        } catch {
            let failure = WorkspaceFileIO.failure(error)
            status = .blocked(failure)
            throw failure
        }
    }

    private static func backupURL(for url: URL) -> URL {
        url.deletingLastPathComponent().appendingPathComponent("state.json.bak")
    }

}
