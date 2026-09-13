import Foundation

public struct LocalWorkspaceState: Codable, Equatable, Sendable {
    public var accounts: [JudgeAccount]
    public var ledger: SyncLedger
    public var profiles: [PublicProfile]

    public init(accounts: [JudgeAccount] = [], ledger: SyncLedger = SyncLedger(), profiles: [PublicProfile] = []) {
        self.accounts = accounts
        self.ledger = ledger
        self.profiles = profiles
    }

    private enum CodingKeys: String, CodingKey {
        case accounts
        case ledger
        case profiles
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        accounts = try container.decode([JudgeAccount].self, forKey: .accounts)
        ledger = try container.decode(SyncLedger.self, forKey: .ledger)
        profiles = try container.decodeIfPresent([PublicProfile].self, forKey: .profiles) ?? []
    }
}

public actor LocalWorkspaceStore {
    private let url: URL

    public static func defaultURL(fileManager: FileManager = .default) -> URL {
        let base = fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? fileManager.temporaryDirectory
        return base.appendingPathComponent("OJ-NEXUS", isDirectory: true)
            .appendingPathComponent("state.json")
    }

    public init(url: URL) {
        self.url = url
    }

    public func load() throws -> LocalWorkspaceState {
        guard FileManager.default.fileExists(atPath: url.path) else {
            return LocalWorkspaceState()
        }
        let data = try Data(contentsOf: url)
        return try JSONDecoder().decode(LocalWorkspaceState.self, from: data)
    }

    public func save(_ state: LocalWorkspaceState) throws {
        let directory = url.deletingLastPathComponent()
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let data = try JSONEncoder().encode(state)
        try data.write(to: url, options: .atomic)
    }
}
