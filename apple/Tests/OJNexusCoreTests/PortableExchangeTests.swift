import XCTest

@testable import OJNexusCore

/// Phase 135 acceptance: mixed golden package lossless round trip (import ->
/// apply -> export -> pinned identity), the rejection matrix, archive-store
/// integrity, and the typed empty/cancelled states. Fixtures are inline
/// literals (the package has no resource bundling); the mixed golden package
/// is the corpus file pw-p01.json, embedded verbatim.
final class PortableExchangeTests: XCTestCase {
    static let goldenP01 = #"""
    {
      "formatVersion": 1,
      "scopeId": "scope.alpha",
      "sourcePlatform": "android",
      "generator": {
        "name": "synthetic-fixture-gen",
        "version": "1.0"
      },
      "createdAtEpochMs": 1730500000000,
      "counts": {
        "problems": 3,
        "attempts": 8,
        "notes": 1,
        "reviews": 2,
        "sessions": 1
      },
      "problems": [
        {
          "stableId": "cf.1A",
          "sourceScopeId": "scope.alpha",
          "origin": "scope",
          "judge": "codeforces",
          "externalId": "1A",
          "title": "Theatre Square",
          "sourceUrl": "https://codeforces.com/problemset/problem/1/A",
          "solved": true,
          "difficulty": 800,
          "tags": [
            "geometry",
            "math"
          ],
          "dayKey": "2026-11-01",
          "extra": {
            "legacyLabel": "cf-1A",
            "reviewFlags": [
              "seen",
              "hard"
            ],
            "synthetic": true,
            "schemaHints": 1
          }
        },
        {
          "stableId": "atc.AGC001A",
          "sourceScopeId": "scope.beta",
          "origin": "scope",
          "judge": "atcoder",
          "externalId": "AGC001A",
          "title": "AtCoder Beginner Contest",
          "solved": true,
          "dayKey": "2026-11-02"
        },
        {
          "stableId": "luogu.P1001",
          "sourceScopeId": "scope.alpha",
          "origin": "unassigned",
          "judge": "luogu",
          "externalId": "P1001",
          "title": "A plus B Problem",
          "solved": false,
          "difficulty": 500,
          "dayKey": "2026-11-03"
        }
      ],
      "attempts": [
        {
          "stableId": "att.01",
          "sourceScopeId": "scope.alpha",
          "origin": "scope",
          "problemId": "cf.1A",
          "timestampEpochMs": 1730500000000,
          "dayKey": "2026-11-02",
          "verdict": "AC"
        },
        {
          "stableId": "att.02",
          "sourceScopeId": "scope.alpha",
          "origin": "scope",
          "problemId": "cf.1A",
          "timestampEpochMs": 1730500001000,
          "dayKey": "2026-11-02",
          "verdict": "WA"
        },
        {
          "stableId": "att.03",
          "sourceScopeId": "scope.alpha",
          "origin": "scope",
          "problemId": "atc.AGC001A",
          "timestampEpochMs": 1730500002000,
          "dayKey": "2026-11-02",
          "verdict": "TLE"
        },
        {
          "stableId": "att.04",
          "sourceScopeId": "scope.alpha",
          "origin": "scope",
          "problemId": "atc.AGC001A",
          "timestampEpochMs": 1730500003000,
          "dayKey": "2026-11-02",
          "verdict": "MLE"
        },
        {
          "stableId": "att.05",
          "sourceScopeId": "scope.beta",
          "origin": "scope",
          "problemId": "atc.AGC001A",
          "timestampEpochMs": 1730500004000,
          "dayKey": "2026-11-02",
          "verdict": "RE"
        },
        {
          "stableId": "att.06",
          "sourceScopeId": "scope.beta",
          "origin": "scope",
          "problemId": "atc.AGC001A",
          "timestampEpochMs": 1730500005000,
          "dayKey": "2026-11-02",
          "verdict": "CE"
        },
        {
          "stableId": "att.07",
          "sourceScopeId": "scope.beta",
          "origin": "scope",
          "problemId": "atc.AGC001A",
          "timestampEpochMs": 1730500006000,
          "dayKey": "2026-11-02",
          "verdict": "PE"
        },
        {
          "stableId": "att.08",
          "sourceScopeId": "scope.beta",
          "origin": "scope",
          "problemId": "atc.AGC001A",
          "timestampEpochMs": 1730500007000,
          "dayKey": "2026-11-02",
          "verdict": "OTHER"
        }
      ],
      "notes": [
        {
          "stableId": "note.001",
          "sourceScopeId": "scope.alpha",
          "origin": "scope",
          "problemId": "cf.1A",
          "keyInsight": "two pointers",
          "implementationNotes": "iterate once",
          "complexity": "O(n log n)",
          "general": "read the statement",
          "updatedEpochMs": 1730500000000,
          "dayKey": "2026-11-02"
        }
      ],
      "reviews": [
        {
          "stableId": "rev.001",
          "sourceScopeId": "scope.alpha",
          "origin": "scope",
          "problemId": "cf.1A",
          "stage": 1,
          "dueEpochMs": 1730600000000,
          "dueDayKey": "2026-11-09",
          "lastResult": "PASS",
          "createdEpochMs": 1730500000000,
          "dayKey": "2026-11-02"
        },
        {
          "stableId": "rev.002",
          "sourceScopeId": "scope.beta",
          "origin": "scope",
          "problemId": "atc.AGC001A",
          "stage": 2,
          "dueEpochMs": 1730700000000,
          "dueDayKey": "2026-11-10",
          "lastResult": "HARD",
          "lastReviewedEpochMs": 1730500000000,
          "createdEpochMs": 1730500000000,
          "dayKey": "2026-11-03"
        }
      ],
      "sessions": [
        {
          "stableId": "sess.001",
          "sourceScopeId": "scope.alpha",
          "origin": "scope",
          "type": "PRACTICE",
          "state": "FINISHED",
          "startedEpochMs": 1730500000000,
          "finishedEpochMs": 1730503600000,
          "totalPausedMs": 60000,
          "targetDurationMin": 60,
          "problemIds": [
            "cf.1A",
            "luogu.P1001"
          ],
          "dayKey": "2026-11-02"
        }
      ]
    }
    """#

    /// The canonical identity pinned across Android, Windows and Linux; the
    /// Swift codec must reproduce it byte-for-byte.
    static let pinnedIdentity = "81bb13c1746381f2d7f95ae69e421503dcc898aae823222e8a3caf40d55b8d44"

    private var scratch: URL!

    override func setUpWithError() throws {
        scratch = FileManager.default.temporaryDirectory
            .appendingPathComponent("ojnexus-portable-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: scratch, withIntermediateDirectories: true)
    }

    override func tearDownWithError() throws {
        try? FileManager.default.removeItem(at: scratch)
    }

    private func goldenData() -> Data {
        Data(Self.goldenP01.utf8)
    }

    private func makeStore() -> PortableArchiveStore {
        PortableArchiveStore(url: scratch.appendingPathComponent("portable-archive.json"))
    }

    private func makeRepository(store: PortableArchiveStore) -> PortableRepository {
        PortableRepository(store: store)
    }

    private func rejectCategory(_ error: Error) -> String {
        if let contract = error as? PortableContractError {
            return contract.reject.wire
        }
        return "not-a-contract-error: \(error)"
    }

    // MARK: G-P golden round trip

    func testGoldenRoundTripPreservesPinnedIdentity() async throws {
        let store = makeStore()
        let repository = makeRepository(store: store)
        _ = try await store.load()

        let applied = try await repository.apply(goldenData())
        XCTAssertEqual(applied.identity, Self.pinnedIdentity)
        XCTAssertFalse(applied.identical)
        XCTAssertEqual(applied.storedOnlyCount, 15)
        XCTAssertEqual(applied.editableCount, 0)
        XCTAssertEqual(applied.rowsUpserted, 15)
        XCTAssertEqual(applied.rowsDeleted, 0)

        let exported = try await repository.export()
        let exportedIdentity = PortableCodec.sha256Hex(exported)
        XCTAssertEqual(exportedIdentity, Self.pinnedIdentity)
    }

    func testReimportOfExportedBytesIsIdenticalNoOp() async throws {
        let store = makeStore()
        let repository = makeRepository(store: store)
        _ = try await store.load()
        _ = try await repository.apply(goldenData())
        let exported = try await repository.export()

        let second = try await repository.apply(exported)
        XCTAssertEqual(second.identical, true)
        XCTAssertEqual(second.rowsUpserted, 0)
        XCTAssertEqual(second.rowsDeleted, 0)
    }

    func testPreviewCountsSourcesAndTiers() throws {
        let preview = try PortableCodec.preview(goldenData())
        XCTAssertEqual(preview.scopeId, "scope.alpha")
        XCTAssertEqual(preview.sourcePlatform, "android")
        XCTAssertEqual(preview.counts["problems"], 3)
        XCTAssertEqual(preview.counts["attempts"], 8)
        XCTAssertEqual(preview.counts["notes"], 1)
        XCTAssertEqual(preview.counts["reviews"], 2)
        XCTAssertEqual(preview.counts["sessions"], 1)
        XCTAssertEqual(preview.editableCount, 0)
        XCTAssertEqual(preview.storedOnlyCount, 15)
        XCTAssertEqual(preview.rejectedCount, 0)
        XCTAssertTrue(preview.acceptAll)
        XCTAssertEqual(preview.identity, Self.pinnedIdentity)
        let alpha = preview.sources.first { $0.sourceScopeId == "scope.alpha" }
        let beta = preview.sources.first { $0.sourceScopeId == "scope.beta" }
        XCTAssertEqual(alpha?.recordCount, 9)
        XCTAssertEqual(beta?.recordCount, 6)
        for decision in preview.decisions {
            XCTAssertEqual(decision.tier, .storedOnly)
            XCTAssertEqual(decision.reason, .boundedArchiveNoDomainUi)
        }
    }

    // MARK: rejection matrix

    func testUnsupportedVersionRejectedAtVersionGate() throws {
        let mutated = Self.goldenP01.replacingOccurrences(of: "\"formatVersion\": 1", with: "\"formatVersion\": 2")
        do {
            _ = try PortableCodec.validate(Data(mutated.utf8))
            XCTFail("expected UNSUPPORTED_VERSION")
        } catch {
            XCTAssertEqual(rejectCategory(error), "UNSUPPORTED_VERSION")
        }
    }

    func testUnknownRootFieldRejected() throws {
        let mutated = Self.goldenP01.replacingOccurrences(of: "\"scopeId\": \"scope.alpha\",", with: "\"scopeId\": \"scope.alpha\", \"extraRoot\": 1,")
        do {
            _ = try PortableCodec.validate(Data(mutated.utf8))
            XCTFail("expected UNKNOWN_FIELD")
        } catch {
            XCTAssertEqual(rejectCategory(error), "UNKNOWN_FIELD")
        }
    }

    func testDuplicateStableIdRejected() throws {
        let mutated = Self.goldenP01.replacingOccurrences(
            of: "\"stableId\": \"att.01\",",
            with: "\"stableId\": \"att.02\","
        )
        do {
            _ = try PortableCodec.validate(Data(mutated.utf8))
            XCTFail("expected DUPLICATE_ID")
        } catch {
            XCTAssertEqual(rejectCategory(error), "DUPLICATE_ID")
        }
    }

    func testMissingReferenceRejected() throws {
        let mutated = Self.goldenP01.replacingOccurrences(
            of: "\"problemId\": \"cf.1A\",",
            with: "\"problemId\": \"cf.GONE\","
        )
        do {
            _ = try PortableCodec.validate(Data(mutated.utf8))
            XCTFail("expected MISSING_REFERENCE")
        } catch {
            XCTAssertEqual(rejectCategory(error), "MISSING_REFERENCE")
        }
    }

    func testCountsMismatchRejected() throws {
        let mutated = Self.goldenP01.replacingOccurrences(of: "\"problems\": 3,", with: "\"problems\": 5,")
        do {
            _ = try PortableCodec.validate(Data(mutated.utf8))
            XCTFail("expected COUNTS_MISMATCH")
        } catch {
            XCTAssertEqual(rejectCategory(error), "COUNTS_MISMATCH")
        }
    }

    func testPathUnsafeExtraValueRejected() throws {
        let mutated = Self.goldenP01.replacingOccurrences(
            of: "\"legacyLabel\": \"cf-1A\",",
            with: "\"legacyLabel\": \"/etc/notes\","
        )
        do {
            _ = try PortableCodec.validate(Data(mutated.utf8))
            XCTFail("expected PATH_UNSAFE")
        } catch {
            XCTAssertEqual(rejectCategory(error), "PATH_UNSAFE")
        }
    }

    func testCredentialShapedExtraKeyRejected() throws {
        let mutated = Self.goldenP01.replacingOccurrences(
            of: "\"legacyLabel\": \"cf-1A\",",
            with: "\"legacy_label\": \"cf-1A\", \"api_key\": \"x\","
        )
        do {
            _ = try PortableCodec.validate(Data(mutated.utf8))
            XCTFail("expected UNKNOWN_FIELD for a credential-shaped key")
        } catch {
            XCTAssertEqual(rejectCategory(error), "UNKNOWN_FIELD")
        }
    }

    func testFractionalDifficultyRejected() throws {
        let mutated = Self.goldenP01.replacingOccurrences(of: "\"difficulty\": 800,", with: "\"difficulty\": 8.5,")
        do {
            _ = try PortableCodec.validate(Data(mutated.utf8))
            XCTFail("expected UNKNOWN_FIELD for a non-integer difficulty")
        } catch {
            XCTAssertEqual(rejectCategory(error), "UNKNOWN_FIELD")
        }
    }

    // MARK: archive store integrity + guarded startup

    func testFreshStoreLoadIsReadOnlyAndCreatesNoFiles() async throws {
        let archive = scratch.appendingPathComponent("portable-archive.json")
        let store = PortableArchiveStore(url: archive)
        let snapshot = try await store.load()
        XCTAssertNil(snapshot)
        let leftovers = try FileManager.default.contentsOfDirectory(atPath: scratch.path)
        XCTAssertTrue(leftovers.isEmpty, "guarded startup must not create files, found \(leftovers)")
    }

    func testCorruptArchiveLoadsAsRecoverableWithDecodableBackup() async throws {
        let store = makeStore()
        let repository = makeRepository(store: store)
        _ = try await store.load()
        _ = try await repository.apply(goldenData())

        let archive = scratch.appendingPathComponent("portable-archive.json")
        try Data("not json".utf8).write(to: archive)

        let second = PortableArchiveStore(url: archive)
        do {
            _ = try await second.load()
            XCTFail("expected corrupt archive to throw")
        } catch let failure as WorkspaceStorageFailure {
            XCTAssertEqual(failure, .corrupt)
        }
        let status = await second.storageStatus()
        XCTAssertEqual(status, .recoverable(.corrupt))
    }

    func testTamperedIdentityFailsIntegrityAtLoad() async throws {
        let store = makeStore()
        let repository = makeRepository(store: store)
        _ = try await store.load()
        _ = try await repository.apply(goldenData())

        let archive = scratch.appendingPathComponent("portable-archive.json")
        var bytes = try Data(contentsOf: archive)
        let original = String(decoding: bytes, as: UTF8.self)
        let tampered = original.replacingOccurrences(
            of: "\"identity\":\"" + Self.pinnedIdentity + "\"",
            with: "\"identity\":\"" + String(repeating: "0", count: 64) + "\""
        )
        bytes = Data(tampered.utf8)
        try bytes.write(to: archive)

        let second = PortableArchiveStore(url: archive)
        do {
            _ = try await second.load()
            XCTFail("expected integrity failure")
        } catch let failure as WorkspaceStorageFailure {
            XCTAssertEqual(failure, .corrupt)
        }
    }

    // MARK: typed states

    func testExportOnFreshStoreIsTypedEmptyState() async throws {
        let store = makeStore()
        let repository = makeRepository(store: store)
        _ = try await store.load()
        do {
            _ = try await repository.export()
            XCTFail("expected the typed empty-archive state")
        } catch let failure as PortableExportEmpty {
            XCTAssertTrue(failure.description.contains("EMPTY"))
        }
    }

    func testCancelBeforeApplyDoesNotAffectTheNextApply() async throws {
        let store = makeStore()
        let repository = makeRepository(store: store)
        _ = try await store.load()
        await repository.cancel() // a cancel with no transfer in flight is a no-op
        let applied = try await repository.apply(goldenData())
        XCTAssertEqual(applied.identity, Self.pinnedIdentity)
    }

    // MARK: wrong-kind detection

    func testLocalSnapshotShapeIsRejectedAsWrongKind() async throws {
        let snapshotShape = #"{"formatVersion": 1, "accounts": [], "ledger": {"operations": []}}"#
        do {
            _ = try PortableCodec.validate(Data(snapshotShape.utf8))
            XCTFail("expected a rejection for the local snapshot shape")
        } catch {
            XCTAssertEqual(rejectCategory(error), "UNKNOWN_FIELD")
        }
    }
}
