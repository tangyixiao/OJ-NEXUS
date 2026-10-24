import Foundation
import XCTest
@testable import OJNexusCore

final class WorkspaceRecoveryTests: XCTestCase {
    func testFreshLoadIsReadOnlyAndFirstSaveCreatesRecoverableSnapshot() async throws {
        let (directory, url) = try workspace()
        defer { try? FileManager.default.removeItem(at: directory) }
        let store = LocalWorkspaceStore(url: url)

        let initial = try await store.load()
        XCTAssertEqual(initial, LocalWorkspaceState())
        XCTAssertFalse(FileManager.default.fileExists(atPath: url.path))
        XCTAssertFalse(FileManager.default.fileExists(atPath: url.appendingPathExtension("bak").path))
        try await store.save(LocalWorkspaceState())
        XCTAssertTrue(FileManager.default.fileExists(atPath: url.path))
        XCTAssertTrue(FileManager.default.fileExists(atPath: url.appendingPathExtension("bak").path))
    }

    func testLegacyLoadDoesNotRewriteUntilDeliberateSave() async throws {
        let (directory, url) = try workspace()
        defer { try? FileManager.default.removeItem(at: directory) }
        let bytes = Data(#"{"accounts":[],"ledger":{"operations":[]}}"#.utf8)
        try bytes.write(to: url)
        let store = LocalWorkspaceStore(url: url)

        let state = try await store.load()
        XCTAssertTrue(state.profiles.isEmpty)
        XCTAssertEqual(try Data(contentsOf: url), bytes)
        try await store.save(state)
        XCTAssertTrue(String(decoding: try Data(contentsOf: url), as: UTF8.self).contains("formatVersion"))
    }

    func testFutureFormatIsTypedAndBlocksSave() async throws {
        let (directory, url) = try workspace()
        defer { try? FileManager.default.removeItem(at: directory) }
        let bytes = Data(#"{"formatVersion":99,"accounts":[],"ledger":{"operations":[]}}"#.utf8)
        try bytes.write(to: url)
        let store = LocalWorkspaceStore(url: url)

        do {
            _ = try await store.load()
            XCTFail("future format must not load")
        } catch let failure as WorkspaceStorageFailure {
            XCTAssertEqual(failure, .unsupportedVersion)
        }
        let blockedStatus = await store.storageStatus()
        XCTAssertEqual(blockedStatus, .blocked(.unsupportedVersion))
        do {
            try await store.save(LocalWorkspaceState())
            XCTFail("failed load must block saving")
        } catch { }
        XCTAssertEqual(try Data(contentsOf: url), bytes)
    }

    func testCorruptMainCanBeQuarantinedAndRestoredWithoutChangingSnapshot() async throws {
        let (directory, url) = try workspace()
        defer { try? FileManager.default.removeItem(at: directory) }
        let store = LocalWorkspaceStore(url: url)
        _ = try await store.load()
        let validState = LocalWorkspaceState()
        try await store.save(validState)
        let backupURL = directory.appendingPathComponent("state.json.bak")
        let backup = try Data(contentsOf: backupURL)
        let corrupt = Data("raw-corrupt-state".utf8)
        try corrupt.write(to: url)
        do {
            _ = try await store.load()
            XCTFail("corrupt main must be rejected")
        } catch let failure as WorkspaceStorageFailure {
            XCTAssertEqual(failure, .corrupt)
        }
        let recoverableStatus = await store.storageStatus()
        XCTAssertEqual(recoverableStatus, .recoverable(.corrupt))

        let restored = try await store.restorePreviousSnapshot()
        XCTAssertEqual(restored, validState)
        XCTAssertEqual(try Data(contentsOf: url), backup)
        XCTAssertEqual(try Data(contentsOf: backupURL), backup)
        let quarantines = try FileManager.default.contentsOfDirectory(at: directory, includingPropertiesForKeys: nil)
            .filter { $0.lastPathComponent.hasPrefix("state.json.quarantine-") }
        XCTAssertEqual(quarantines.count, 1)
        XCTAssertEqual(try Data(contentsOf: XCTUnwrap(quarantines.first)), corrupt)
    }

    func testMissingPreviouslyEstablishedMainIsRecoverable() async throws {
        let (directory, url) = try workspace()
        defer { try? FileManager.default.removeItem(at: directory) }
        let store = LocalWorkspaceStore(url: url)
        _ = try await store.load()
        try await store.save(LocalWorkspaceState())
        try FileManager.default.removeItem(at: url)

        do {
            _ = try await store.load()
            XCTFail("missing established main must fail")
        } catch let failure as WorkspaceStorageFailure {
            XCTAssertEqual(failure, .missingEstablished)
        }
        let recoverableStatus = await store.storageStatus()
        XCTAssertEqual(recoverableStatus, .recoverable(.missingEstablished))
        _ = try await store.restorePreviousSnapshot()
        XCTAssertTrue(FileManager.default.fileExists(atPath: url.path))
    }

    private func workspace() throws -> (directory: URL, state: URL) {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("ojnexus-workspace-recovery-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        return (directory, directory.appendingPathComponent("state.json"))
    }
}
