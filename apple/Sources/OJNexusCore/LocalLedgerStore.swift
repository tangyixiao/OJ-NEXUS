import Foundation

public actor LocalLedgerStore {
    private let url: URL

    public static func defaultURL(fileManager: FileManager = .default) -> URL {
        let base = fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? fileManager.temporaryDirectory
        return base.appendingPathComponent("OJ-NEXUS", isDirectory: true)
            .appendingPathComponent("ledger.json")
    }

    public init(url: URL) {
        self.url = url
    }

    public func load() throws -> SyncLedger {
        guard FileManager.default.fileExists(atPath: url.path) else {
            return SyncLedger()
        }
        let data = try Data(contentsOf: url)
        return try JSONDecoder().decode(SyncLedger.self, from: data)
    }

    public func save(_ ledger: SyncLedger) throws {
        let directory = url.deletingLastPathComponent()
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let data = try JSONEncoder().encode(ledger)
        try data.write(to: url, options: .atomic)
    }
}
