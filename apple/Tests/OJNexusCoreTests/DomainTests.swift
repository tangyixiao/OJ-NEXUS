import Foundation
import XCTest
@testable import OJNexusCore
@testable import OJNexusUI

final class DomainTests: XCTestCase {
    func testJudgeIDParsingIsCaseInsensitive() {
        XCTAssertEqual(JudgeID.parse(" CoDeFoRcEs "), .codeforces)
        XCTAssertEqual(JudgeID.parse("AC"), .atcoder)
        XCTAssertEqual(JudgeID.parse("lg"), .luogu)
        XCTAssertNil(JudgeID.parse("unknown"))
    }

    func testAccountNormalizesHandleAndRejectsBlank() {
        XCTAssertEqual(JudgeAccount(judge: .codeforces, handle: "  tourist ")?.handle, "tourist")
        XCTAssertNil(JudgeAccount(judge: .atcoder, handle: "  "))
        XCTAssertNil(JudgeAccount(judge: .atcoder, handle: "tourist/other"))
        XCTAssertNil(JudgeAccount(judge: .luogu, handle: "uid:abc"))
    }

    func testAccountRejectsLuoguHandlesWithNonUidPrefix() {
        XCTAssertNil(JudgeAccount(judge: .luogu, handle: "user:2"))
        XCTAssertNil(JudgeAccount(judge: .luogu, handle: "uid:2:3"))
    }

    func testAccountRejectsCodeforcesHandlesThatCanAlterQueryParameters() {
        XCTAssertNil(JudgeAccount(judge: .codeforces, handle: "tourist&handles=other"))
        XCTAssertNil(JudgeAccount(judge: .codeforces, handle: "tourist#fragment"))
    }

    func testAccountRejectsAtCoderHandlesOutsideTheExistingURLBoundary() {
        XCTAssertNil(JudgeAccount(judge: .atcoder, handle: "tourist.user"))
    }

    func testPersistedAccountValidationRejectsUnsafeHandle() {
        let payload = Data(#"{"judge":"codeforces","handle":"tourist&handles=other","enabled":true}"#.utf8)

        XCTAssertThrowsError(try JSONDecoder().decode(JudgeAccount.self, from: payload))
    }

    func testJudgeHandleMatchingUsesJudgeSpecificCanonicalRules() {
        XCTAssertTrue(judgeHandlesMatch(.codeforces, " Tourist ", "tourist"))
        XCTAssertFalse(judgeHandlesMatch(.atcoder, " Tourist ", "tourist"))
        XCTAssertTrue(judgeHandlesMatch(.luogu, " UID:2 ", "2"))
        XCTAssertFalse(judgeHandlesMatch(.luogu, "uid:2", "uid:3"))
    }

    func testLedgerRecoversInterruptedOperationsAsCancelled() {
        let started = Date(timeIntervalSince1970: 100)
        var ledger = SyncLedger()
        let account = try! XCTUnwrap(JudgeAccount(judge: .codeforces, handle: "tourist"))
        let id = ledger.open(account: account, generation: "apple-0.1", at: started)

        ledger.recoverInterrupted(at: Date(timeIntervalSince1970: 120))

        XCTAssertEqual(ledger.operation(id: id)?.status, .cancelled)
        XCTAssertEqual(ledger.operation(id: id)?.error, .cancelled)
        XCTAssertEqual(ledger.operation(id: id)?.finishedAt, Date(timeIntervalSince1970: 120))
    }

    func testLedgerRetainsOnlyTheNewestCompletedOperations() throws {
        let account = try XCTUnwrap(JudgeAccount(judge: .codeforces, handle: "tourist"))
        var ledger = SyncLedger()

        for index in 0..<45 {
            let id = ledger.open(account: account, generation: "generation-\(index)",
                                 at: Date(timeIntervalSince1970: TimeInterval(index)))
            ledger.close(id: id, status: .success, at: Date(timeIntervalSince1970: TimeInterval(index + 1)))
        }

        XCTAssertEqual(ledger.operations.count, SyncLedger.retentionLimit)
        XCTAssertEqual(ledger.operations.first?.generation, "generation-44")
        XCTAssertEqual(ledger.operations.last?.generation, "generation-5")
    }

    func testSyncOperationSummarizesModuleFailures() throws {
        let account = try XCTUnwrap(JudgeAccount(judge: .codeforces, handle: "tourist"))
        let operation = SyncOperation(
            account: account,
            generation: "apple-0.1",
            startedAt: Date(timeIntervalSince1970: 1),
            status: .partial,
            modules: [
                SyncModuleOutcome(stage: "PROFILE", status: .success, imported: 1),
                SyncModuleOutcome(stage: "SUBMISSIONS", status: .offline, failure: .offline),
            ])

        XCTAssertEqual(operation.moduleSummary, "PROFILE:SUCCESS / SUBMISSIONS:OFFLINE (OFFLINE)")
    }

    func testLedgerStoreRoundTripsTheLocalLedger() async throws {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("ojnexus-core-tests", isDirectory: true)
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        let store = LocalLedgerStore(url: directory.appendingPathComponent("ledger.json"))
        let account = try XCTUnwrap(JudgeAccount(judge: .luogu, handle: "uid:2"))
        var ledger = SyncLedger()
        _ = ledger.open(account: account, generation: "apple-0.1", at: Date(timeIntervalSince1970: 10))

        try await store.save(ledger)
        let loaded = try await store.load()

        XCTAssertEqual(loaded, ledger)
    }

    func testDefaultLedgerLocationIsApplicationSupportScoped() {
        let url = LocalLedgerStore.defaultURL()

        XCTAssertEqual(url.lastPathComponent, "ledger.json")
        XCTAssertTrue(url.path.contains("OJ-NEXUS"))
    }

    func testWorkspaceStoreRoundTripsAccountsAndLedger() async throws {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("ojnexus-workspace-tests", isDirectory: true)
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        let store = LocalWorkspaceStore(url: directory.appendingPathComponent("state.json"))
        let account = try XCTUnwrap(JudgeAccount(judge: .atcoder, handle: "tourist"))
        var ledger = SyncLedger()
        _ = ledger.open(account: account, generation: "apple-0.1", at: Date(timeIntervalSince1970: 30))
        let state = LocalWorkspaceState(accounts: [account], ledger: ledger)

        try await store.save(state)
        let loaded = try await store.load()

        XCTAssertEqual(loaded, state)
    }

    func testWorkspaceStoreLoadsLegacyStateWithoutProfiles() async throws {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("ojnexus-legacy-state-tests", isDirectory: true)
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let url = directory.appendingPathComponent("state.json")
        let legacy = Data(#"{"accounts":[],"ledger":{"operations":[]}}"#.utf8)
        try legacy.write(to: url)

        let loaded = try await LocalWorkspaceStore(url: url).load()

        XCTAssertTrue(loaded.accounts.isEmpty)
        XCTAssertTrue(loaded.profiles.isEmpty)
    }

    func testWorkspaceStoreLoadsLegacyProfileWithoutDisplayName() async throws {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("ojnexus-legacy-profile-tests", isDirectory: true)
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let url = directory.appendingPathComponent("state.json")
        let legacy = Data(#"{"accounts":[],"ledger":{"operations":[]},"profiles":[{"judge":"codeforces","handle":"tourist","rating":3800}]}"#.utf8)
        try legacy.write(to: url)

        let loaded = try await LocalWorkspaceStore(url: url).load()

        XCTAssertNil(loaded.profiles.first?.displayName)
    }

    func testWorkspaceStoreLoadsLegacyStateWithoutRatingChanges() async throws {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("ojnexus-legacy-rating-tests", isDirectory: true)
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let url = directory.appendingPathComponent("state.json")
        let legacy = Data(#"{"accounts":[],"ledger":{"operations":[]},"profiles":[]}"#.utf8)
        try legacy.write(to: url)

        let loaded = try await LocalWorkspaceStore(url: url).load()

        XCTAssertTrue(loaded.ratingChanges.isEmpty)
    }

    func testWorkspaceStoreLoadsLegacyStateWithoutSubmissions() async throws {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("ojnexus-legacy-submission-tests", isDirectory: true)
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let url = directory.appendingPathComponent("state.json")
        let legacy = Data(#"{"accounts":[],"ledger":{"operations":[]},"profiles":[],"ratingChanges":[]}"#.utf8)
        try legacy.write(to: url)

        let loaded = try await LocalWorkspaceStore(url: url).load()

        XCTAssertTrue(loaded.submissions.isEmpty)
    }

    func testWorkspaceStoreRoundTripsRatingChanges() async throws {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("ojnexus-rating-store-tests", isDirectory: true)
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        let store = LocalWorkspaceStore(url: directory.appendingPathComponent("state.json"))
        let account = try XCTUnwrap(JudgeAccount(judge: .codeforces, handle: "tourist"))
        let change = try XCTUnwrap(RatingChange(
            judge: account.judge,
            handle: account.handle,
            contestID: "1",
            contestName: "TEST",
            oldRating: 3500,
            newRating: 3800,
            rank: 1,
            occurredAt: Date(timeIntervalSince1970: 100)))
        let state = LocalWorkspaceState(accounts: [account], ratingChanges: [change])

        try await store.save(state)
        let loaded = try await store.load()

        XCTAssertEqual(loaded.ratingChanges, [change])
    }

    func testWorkspaceStoreRoundTripsSubmissions() async throws {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("ojnexus-submission-store-tests", isDirectory: true)
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        let store = LocalWorkspaceStore(url: directory.appendingPathComponent("state.json"))
        let submission = try XCTUnwrap(SubmissionRecord(
            judge: .codeforces,
            handle: "tourist",
            externalID: "9",
            verdict: "OK",
            problemID: "1A",
            problemName: "TEST",
            language: "GNU C++20",
            submittedAt: Date(timeIntervalSince1970: 100)))
        let state = LocalWorkspaceState(submissions: [submission])

        try await store.save(state)
        let loaded = try await store.load()

        XCTAssertEqual(loaded.submissions, [submission])
    }

    func testWorkspaceStoreRejectsPersistedAccountWithUnsafeHandle() async throws {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("ojnexus-invalid-account-tests", isDirectory: true)
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let url = directory.appendingPathComponent("state.json")
        let invalidState = Data(#"{"accounts":[{"judge":"codeforces","handle":"tourist&handles=other","enabled":true}],"ledger":{"operations":[]},"profiles":[]}"#.utf8)
        try invalidState.write(to: url)

        do {
            _ = try await LocalWorkspaceStore(url: url).load()
            XCTFail("unsafe persisted account must be rejected")
        } catch is DecodingError {
            // Expected: persisted accounts must pass the same validation as new accounts.
        } catch {
            XCTFail("unexpected persistence error: \(error)")
        }
    }

    func testWorkspaceStoreRejectsPersistedProfileWithUnsafeHandle() async throws {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("ojnexus-invalid-profile-tests", isDirectory: true)
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let url = directory.appendingPathComponent("state.json")
        let invalidState = Data(#"{"accounts":[],"ledger":{"operations":[]},"profiles":[{"judge":"codeforces","handle":"tourist&handles=other","rating":3800}]}"#.utf8)
        try invalidState.write(to: url)

        do {
            _ = try await LocalWorkspaceStore(url: url).load()
            XCTFail("unsafe persisted profile must be rejected")
        } catch is DecodingError {
            // Expected: persisted profiles must pass the same validation as accounts.
        } catch {
            XCTFail("unexpected persistence error: \(error)")
        }
    }

    func testPersistedProfileValidationUsesJudgeSpecificRules() {
        let invalidProfiles = [
            Data(#"{"judge":"atcoder","handle":"tourist.user"}"#.utf8),
            Data(#"{"judge":"luogu","handle":"uid:2/other"}"#.utf8),
        ]

        for payload in invalidProfiles {
            XCTAssertThrowsError(try JSONDecoder().decode(PublicProfile.self, from: payload))
        }
    }

    func testPersistedProfileDecodingNormalizesHandleWhitespace() throws {
        let payload = Data(#"{"judge":"codeforces","handle":" tourist ","rating":3800}"#.utf8)

        let profile = try JSONDecoder().decode(PublicProfile.self, from: payload)

        XCTAssertEqual(profile.handle, "tourist")
    }

    func testPersistedRatingChangeValidationRejectsUnsafeIdentity() {
        let payload = Data(#"{"id":"bad","judge":"codeforces","handle":"tourist&handles=other","contestID":"1","newRating":3800}"#.utf8)

        XCTAssertThrowsError(try JSONDecoder().decode(RatingChange.self, from: payload))
    }

    func testPersistedSubmissionValidationRejectsUnsafeIdentity() {
        let payload = Data(#"{"id":"9","judge":"codeforces","handle":"tourist&handles=other","externalID":"9","verdict":"OK"}"#.utf8)

        XCTAssertThrowsError(try JSONDecoder().decode(SubmissionRecord.self, from: payload))
    }

    func testCodeforcesProfileDecoderMapsThePublicEnvelope() throws {
        let payload = Data(#"{"status":"OK","result":[{"handle":"tourist","firstName":"Petr","lastName":"Ermilov","rating":3800,"rank":"legendary"}]}"#.utf8)

        let profile = try CodeforcesAdapter().decodeProfile(payload, requestedHandle: "tourist")

        XCTAssertEqual(profile.judge, .codeforces)
        XCTAssertEqual(profile.handle, "tourist")
        XCTAssertEqual(profile.rating, 3800)
        XCTAssertEqual(profile.rank, "legendary")
        XCTAssertEqual(profile.displayName, "Petr Ermilov")
    }

    func testCodeforcesProfileDecoderRejectsADifferentProfile() {
        let payload = Data(#"{"status":"OK","result":[{"handle":"other","rating":3800}]}"#.utf8)

        XCTAssertThrowsError(try CodeforcesAdapter().decodeProfile(payload, requestedHandle: "tourist")) { error in
            XCTAssertEqual(error as? AdapterError, .apiFailure)
        }
    }

    func testCodeforcesProfileDecoderRejectsAProfileWithoutReturnedIdentity() {
        let payload = Data(#"{"status":"OK","result":[{"rating":3800}]}"#.utf8)

        XCTAssertThrowsError(try CodeforcesAdapter().decodeProfile(payload, requestedHandle: "tourist")) { error in
            XCTAssertEqual(error as? AdapterError, .apiFailure)
        }
    }

    func testCodeforcesProfileDecoderHidesNonOKApiDetails() {
        let payload = Data(#"{"status":"FAILED","comment":"private server detail"}"#.utf8)

        XCTAssertThrowsError(try CodeforcesAdapter().decodeProfile(payload, requestedHandle: "tourist")) { error in
            XCTAssertEqual(error as? AdapterError, .apiFailure)
        }
    }

    func testCodeforcesProfileURLUsesTheOfficialEndpoint() throws {
        let url = try CodeforcesAdapter().profileURL(for: "tourist")

        XCTAssertEqual(url.absoluteString, "https://codeforces.com/api/user.info?handles=tourist")
    }

    func testAtCoderProfileDecoderReadsTheOfficialTitle() throws {
        let html = Data("<html><title>tourist - AtCoder</title><script>var rank_history=[{\"Rating\":1800},{\"Rating\":246}];</script></html>".utf8)

        let profile = try AtCoderAdapter().decodeProfile(html, requestedHandle: "tourist")

        XCTAssertEqual(profile.judge, .atcoder)
        XCTAssertEqual(profile.handle, "tourist")
        XCTAssertEqual(profile.rating, 246)
    }

    func testCodeforcesRatingDecoderMapsOfficialHistory() throws {
        let payload = Data(#"{"status":"OK","result":[{"contestId":1,"contestName":"TEST","oldRating":3500,"newRating":3800,"rank":1,"ratingUpdateTimeSeconds":1700000000}]}"#.utf8)

        let changes = try CodeforcesAdapter().decodeRating(payload, requestedHandle: "tourist")

        XCTAssertEqual(changes.count, 1)
        XCTAssertEqual(changes.first?.judge, .codeforces)
        XCTAssertEqual(changes.first?.handle, "tourist")
        XCTAssertEqual(changes.first?.contestID, "1")
        XCTAssertEqual(changes.first?.newRating, 3800)
        XCTAssertEqual(changes.first?.occurredAt, Date(timeIntervalSince1970: 1700000000))
    }

    func testAtCoderRatingDecoderMapsRankHistory() throws {
        let html = Data("""
        <html><title>tourist - AtCoder</title><script>
        var rank_history=[{"ContestName":"ABC 1","ContestUrl":"https://atcoder.jp/contests/abc1","Rating":1200,"Rank":10},{"ContestName":"ABC 2","ContestUrl":"https://atcoder.jp/contests/abc2","Rating":1300,"Rank":5}];
        </script></html>
        """.utf8)

        let changes = try AtCoderAdapter().decodeRating(html, requestedHandle: "tourist")

        XCTAssertEqual(changes.count, 2)
        XCTAssertEqual(changes.first?.contestID, "abc1")
        XCTAssertEqual(changes.first?.newRating, 1200)
        XCTAssertNil(changes.first?.oldRating)
        XCTAssertEqual(changes.last?.contestID, "abc2")
        XCTAssertEqual(changes.last?.oldRating, 1200)
        XCTAssertEqual(changes.last?.newRating, 1300)
    }

    func testCodeforcesSubmissionDecoderMapsOfficialHistory() throws {
        let payload = Data(#"{"status":"OK","result":[{"id":9,"contestId":1,"creationTimeSeconds":1700000000,"verdict":"OK","programmingLanguage":"GNU C++20","problem":{"index":"A","name":"TEST"}}]}"#.utf8)

        let submissions = try CodeforcesAdapter().decodeSubmissions(payload, requestedHandle: "tourist")

        XCTAssertEqual(submissions.count, 1)
        XCTAssertEqual(submissions.first?.judge, .codeforces)
        XCTAssertEqual(submissions.first?.handle, "tourist")
        XCTAssertEqual(submissions.first?.externalID, "9")
        XCTAssertEqual(submissions.first?.id, "codeforces:tourist:9")
        XCTAssertEqual(submissions.first?.verdict, "OK")
        XCTAssertEqual(submissions.first?.problemID, "1A")
        XCTAssertEqual(submissions.first?.submittedAt, Date(timeIntervalSince1970: 1700000000))
    }

    func testAtCoderSubmissionDecoderMapsCommunityHistory() throws {
        let payload = Data("""
        [{"id":7,"contest_id":"abc001","problem_id":"abc001_a","epoch_second":1700000000,"result":"AC","language":"C++"}]
        """.utf8)

        let submissions = try AtCoderAdapter().decodeSubmissions(payload, requestedHandle: "tourist")

        XCTAssertEqual(submissions.count, 1)
        XCTAssertEqual(submissions.first?.externalID, "7")
        XCTAssertEqual(submissions.first?.verdict, "AC")
        XCTAssertEqual(submissions.first?.problemID, "abc001_a")
        XCTAssertEqual(submissions.first?.submittedAt, Date(timeIntervalSince1970: 1700000000))
    }

    func testLuoguRatingCapabilityRemainsUnsupported() throws {
        XCTAssertThrowsError(try LuoguAdapter().decodeRating(Data(), requestedHandle: "uid:2")) { error in
            XCTAssertEqual(error as? AdapterError, .unsupported)
        }
    }

    func testLuoguSubmissionCapabilityRemainsUnsupported() throws {
        XCTAssertThrowsError(try LuoguAdapter().decodeSubmissions(Data(), requestedHandle: "uid:2")) { error in
            XCTAssertEqual(error as? AdapterError, .unsupported)
        }
    }

    func testAtCoderProfileDecoderRejectsADifferentProfile() {
        let html = Data("<html><title>other - AtCoder</title></html>".utf8)

        XCTAssertThrowsError(try AtCoderAdapter().decodeProfile(html, requestedHandle: "tourist")) { error in
            XCTAssertEqual(error as? AdapterError, .apiFailure)
        }
    }

    func testAtCoderProfileDecoderRejectsCaseMismatchedProfile() {
        let html = Data("<html><title>Tourist - AtCoder</title></html>".utf8)

        XCTAssertThrowsError(try AtCoderAdapter().decodeProfile(html, requestedHandle: "tourist")) { error in
            XCTAssertEqual(error as? AdapterError, .apiFailure)
        }
    }

    func testAtCoderProfileURLRejectsPathDelimiters() {
        XCTAssertThrowsError(try AtCoderAdapter().profileURL(for: "tourist/other")) { error in
            XCTAssertEqual(error as? AdapterError, .invalidPayload)
        }
    }

    func testJudgeCatalogUsesAdapterDeclaredCapabilities() throws {
        XCTAssertEqual(JudgeCatalog.descriptors.map(\.judge), JudgeID.allCases)

        let codeforces = try XCTUnwrap(JudgeCatalog.descriptors.first { $0.judge == .codeforces })
        XCTAssertEqual(codeforces.reliability, "OFFICIAL")
        XCTAssertEqual(codeforces.capabilities, ["PROFILE", "RATING", "SUBMISSIONS"])

        let atcoder = try XCTUnwrap(JudgeCatalog.descriptors.first { $0.judge == .atcoder })
        XCTAssertTrue(atcoder.reliability.contains("EXPERIMENTAL"))
        XCTAssertEqual(atcoder.capabilities, ["PROFILE", "RATING", "SUBMISSIONS"])

        let luogu = try XCTUnwrap(JudgeCatalog.descriptors.first { $0.judge == .luogu })
        XCTAssertTrue(luogu.reliability.contains("PUBLIC_SITE"))
        XCTAssertEqual(luogu.capabilities, ["PROFILE"])
    }

    func testLuoguProfileDecoderReadsPublicUserInfo() throws {
        let payload = Data(#"{"user":{"uid":2,"name":"tourist","elo":2000,"ccfLevel":6}}"#.utf8)

        let profile = try LuoguAdapter().decodeProfile(payload, requestedHandle: "uid:2")

        XCTAssertEqual(profile.judge, .luogu)
        XCTAssertEqual(profile.handle, "uid:2")
        XCTAssertEqual(profile.rating, 2000)
        XCTAssertEqual(profile.displayName, "tourist")
    }

    func testLuoguProfileDecoderRejectsAMismatchedUID() {
        let payload = Data(#"{"user":{"uid":3,"name":"other","elo":2000}}"#.utf8)

        XCTAssertThrowsError(try LuoguAdapter().decodeProfile(payload, requestedHandle: "uid:2")) { error in
            XCTAssertEqual(error as? AdapterError, .apiFailure)
        }
    }

    func testLuoguProfileURLRejectsNonNumericUID() {
        XCTAssertThrowsError(try LuoguAdapter().profileURL(for: "uid:2/other")) { error in
            XCTAssertEqual(error as? AdapterError, .invalidPayload)
        }
    }

    func testProfileSyncServiceUsesTheMatchingAdapterAndTransport() async throws {
        let payload = Data(#"{"status":"OK","result":[{"handle":"tourist","rating":3800}]}"#.utf8)
        let client = RecordingHTTPClient(payload: payload)
        let account = try XCTUnwrap(JudgeAccount(judge: .codeforces, handle: "tourist"))

        let profile = try await ProfileSyncService().fetchProfile(
            for: account, adapter: CodeforcesAdapter(), client: client)

        XCTAssertEqual(profile.handle, "tourist")
        XCTAssertEqual(client.requestedURL?.absoluteString,
                       "https://codeforces.com/api/user.info?handles=tourist")
    }

    func testProfileSyncServiceRejectsAnAdapterWithoutProfileCapability() async throws {
        let client = RecordingHTTPClient(payload: Data())
        let account = try XCTUnwrap(JudgeAccount(judge: .codeforces, handle: "tourist"))

        do {
            _ = try await ProfileSyncService().fetchProfile(
                for: account, adapter: CatalogOnlyAdapter(), client: client)
            XCTFail("an adapter without PROFILE must be rejected")
        } catch let error as AdapterError {
            XCTAssertEqual(error, .unsupported)
        } catch {
            XCTFail("unexpected adapter error: \(error)")
        }

        XCTAssertNil(client.requestedURL)
    }

    func testProfileSyncServiceRecordsUnsupportedCapability() async throws {
        let client = RecordingHTTPClient(payload: Data())
        let account = try XCTUnwrap(JudgeAccount(judge: .codeforces, handle: "tourist"))

        let result = await ProfileSyncService().syncProfile(
            for: account,
            adapter: CatalogOnlyAdapter(),
            client: client,
            ledger: SyncLedger(),
            generation: "apple-0.1",
            startedAt: Date(timeIntervalSince1970: 100),
            finishedAt: Date(timeIntervalSince1970: 110))

        XCTAssertEqual(result.operation.status, .error)
        XCTAssertEqual(result.operation.error, .unsupported)
        XCTAssertEqual(result.operation.modules.first?.failure, .unsupported)
        XCTAssertNil(client.requestedURL)
    }

    func testProfileSyncServiceClosesSuccessfulLedgerOperation() async throws {
        let payload = Data(#"{"status":"OK","result":[{"handle":"tourist","rating":3800}]}"#.utf8)
        let client = RecordingHTTPClient(payload: payload)
        let account = try XCTUnwrap(JudgeAccount(judge: .codeforces, handle: "tourist"))

        let result = await ProfileSyncService().syncProfile(
            for: account,
            adapter: CodeforcesAdapter(),
            client: client,
            ledger: SyncLedger(),
            generation: "apple-0.1",
            startedAt: Date(timeIntervalSince1970: 100),
            finishedAt: Date(timeIntervalSince1970: 110))

        XCTAssertEqual(result.profile?.handle, "tourist")
        XCTAssertEqual(result.profile?.fetchedAt, Date(timeIntervalSince1970: 110))
        XCTAssertEqual(result.operation.status, .success)
        XCTAssertNil(result.operation.error)
        XCTAssertEqual(result.operation.modules.first?.stage, "PROFILE")
        XCTAssertEqual(result.operation.modules.first?.status, .success)
        XCTAssertEqual(result.operation.finishedAt, Date(timeIntervalSince1970: 110))
    }

    func testProfileSyncServiceUsesCompletionTimeWhenNotProvided() async throws {
        let payload = Data(#"{"status":"OK","result":[{"handle":"tourist","rating":3800}]}"#.utf8)
        let client = DelayedHTTPClient(payload: payload, delayNanoseconds: 50_000_000)
        let account = try XCTUnwrap(JudgeAccount(judge: .codeforces, handle: "tourist"))
        let startedAt = Date(timeIntervalSince1970: 100)

        let result = await ProfileSyncService().syncProfile(
            for: account,
            adapter: CodeforcesAdapter(),
            client: client,
            ledger: SyncLedger(),
            generation: "apple-0.1",
            startedAt: startedAt)

        let finishedAt = try XCTUnwrap(result.operation.finishedAt)
        let returnedAt = try XCTUnwrap(client.returnedAt)
        XCTAssertGreaterThanOrEqual(finishedAt, returnedAt)
    }

    func testProfileSyncServicePersistsTypedNetworkFailure() async throws {
        let client = RecordingHTTPClient(payload: Data(), error: .network)
        let account = try XCTUnwrap(JudgeAccount(judge: .atcoder, handle: "tourist"))

        let result = await ProfileSyncService().syncProfile(
            for: account,
            adapter: AtCoderAdapter(),
            client: client,
            ledger: SyncLedger(),
            generation: "apple-0.1",
            startedAt: Date(timeIntervalSince1970: 100),
            finishedAt: Date(timeIntervalSince1970: 110))

        XCTAssertNil(result.profile)
        XCTAssertEqual(result.operation.status, .error)
        XCTAssertEqual(result.operation.error, .network)
        XCTAssertEqual(result.operation.modules.first?.failure, .network)
    }

    func testProfileSyncServicePersistsTypedOfflineFailure() async throws {
        let client = RecordingHTTPClient(payload: Data(), error: .offline)
        let account = try XCTUnwrap(JudgeAccount(judge: .atcoder, handle: "tourist"))

        let result = await ProfileSyncService().syncProfile(
            for: account,
            adapter: AtCoderAdapter(),
            client: client,
            ledger: SyncLedger(),
            generation: "apple-0.1",
            startedAt: Date(timeIntervalSince1970: 100),
            finishedAt: Date(timeIntervalSince1970: 110))

        XCTAssertNil(result.profile)
        XCTAssertEqual(result.operation.status, .offline)
        XCTAssertEqual(result.operation.error, .offline)
        XCTAssertEqual(result.operation.modules.first?.status, .offline)
        XCTAssertEqual(result.operation.modules.first?.failure, .offline)
    }

    func testProfileSyncServicePersistsTypedAuthenticationFailure() async throws {
        let client = RecordingHTTPClient(payload: Data(), error: .authentication)
        let account = try XCTUnwrap(JudgeAccount(judge: .luogu, handle: "uid:2"))

        let result = await ProfileSyncService().syncProfile(
            for: account,
            adapter: LuoguAdapter(),
            client: client,
            ledger: SyncLedger(),
            generation: "apple-0.1",
            startedAt: Date(timeIntervalSince1970: 100),
            finishedAt: Date(timeIntervalSince1970: 110))

        XCTAssertNil(result.profile)
        XCTAssertEqual(result.operation.status, .error)
        XCTAssertEqual(result.operation.error, .authentication)
        XCTAssertEqual(result.operation.modules.first?.failure, .authentication)
    }

    func testRatingSyncServicePersistsOfficialHistoryAndLedgerOutcome() async throws {
        let payload = Data(#"{"status":"OK","result":[{"contestId":1,"contestName":"TEST","oldRating":3500,"newRating":3800,"rank":1,"ratingUpdateTimeSeconds":1700000000}]}"#.utf8)
        let client = RecordingHTTPClient(payload: payload)
        let account = try XCTUnwrap(JudgeAccount(judge: .codeforces, handle: "tourist"))

        let result = await RatingSyncService().syncRating(
            for: account,
            adapter: CodeforcesAdapter(),
            client: client,
            ledger: SyncLedger(),
            generation: "apple-0.1",
            startedAt: Date(timeIntervalSince1970: 100),
            finishedAt: Date(timeIntervalSince1970: 110))

        XCTAssertEqual(result.changes.count, 1)
        XCTAssertEqual(result.operation.status, .success)
        XCTAssertEqual(result.operation.modules.first?.stage, "RATING")
        XCTAssertEqual(result.operation.modules.first?.imported, 1)
        XCTAssertEqual(client.requestedURL?.absoluteString,
                       "https://codeforces.com/api/user.rating?handle=tourist")
    }

    func testRatingSyncServiceRejectsUnsupportedJudgeCapabilityBeforeNetwork() async throws {
        let client = RecordingHTTPClient(payload: Data())
        let account = try XCTUnwrap(JudgeAccount(judge: .luogu, handle: "uid:2"))

        let result = await RatingSyncService().syncRating(
            for: account,
            adapter: LuoguAdapter(),
            client: client,
            ledger: SyncLedger(),
            generation: "apple-0.1",
            startedAt: Date(timeIntervalSince1970: 100),
            finishedAt: Date(timeIntervalSince1970: 110))

        XCTAssertEqual(result.changes, [])
        XCTAssertEqual(result.operation.status, .error)
        XCTAssertEqual(result.operation.error, .unsupported)
        XCTAssertNil(client.requestedURL)
    }

    func testSubmissionSyncServicePersistsOfficialHistoryAndLedgerOutcome() async throws {
        let payload = Data(#"{"status":"OK","result":[{"id":9,"contestId":1,"creationTimeSeconds":1700000000,"verdict":"OK","programmingLanguage":"GNU C++20","problem":{"index":"A","name":"TEST"}}]}"#.utf8)
        let client = RecordingHTTPClient(payload: payload)
        let account = try XCTUnwrap(JudgeAccount(judge: .codeforces, handle: "tourist"))

        let result = await SubmissionSyncService().syncSubmissions(
            for: account,
            adapter: CodeforcesAdapter(),
            client: client,
            ledger: SyncLedger(),
            generation: "apple-0.1",
            startedAt: Date(timeIntervalSince1970: 100),
            finishedAt: Date(timeIntervalSince1970: 110))

        XCTAssertEqual(result.submissions.count, 1)
        XCTAssertEqual(result.operation.status, .success)
        XCTAssertEqual(result.operation.modules.first?.stage, "SUBMISSIONS")
        XCTAssertEqual(result.operation.modules.first?.imported, 1)
        XCTAssertEqual(client.requestedURL?.absoluteString,
                       "https://codeforces.com/api/user.status?handle=tourist&from=1&count=1000")
    }

    func testSubmissionSyncServiceRejectsUnsupportedJudgeCapabilityBeforeNetwork() async throws {
        let client = RecordingHTTPClient(payload: Data())
        let account = try XCTUnwrap(JudgeAccount(judge: .luogu, handle: "uid:2"))

        let result = await SubmissionSyncService().syncSubmissions(
            for: account,
            adapter: LuoguAdapter(),
            client: client,
            ledger: SyncLedger(),
            generation: "apple-0.1",
            startedAt: Date(timeIntervalSince1970: 100),
            finishedAt: Date(timeIntervalSince1970: 110))

        XCTAssertEqual(result.submissions, [])
        XCTAssertEqual(result.operation.status, .error)
        XCTAssertEqual(result.operation.error, .unsupported)
        XCTAssertNil(client.requestedURL)
    }

    func testURLSessionClientMapsUnauthorizedStatusToAuthentication() async {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [StatusURLProtocol.self]
        let client = URLSessionHTTPClient(session: URLSession(configuration: configuration))

        for statusCode in [401, 403] {
            let url = URL(string: "https://example.invalid/status/\(statusCode)")!
            do {
                _ = try await client.get(url)
                XCTFail("HTTP \(statusCode) must fail")
            } catch let error as AdapterError {
                XCTAssertEqual(error, .authentication)
            } catch {
                XCTFail("HTTP \(statusCode) returned unexpected error: \(error)")
            }
        }
    }

    func testDefaultHTTPClientConfigurationUsesBoundedTimeouts() {
        let configuration = URLSessionHTTPClient.defaultSessionConfiguration()

        XCTAssertEqual(configuration.timeoutIntervalForRequest, 30)
        XCTAssertEqual(configuration.timeoutIntervalForResource, 30)
    }

    func testDefaultHTTPClientConfigurationDisablesCookieHandling() {
        let configuration = URLSessionHTTPClient.defaultSessionConfiguration()

        XCTAssertFalse(configuration.httpShouldSetCookies)
        XCTAssertNil(configuration.httpCookieStorage)
    }

    func testURLSessionClientSendsExplicitPublicRequestHeaders() async throws {
        HeaderURLProtocol.lastRequest = nil
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [HeaderURLProtocol.self]
        let client = URLSessionHTTPClient(session: URLSession(configuration: configuration))

        _ = try await client.get(URL(string: "https://example.invalid/public")!)

        let request = try XCTUnwrap(HeaderURLProtocol.lastRequest)
        XCTAssertEqual(request.value(forHTTPHeaderField: "Accept"), "application/json, text/html;q=0.9")
        XCTAssertEqual(request.value(forHTTPHeaderField: "User-Agent"), "OJ-NEXUS/0.1")
    }

    func testURLSessionClientDisablesCookieHandlingForPublicRequest() async throws {
        HeaderURLProtocol.lastRequest = nil
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [HeaderURLProtocol.self]
        let client = URLSessionHTTPClient(session: URLSession(configuration: configuration))

        _ = try await client.get(URL(string: "https://example.invalid/public")!)

        let request = try XCTUnwrap(HeaderURLProtocol.lastRequest)
        XCTAssertFalse(request.httpShouldHandleCookies)
    }

    @MainActor
    func testDashboardModelSyncUpdatesProfileStatusAndLedger() async throws {
        let payload = Data(#"{"status":"OK","result":[{"handle":"tourist","rating":3800}]}"#.utf8)
        let client = RecordingHTTPClient(payload: payload)
        let account = try XCTUnwrap(JudgeAccount(judge: .codeforces, handle: "tourist"))
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("ojnexus-model-tests", isDirectory: true)
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        let store = LocalWorkspaceStore(url: directory.appendingPathComponent("state.json"))
        let model = NexusDashboardModel(accounts: [account], workspaceStore: store, httpClient: client)

        await model.syncProfile(for: account)

        XCTAssertEqual(model.syncStatus, .success)
        XCTAssertEqual(model.lastProfile?.handle, "tourist")
        XCTAssertEqual(model.ledger.operations.count, 1)
        XCTAssertEqual(model.ledger.operations.first?.status, .success)
        let profile = try XCTUnwrap(model.lastProfile)
        let saved = try await store.load()
        XCTAssertEqual(saved.profiles, [profile])
    }

    @MainActor
    func testDashboardModelSyncsAndProjectsRatingHistory() async throws {
        let payload = Data(#"{"status":"OK","result":[{"contestId":1,"contestName":"TEST","oldRating":3500,"newRating":3800,"rank":1,"ratingUpdateTimeSeconds":1700000000}]}"#.utf8)
        let client = RecordingHTTPClient(payload: payload)
        let account = try XCTUnwrap(JudgeAccount(judge: .codeforces, handle: "tourist"))
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("ojnexus-model-rating-tests", isDirectory: true)
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        let store = LocalWorkspaceStore(url: directory.appendingPathComponent("state.json"))
        let model = NexusDashboardModel(accounts: [account], workspaceStore: store, httpClient: client)

        await model.syncRating(for: account)

        XCTAssertEqual(model.syncStatus, .success)
        XCTAssertEqual(model.ratingChanges.count, 1)
        XCTAssertEqual(model.latestRating(for: account)?.newRating, 3800)
        XCTAssertEqual(model.ledger.operations.first?.modules.first?.stage, "RATING")
        let saved = try await store.load()
        XCTAssertEqual(saved.ratingChanges.count, 1)
    }

    @MainActor
    func testDashboardModelKeepsCachedRatingWhenRefreshFails() async throws {
        let payload = Data(#"{"status":"OK","result":[{"contestId":1,"contestName":"TEST","oldRating":3500,"newRating":3800,"rank":1,"ratingUpdateTimeSeconds":1700000000}]}"#.utf8)
        let client = SequencedHTTPClient(results: [.success(payload), .failure(.network)])
        let account = try XCTUnwrap(JudgeAccount(judge: .codeforces, handle: "tourist"))
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("ojnexus-model-rating-failure-tests", isDirectory: true)
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        let store = LocalWorkspaceStore(url: directory.appendingPathComponent("state.json"))
        let model = NexusDashboardModel(accounts: [account], workspaceStore: store, httpClient: client)

        await model.syncRating(for: account)
        await model.syncRating(for: account)

        XCTAssertEqual(model.syncStatus, .error)
        XCTAssertEqual(model.ratingChanges.count, 1)
        XCTAssertEqual(model.latestRating(for: account)?.newRating, 3800)
    }

    @MainActor
    func testDashboardModelSyncsAndProjectsSubmissions() async throws {
        let payload = Data(#"{"status":"OK","result":[{"id":9,"contestId":1,"creationTimeSeconds":1700000000,"verdict":"OK","programmingLanguage":"GNU C++20","problem":{"index":"A","name":"TEST"}}]}"#.utf8)
        let client = RecordingHTTPClient(payload: payload)
        let account = try XCTUnwrap(JudgeAccount(judge: .codeforces, handle: "tourist"))
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("ojnexus-model-submission-tests", isDirectory: true)
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        let store = LocalWorkspaceStore(url: directory.appendingPathComponent("state.json"))
        let model = NexusDashboardModel(accounts: [account], workspaceStore: store, httpClient: client)

        await model.syncSubmissions(for: account)

        XCTAssertEqual(model.syncStatus, .success)
        XCTAssertEqual(model.submissions.count, 1)
        XCTAssertEqual(model.latestSubmission(for: account)?.verdict, "OK")
        XCTAssertEqual(model.ledger.operations.first?.modules.first?.stage, "SUBMISSIONS")
        let saved = try await store.load()
        XCTAssertEqual(saved.submissions.count, 1)
    }

    @MainActor
    func testDashboardModelKeepsCachedSubmissionsWhenRefreshFails() async throws {
        let payload = Data(#"{"status":"OK","result":[{"id":9,"contestId":1,"creationTimeSeconds":1700000000,"verdict":"OK","programmingLanguage":"GNU C++20","problem":{"index":"A","name":"TEST"}}]}"#.utf8)
        let client = SequencedHTTPClient(results: [.success(payload), .failure(.network)])
        let account = try XCTUnwrap(JudgeAccount(judge: .codeforces, handle: "tourist"))
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("ojnexus-model-submission-failure-tests", isDirectory: true)
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        let store = LocalWorkspaceStore(url: directory.appendingPathComponent("state.json"))
        let model = NexusDashboardModel(accounts: [account], workspaceStore: store, httpClient: client)

        await model.syncSubmissions(for: account)
        await model.syncSubmissions(for: account)

        XCTAssertEqual(model.syncStatus, .error)
        XCTAssertEqual(model.submissions.count, 1)
        XCTAssertEqual(model.latestSubmission(for: account)?.verdict, "OK")
    }

    @MainActor
    func testDashboardModelProjectsBoundedRecentSubmissionHistory() throws {
        let account = try XCTUnwrap(JudgeAccount(judge: .codeforces, handle: "tourist"))
        let submissions = try (1...21).map { index in
            try XCTUnwrap(SubmissionRecord(
                judge: .codeforces,
                handle: "tourist",
                externalID: String(index),
                verdict: index.isMultiple(of: 2) ? "WA" : "OK",
                problemID: "\(index)A",
                submittedAt: Date(timeIntervalSince1970: TimeInterval(index))))
        }
        let model = NexusDashboardModel(accounts: [account], submissions: submissions)

        let recent = model.recentSubmissions(for: account)

        XCTAssertEqual(recent.count, 20)
        XCTAssertEqual(recent.first?.externalID, "21")
        XCTAssertEqual(recent.last?.externalID, "2")
        XCTAssertTrue(model.recentSubmissions(for: account, limit: 0).isEmpty)
    }

    @MainActor
    func testDashboardModelLooksUpCachedProfileByAccountIdentity() throws {
        let codeforces = try XCTUnwrap(JudgeAccount(judge: .codeforces, handle: "tourist"))
        let atcoder = try XCTUnwrap(JudgeAccount(judge: .atcoder, handle: "tourist"))
        let staleCodeforcesProfile = PublicProfile(judge: .codeforces, handle: "old-user", rating: 3800)
        let atcoderProfile = PublicProfile(judge: .atcoder, handle: "tourist", rating: 1800)
        let model = NexusDashboardModel(
            accounts: [codeforces, atcoder], profiles: [staleCodeforcesProfile, atcoderProfile])

        XCTAssertEqual(model.cachedProfile(for: atcoder), atcoderProfile)
        XCTAssertNil(model.cachedProfile(for: codeforces))
    }

    @MainActor
    func testDashboardModelMatchesCodeforcesCanonicalHandleCasing() throws {
        let account = try XCTUnwrap(JudgeAccount(judge: .codeforces, handle: "Tourist"))
        let profile = PublicProfile(judge: .codeforces, handle: "tourist", rating: 3800)
        let model = NexusDashboardModel(accounts: [account], profiles: [profile])

        XCTAssertEqual(model.cachedProfile(for: account), profile)
        XCTAssertEqual(model.lastProfile, profile)
    }

    @MainActor
    func testDashboardModelMatchesEquivalentLuoguUIDForms() throws {
        let account = try XCTUnwrap(JudgeAccount(judge: .luogu, handle: "uid:2"))
        let profile = PublicProfile(judge: .luogu, handle: "2", rating: 2000)
        let model = NexusDashboardModel(accounts: [account], profiles: [profile])

        XCTAssertEqual(model.cachedProfile(for: account), profile)
        XCTAssertEqual(model.lastProfile, profile)
    }

    @MainActor
    func testDashboardModelLooksUpLatestOperationByJudgeAwareAccountIdentity() throws {
        let codeforces = try XCTUnwrap(JudgeAccount(judge: .codeforces, handle: "tourist"))
        let atcoder = try XCTUnwrap(JudgeAccount(judge: .atcoder, handle: "tourist"))
        let oldCodeforces = SyncOperation(
            account: codeforces, generation: "apple-0.1",
            startedAt: Date(timeIntervalSince1970: 1), status: .error, error: .network)
        let latestCodeforces = SyncOperation(
            account: codeforces, generation: "apple-0.1",
            startedAt: Date(timeIntervalSince1970: 2), status: .success)
        let atcoderOperation = SyncOperation(
            account: atcoder, generation: "apple-0.1",
            startedAt: Date(timeIntervalSince1970: 3), status: .offline, error: .offline)
        let model = NexusDashboardModel(
            accounts: [codeforces, atcoder],
            ledger: SyncLedger(operations: [latestCodeforces, oldCodeforces, atcoderOperation]))

        XCTAssertEqual(model.lastOperation(for: codeforces), latestCodeforces)
        XCTAssertEqual(model.lastOperation(for: atcoder), atcoderOperation)
        let changedHandle = try XCTUnwrap(JudgeAccount(judge: .codeforces, handle: "new-user"))
        XCTAssertNil(model.lastOperation(for: changedHandle))
    }

    @MainActor
    func testDashboardModelDoesNotExposeStaleLastProfile() throws {
        let account = try XCTUnwrap(JudgeAccount(judge: .codeforces, handle: "tourist"))
        let staleProfile = PublicProfile(judge: .codeforces, handle: "old-user", rating: 3800)

        let model = NexusDashboardModel(accounts: [account], profiles: [staleProfile])

        XCTAssertNil(model.lastProfile)
        XCTAssertEqual(model.profiles, [staleProfile])
    }

    @MainActor
    func testDashboardModelProvidesCurrentLocalStateMessage() async {
        let model = NexusDashboardModel()

        XCTAssertEqual(model.localStateMessage, "READY / LOCAL STATE")

        await model.configurePublicAccount(judge: .atcoder, handle: "tourist/user")

        XCTAssertEqual(model.localStateMessage, "HANDLE REQUIRED")
    }

    @MainActor
    func testDashboardModelRollsBackInMemorySyncWhenWorkspaceSaveFails() async throws {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("ojnexus-model-save-failure-tests", isDirectory: true)
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let blocker = directory.appendingPathComponent("not-a-directory")
        try Data().write(to: blocker)
        let store = LocalWorkspaceStore(url: blocker.appendingPathComponent("state.json"))
        let payload = Data(#"{"status":"OK","result":[{"handle":"tourist","rating":3800}]}"#.utf8)
        let client = RecordingHTTPClient(payload: payload)
        let account = try XCTUnwrap(JudgeAccount(judge: .codeforces, handle: "tourist"))
        let model = NexusDashboardModel(accounts: [account], workspaceStore: store, httpClient: client)

        await model.syncProfile(for: account)

        XCTAssertEqual(model.syncStatus, .error)
        XCTAssertEqual(model.loadError, "SYNC SAVE FAILED")
        XCTAssertTrue(model.profiles.isEmpty)
        XCTAssertNil(model.lastProfile)
        XCTAssertTrue(model.ledger.operations.isEmpty)
    }

    @MainActor
    func testDashboardModelConfiguresAndPersistsPublicAccount() async throws {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("ojnexus-model-config-tests", isDirectory: true)
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        let url = directory.appendingPathComponent("state.json")
        let store = LocalWorkspaceStore(url: url)
        let model = NexusDashboardModel(workspaceStore: store)

        await model.configurePublicAccount(judge: .luogu, handle: " uid:2 ")

        XCTAssertEqual(model.accounts, [try XCTUnwrap(JudgeAccount(judge: .luogu, handle: "uid:2"))])
        let saved = try await store.load()
        XCTAssertEqual(saved.accounts, model.accounts)
    }

    @MainActor
    func testDashboardModelPreservesDisabledStateWhenReconfiguringEquivalentHandle() async throws {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("ojnexus-model-equivalent-config-tests", isDirectory: true)
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        let store = LocalWorkspaceStore(url: directory.appendingPathComponent("state.json"))
        let disabled = try XCTUnwrap(JudgeAccount(judge: .codeforces, handle: "Tourist", enabled: false))
        let model = NexusDashboardModel(accounts: [disabled], workspaceStore: store)

        await model.configurePublicAccount(judge: .codeforces, handle: " tourist ")

        XCTAssertEqual(model.accounts.first?.enabled, false)
        XCTAssertEqual(model.accounts.first?.handle, "Tourist")
        let reloaded = try await store.load()
        XCTAssertEqual(reloaded.accounts.first?.enabled, false)
        XCTAssertEqual(reloaded.accounts.first?.handle, "Tourist")
    }

    @MainActor
    func testDashboardModelClearsStaleProfileWhenHandleChanges() async throws {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("ojnexus-model-handle-change-tests", isDirectory: true)
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        let store = LocalWorkspaceStore(url: directory.appendingPathComponent("state.json"))
        let oldAccount = try XCTUnwrap(JudgeAccount(judge: .codeforces, handle: "tourist"))
        let oldProfile = PublicProfile(judge: .codeforces, handle: "tourist", rating: 3800)
        let oldRating = try XCTUnwrap(RatingChange(
            judge: .codeforces, handle: "tourist", contestID: "1", newRating: 3800))
        let oldSubmission = try XCTUnwrap(SubmissionRecord(
            judge: .codeforces, handle: "tourist", externalID: "9", verdict: "OK"))
        let model = NexusDashboardModel(
            accounts: [oldAccount], profiles: [oldProfile], ratingChanges: [oldRating],
            submissions: [oldSubmission], workspaceStore: store)

        await model.configurePublicAccount(judge: .codeforces, handle: "new-user")

        XCTAssertEqual(model.accounts.first?.handle, "new-user")
        XCTAssertTrue(model.profiles.isEmpty)
        XCTAssertTrue(model.ratingChanges.isEmpty)
        XCTAssertTrue(model.submissions.isEmpty)
        XCTAssertNil(model.lastProfile)
        let saved = try await store.load()
        XCTAssertTrue(saved.profiles.isEmpty)
        XCTAssertTrue(saved.ratingChanges.isEmpty)
        XCTAssertTrue(saved.submissions.isEmpty)
    }

    @MainActor
    func testDashboardModelCanDisableAndRemovePublicAccount() async throws {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("ojnexus-model-account-lifecycle-tests", isDirectory: true)
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        let store = LocalWorkspaceStore(url: directory.appendingPathComponent("state.json"))
        let account = try XCTUnwrap(JudgeAccount(judge: .atcoder, handle: "tourist"))
        let profile = PublicProfile(judge: .atcoder, handle: "tourist", rating: 246)
        let operation = SyncOperation(account: account, generation: "apple-0.1",
                                      startedAt: Date(timeIntervalSince1970: 1), status: .success)
        let model = NexusDashboardModel(accounts: [account],
                                        ledger: SyncLedger(operations: [operation]),
                                        profiles: [profile],
                                        workspaceStore: store)

        await model.setPublicAccountEnabled(judge: account.judge, enabled: false)

        XCTAssertEqual(model.accounts.first?.enabled, false)
        XCTAssertEqual(model.enabledAccountCount, 0)
        let disabledState = try await store.load()
        XCTAssertEqual(disabledState.accounts.first?.enabled, false)

        await model.removePublicAccount(judge: account.judge)

        XCTAssertTrue(model.accounts.isEmpty)
        XCTAssertNil(model.lastProfile)
        let removedState = try await store.load()
        XCTAssertTrue(removedState.accounts.isEmpty)
        XCTAssertEqual(removedState.profiles, [profile])
        XCTAssertEqual(removedState.ledger.operations, [operation])
    }

    @MainActor
    func testDashboardModelDoesNotSyncDisabledOrUnconfiguredAccount() async throws {
        let disabled = try XCTUnwrap(JudgeAccount(judge: .codeforces, handle: "tourist", enabled: false))
        let model = NexusDashboardModel(accounts: [disabled])

        model.retryProfileSync(for: disabled)

        XCTAssertFalse(model.isSyncing)
        XCTAssertEqual(model.syncStatus, .error)
        XCTAssertEqual(model.loadError, "ACCOUNT NOT ENABLED")
        XCTAssertTrue(model.ledger.operations.isEmpty)

        await model.syncProfile(for: disabled)

        XCTAssertFalse(model.isSyncing)
        XCTAssertEqual(model.syncStatus, .error)
        XCTAssertEqual(model.loadError, "ACCOUNT NOT ENABLED")
        XCTAssertTrue(model.ledger.operations.isEmpty)

        let differentHandle = try XCTUnwrap(JudgeAccount(judge: .codeforces, handle: "new-user"))
        model.retryProfileSync(for: differentHandle)

        XCTAssertFalse(model.isSyncing)
        XCTAssertEqual(model.syncStatus, .error)
        XCTAssertEqual(model.loadError, "ACCOUNT NOT CONFIGURED")
        XCTAssertTrue(model.ledger.operations.isEmpty)

        await model.syncProfile(for: differentHandle)

        XCTAssertFalse(model.isSyncing)
        XCTAssertEqual(model.syncStatus, .error)
        XCTAssertEqual(model.loadError, "ACCOUNT NOT CONFIGURED")
        XCTAssertTrue(model.ledger.operations.isEmpty)
    }

    @MainActor
    func testDashboardModelCancelsAnActiveProfileSync() async throws {
        let account = try XCTUnwrap(JudgeAccount(judge: .codeforces, handle: "tourist"))
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("ojnexus-model-cancel-tests", isDirectory: true)
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        let store = LocalWorkspaceStore(url: directory.appendingPathComponent("state.json"))
        let model = NexusDashboardModel(
            accounts: [account], workspaceStore: store, httpClient: BlockingHTTPClient())

        model.startProfileSync(for: account)
        for _ in 0..<100 where !model.isSyncing {
            try await Task.sleep(nanoseconds: 1_000_000)
        }
        XCTAssertTrue(model.isSyncing)

        model.cancelSync()
        for _ in 0..<200 where model.isSyncing {
            try await Task.sleep(nanoseconds: 10_000_000)
        }

        XCTAssertFalse(model.isSyncing)
        XCTAssertEqual(model.syncStatus, .cancelled)
        XCTAssertEqual(model.ledger.operations.first?.status, .cancelled)
    }

    @MainActor
    func testDashboardModelRetriesFailedProfileSync() async throws {
        let payload = Data(#"{"status":"OK","result":[{"handle":"tourist","rating":3800}]}"#.utf8)
        let client = SequencedHTTPClient(results: [.failure(.network), .success(payload)])
        let account = try XCTUnwrap(JudgeAccount(judge: .codeforces, handle: "tourist"))
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("ojnexus-model-retry-tests", isDirectory: true)
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        let store = LocalWorkspaceStore(url: directory.appendingPathComponent("state.json"))
        let model = NexusDashboardModel(
            accounts: [account], workspaceStore: store, httpClient: client)

        await model.syncProfile(for: account)
        XCTAssertEqual(model.syncStatus, .error)

        model.retryProfileSync(for: account)
        for _ in 0..<100 where !model.isSyncing {
            try await Task.sleep(nanoseconds: 1_000_000)
        }
        XCTAssertTrue(model.isSyncing)
        for _ in 0..<200 where model.isSyncing {
            try await Task.sleep(nanoseconds: 10_000_000)
        }

        XCTAssertFalse(model.isSyncing)
        XCTAssertEqual(model.syncStatus, .success)
        XCTAssertEqual(model.ledger.operations.count, 2)
    }

    @MainActor
    func testDashboardModelFiltersHistoryByJudge() throws {
        let codeforces = try XCTUnwrap(JudgeAccount(judge: .codeforces, handle: "tourist"))
        let atcoder = try XCTUnwrap(JudgeAccount(judge: .atcoder, handle: "tourist"))
        let operations = [
            SyncOperation(account: codeforces, generation: "apple-0.1", startedAt: Date(timeIntervalSince1970: 1), status: .success),
            SyncOperation(account: atcoder, generation: "apple-0.1", startedAt: Date(timeIntervalSince1970: 2), status: .error, error: .network),
        ]
        let model = NexusDashboardModel(ledger: SyncLedger(operations: operations))

        XCTAssertEqual(model.history(for: .atcoder).first?.account.judge, .atcoder)
        XCTAssertEqual(model.history(for: nil).count, 2)
    }

    @MainActor
    func testDashboardModelSyncsAllEnabledProfilesInOrder() async throws {
        let codeforcesPayload = Data(#"{"status":"OK","result":[{"handle":"tourist","rating":3800}]}"#.utf8)
        let atcoderPayload = Data("<html><title>tourist - AtCoder</title></html>".utf8)
        let client = SequencedHTTPClient(results: [.success(codeforcesPayload), .success(atcoderPayload)])
        let codeforces = try XCTUnwrap(JudgeAccount(judge: .codeforces, handle: "tourist"))
        let atcoder = try XCTUnwrap(JudgeAccount(judge: .atcoder, handle: "tourist"))
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("ojnexus-model-sync-all-tests", isDirectory: true)
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        let store = LocalWorkspaceStore(url: directory.appendingPathComponent("state.json"))
        let model = NexusDashboardModel(
            accounts: [codeforces, atcoder], workspaceStore: store, httpClient: client)

        model.startProfileSyncAll()
        for _ in 0..<100 where !model.isSyncing {
            try await Task.sleep(nanoseconds: 1_000_000)
        }
        XCTAssertTrue(model.isSyncing)
        for _ in 0..<200 where model.isSyncing {
            try await Task.sleep(nanoseconds: 10_000_000)
        }

        XCTAssertFalse(model.isSyncing)
        XCTAssertEqual(model.ledger.operations.count, 2)
        XCTAssertTrue(model.ledger.operations.allSatisfy { $0.status == .success })
    }

    @MainActor
    func testDashboardModelSyncAllRunsSupportedModulesInOrder() async throws {
        let profilePayload = Data(#"{"status":"OK","result":[{"handle":"tourist","rating":3800}]}"#.utf8)
        let ratingPayload = Data(#"{"status":"OK","result":[]}"#.utf8)
        let submissionsPayload = Data(#"{"status":"OK","result":[]}"#.utf8)
        let client = SequencedHTTPClient(results: [
            .success(profilePayload), .success(ratingPayload), .success(submissionsPayload)
        ])
        let account = try XCTUnwrap(JudgeAccount(judge: .codeforces, handle: "tourist"))
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("ojnexus-model-full-sync-tests", isDirectory: true)
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        let store = LocalWorkspaceStore(url: directory.appendingPathComponent("state.json"))
        let model = NexusDashboardModel(accounts: [account], workspaceStore: store, httpClient: client)

        model.startFullSyncAll()
        for _ in 0..<200 where model.isSyncing {
            try await Task.sleep(nanoseconds: 10_000_000)
        }

        XCTAssertFalse(model.isSyncing)
        XCTAssertEqual(model.ledger.operations.count, 3)
        XCTAssertEqual(model.ledger.operations.reversed().map { $0.modules.first?.stage },
                       ["PROFILE", "RATING", "SUBMISSIONS"])
        XCTAssertTrue(model.ledger.operations.allSatisfy { $0.status == .success })
    }

    @MainActor
    func testDashboardModelSyncAllSkipsUnsupportedModules() async throws {
        let profilePayload = Data(#"{"user":{"uid":2,"name":"test","elo":1200}}"#.utf8)
        let client = SequencedHTTPClient(results: [.success(profilePayload)])
        let account = try XCTUnwrap(JudgeAccount(judge: .luogu, handle: "uid:2"))
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("ojnexus-model-luogu-full-sync-tests", isDirectory: true)
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        let store = LocalWorkspaceStore(url: directory.appendingPathComponent("state.json"))
        let model = NexusDashboardModel(accounts: [account], workspaceStore: store, httpClient: client)

        model.startFullSyncAll()
        for _ in 0..<200 where model.isSyncing {
            try await Task.sleep(nanoseconds: 10_000_000)
        }

        XCTAssertFalse(model.isSyncing)
        XCTAssertEqual(model.ledger.operations.count, 1)
        XCTAssertEqual(model.ledger.operations.first?.modules.map(\.stage), ["PROFILE"])
        XCTAssertEqual(model.syncStatus, .success)
    }

    @MainActor
    func testDashboardModelFullSyncCancelsAtNextModuleAfterProfileSuccess() async throws {
        let profilePayload = Data(#"{"status":"OK","result":[{"handle":"tourist","rating":3800}]}"#.utf8)
        let client = FirstSuccessThenBlockingHTTPClient(payload: profilePayload)
        let account = try XCTUnwrap(JudgeAccount(judge: .codeforces, handle: "tourist"))
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("ojnexus-model-full-sync-cancel-tests", isDirectory: true)
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        let store = LocalWorkspaceStore(url: directory.appendingPathComponent("state.json"))
        let model = NexusDashboardModel(accounts: [account], workspaceStore: store, httpClient: client)

        model.startFullSyncAll()
        for _ in 0..<200 where client.requestCount < 2 {
            try await Task.sleep(nanoseconds: 10_000_000)
        }
        XCTAssertGreaterThanOrEqual(client.requestCount, 2)

        model.cancelSync()
        for _ in 0..<200 where model.isSyncing {
            try await Task.sleep(nanoseconds: 10_000_000)
        }

        XCTAssertFalse(model.isSyncing)
        XCTAssertEqual(model.syncStatus, .cancelled)
        XCTAssertEqual(model.ledger.operations.count, 2)
        XCTAssertEqual(model.ledger.operations.last?.status, .success)
        XCTAssertEqual(model.ledger.operations.first?.status, .cancelled)
    }

    @MainActor
    func testDashboardModelMarksBatchBusyImmediatelyAndClearsAfterCancellation() async throws {
        let account = try XCTUnwrap(JudgeAccount(judge: .codeforces, handle: "tourist"))
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("ojnexus-model-sync-busy-tests", isDirectory: true)
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        let store = LocalWorkspaceStore(url: directory.appendingPathComponent("state.json"))
        let model = NexusDashboardModel(
            accounts: [account], workspaceStore: store, httpClient: BlockingHTTPClient())

        model.startProfileSyncAll()

        XCTAssertTrue(model.isSyncing)
        model.cancelSync()
        for _ in 0..<200 where model.isSyncing {
            try await Task.sleep(nanoseconds: 10_000_000)
        }
        XCTAssertFalse(model.isSyncing)
    }

    @MainActor
    func testDashboardModelKeepsCancelledStatusAfterPartialBatchCancellation() async throws {
        let payload = Data(#"{"status":"OK","result":[{"handle":"tourist","rating":3800}]}"#.utf8)
        let client = FirstSuccessThenBlockingHTTPClient(payload: payload)
        let codeforces = try XCTUnwrap(JudgeAccount(judge: .codeforces, handle: "tourist"))
        let atcoder = try XCTUnwrap(JudgeAccount(judge: .atcoder, handle: "tourist"))
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("ojnexus-model-sync-cancelled-batch-tests", isDirectory: true)
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        let store = LocalWorkspaceStore(url: directory.appendingPathComponent("state.json"))
        let model = NexusDashboardModel(
            accounts: [codeforces, atcoder], workspaceStore: store, httpClient: client)

        model.startProfileSyncAll()
        for _ in 0..<200 where client.requestCount < 2 {
            try await Task.sleep(nanoseconds: 10_000_000)
        }
        XCTAssertGreaterThanOrEqual(client.requestCount, 2)

        model.cancelSync()
        for _ in 0..<200 where model.isSyncing {
            try await Task.sleep(nanoseconds: 10_000_000)
        }

        XCTAssertFalse(model.isSyncing)
        XCTAssertEqual(model.syncStatus, .cancelled)
        XCTAssertEqual(model.ledger.operations.count, 2)
        XCTAssertEqual(model.ledger.operations[0].status, .cancelled)
        XCTAssertEqual(model.ledger.operations[1].status, .success)
    }

    @MainActor
    func testDashboardModelSyncAllReportsPartialWhenOneProfileFails() async throws {
        let successPayload = Data(#"{"status":"OK","result":[{"handle":"tourist","rating":3800}]}"#.utf8)
        let client = SequencedHTTPClient(results: [.failure(.network), .success(successPayload)])
        let codeforces = try XCTUnwrap(JudgeAccount(judge: .codeforces, handle: "tourist"))
        let atcoder = try XCTUnwrap(JudgeAccount(judge: .atcoder, handle: "tourist"))
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("ojnexus-model-sync-all-partial-tests", isDirectory: true)
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        let store = LocalWorkspaceStore(url: directory.appendingPathComponent("state.json"))
        let model = NexusDashboardModel(
            accounts: [codeforces, atcoder], workspaceStore: store, httpClient: client)

        model.startProfileSyncAll()
        for _ in 0..<200 where model.ledger.operations.count < 2 {
            try await Task.sleep(nanoseconds: 10_000_000)
        }
        for _ in 0..<200 where model.isSyncing {
            try await Task.sleep(nanoseconds: 10_000_000)
        }

        XCTAssertEqual(model.syncStatus, .partial)
        XCTAssertEqual(model.ledger.operations.count, 2)
        XCTAssertEqual(model.ledger.operations[0].status, .success)
        XCTAssertEqual(model.ledger.operations[1].status, .error)
    }
}

private final class RecordingHTTPClient: HTTPClient, @unchecked Sendable {
    let payload: Data
    let error: AdapterError?
    private(set) var requestedURL: URL?

    init(payload: Data, error: AdapterError? = nil) {
        self.payload = payload
        self.error = error
    }

    func get(_ url: URL) async throws -> Data {
        requestedURL = url
        if let error { throw error }
        return payload
    }
}

private struct CatalogOnlyAdapter: JudgeAdapter, Sendable {
    let judge: JudgeID = .codeforces
    let reliability = "TEST"
    let capabilities = ["RATING"]

    func profileURL(for handle: String) throws -> URL {
        URL(string: "https://example.invalid/profile/\(handle)")!
    }

    func decodeProfile(_ data: Data, requestedHandle: String) throws -> PublicProfile {
        throw AdapterError.invalidPayload
    }
}

private final class StatusURLProtocol: URLProtocol {
    override class func canInit(with request: URLRequest) -> Bool {
        true
    }

    override class func canonicalRequest(for request: URLRequest) -> URLRequest {
        request
    }

    override func startLoading() {
        guard let url = request.url,
              let response = HTTPURLResponse(
                  url: url,
                  statusCode: Int(url.lastPathComponent) ?? 500,
                  httpVersion: nil,
                  headerFields: nil) else {
            client?.urlProtocol(self, didFailWithError: URLError(.badServerResponse))
            return
        }
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: Data())
        client?.urlProtocolDidFinishLoading(self)
    }

    override func stopLoading() {}
}

private final class HeaderURLProtocol: URLProtocol {
    static var lastRequest: URLRequest?

    override class func canInit(with request: URLRequest) -> Bool {
        true
    }

    override class func canonicalRequest(for request: URLRequest) -> URLRequest {
        request
    }

    override func startLoading() {
        Self.lastRequest = request
        guard let url = request.url,
              let response = HTTPURLResponse(
                  url: url,
                  statusCode: 200,
                  httpVersion: nil,
                  headerFields: nil) else {
            client?.urlProtocol(self, didFailWithError: URLError(.badServerResponse))
            return
        }
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: Data("ok".utf8))
        client?.urlProtocolDidFinishLoading(self)
    }

    override func stopLoading() {}
}

private final class DelayedHTTPClient: HTTPClient, @unchecked Sendable {
    let payload: Data
    let delayNanoseconds: UInt64
    private(set) var returnedAt: Date?

    init(payload: Data, delayNanoseconds: UInt64 = 0) {
        self.payload = payload
        self.delayNanoseconds = delayNanoseconds
    }

    func get(_ url: URL) async throws -> Data {
        try await Task.sleep(nanoseconds: delayNanoseconds)
        returnedAt = Date()
        return payload
    }
}

private struct BlockingHTTPClient: HTTPClient, Sendable {
    func get(_ url: URL) async throws -> Data {
        try await Task.sleep(nanoseconds: 60_000_000_000)
        return Data()
    }
}

private final class SequencedHTTPClient: HTTPClient, @unchecked Sendable {
    private var results: [Result<Data, AdapterError>]

    init(results: [Result<Data, AdapterError>]) {
        self.results = results
    }

    func get(_ url: URL) async throws -> Data {
        let result = results.removeFirst()
        return try result.get()
    }
}

private final class FirstSuccessThenBlockingHTTPClient: HTTPClient, @unchecked Sendable {
    let payload: Data
    private(set) var requestCount = 0

    init(payload: Data) {
        self.payload = payload
    }

    func get(_ url: URL) async throws -> Data {
        requestCount += 1
        if requestCount == 1 {
            return payload
        }
        try await Task.sleep(nanoseconds: 60_000_000_000)
        return Data()
    }
}
