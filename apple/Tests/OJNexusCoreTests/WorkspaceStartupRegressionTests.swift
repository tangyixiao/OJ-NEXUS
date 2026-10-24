import Foundation
import XCTest
@testable import OJNexusCore
@testable import OJNexusUI

final class WorkspaceStartupRegressionTests: XCTestCase {
    @MainActor
    func testCorruptStartupCannotBeOverwrittenByAccountConfiguration() async throws {
        let (directory, url) = try temporaryWorkspace()
        defer { try? FileManager.default.removeItem(at: directory) }
        let originalBytes = Data("{ definitely not valid JSON".utf8)
        try originalBytes.write(to: url)
        let store = LocalWorkspaceStore(url: url)
        let model = NexusDashboardModel(workspaceStore: store)

        await model.load()
        await model.configurePublicAccount(judge: .codeforces, handle: "tourist")

        XCTAssertEqual(try Data(contentsOf: url), originalBytes)
        XCTAssertTrue(model.accounts.isEmpty)
    }

    @MainActor
    func testFutureFormatVersionCannotBeRewrittenDuringStartup() async throws {
        let (directory, url) = try temporaryWorkspace()
        defer { try? FileManager.default.removeItem(at: directory) }
        let originalBytes = Data(#"{"formatVersion":99,"accounts":[],"ledger":{"operations":[]},"profiles":[],"ratingChanges":[],"submissions":[]}"#.utf8)
        try originalBytes.write(to: url)
        let model = NexusDashboardModel(workspaceStore: LocalWorkspaceStore(url: url))

        await model.load()

        XCTAssertEqual(try Data(contentsOf: url), originalBytes)
        XCTAssertTrue(model.accounts.isEmpty)
    }

    @MainActor
    func testStartupDoesNotRewriteRunningLedgerBytes() async throws {
        let (directory, url) = try temporaryWorkspace()
        defer { try? FileManager.default.removeItem(at: directory) }
        let account = try XCTUnwrap(JudgeAccount(judge: .codeforces, handle: "tourist"))
        let operation = SyncOperation(
            account: account,
            generation: "apple-0.1",
            startedAt: Date(timeIntervalSince1970: 1),
            status: .running)
        let originalBytes = try JSONEncoder().encode(
            LocalWorkspaceState(ledger: SyncLedger(operations: [operation])))
        try originalBytes.write(to: url)
        let model = NexusDashboardModel(workspaceStore: LocalWorkspaceStore(url: url))

        await model.load()

        XCTAssertEqual(try Data(contentsOf: url), originalBytes)
    }

    func testDirectSaveWithoutLoadFailsAndLeavesMainAbsent() async throws {
        let (directory, url) = try temporaryWorkspace()
        defer { try? FileManager.default.removeItem(at: directory) }
        let store = LocalWorkspaceStore(url: url)

        do {
            try await store.save(LocalWorkspaceState())
            XCTFail("Saving without a successful load should fail")
        } catch {
            XCTAssertFalse(FileManager.default.fileExists(atPath: url.path))
        }
    }

    private func temporaryWorkspace() throws -> (directory: URL, state: URL) {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("ojnexus-startup-regression-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        return (directory, directory.appendingPathComponent("state.json"))
    }
}
