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

    func testCodeforcesProfileDecoderMapsThePublicEnvelope() throws {
        let payload = Data(#"{"status":"OK","result":[{"handle":"tourist","firstName":"Petr","lastName":"Ermilov","rating":3800,"rank":"legendary"}]}"#.utf8)

        let profile = try CodeforcesAdapter().decodeProfile(payload, requestedHandle: "tourist")

        XCTAssertEqual(profile.judge, .codeforces)
        XCTAssertEqual(profile.handle, "tourist")
        XCTAssertEqual(profile.rating, 3800)
        XCTAssertEqual(profile.rank, "legendary")
        XCTAssertEqual(profile.displayName, "Petr Ermilov")
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

    func testAtCoderProfileDecoderRejectsADifferentProfile() {
        let html = Data("<html><title>other - AtCoder</title></html>".utf8)

        XCTAssertThrowsError(try AtCoderAdapter().decodeProfile(html, requestedHandle: "tourist")) { error in
            XCTAssertEqual(error as? AdapterError, .apiFailure)
        }
    }

    func testAtCoderProfileURLRejectsPathDelimiters() {
        XCTAssertThrowsError(try AtCoderAdapter().profileURL(for: "tourist/other")) { error in
            XCTAssertEqual(error as? AdapterError, .invalidPayload)
        }
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
    func testDashboardModelLooksUpLatestOperationByExactAccountIdentity() throws {
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
    func testDashboardModelClearsStaleProfileWhenHandleChanges() async throws {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("ojnexus-model-handle-change-tests", isDirectory: true)
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        let store = LocalWorkspaceStore(url: directory.appendingPathComponent("state.json"))
        let oldAccount = try XCTUnwrap(JudgeAccount(judge: .codeforces, handle: "tourist"))
        let oldProfile = PublicProfile(judge: .codeforces, handle: "tourist", rating: 3800)
        let model = NexusDashboardModel(accounts: [oldAccount], profiles: [oldProfile], workspaceStore: store)

        await model.configurePublicAccount(judge: .codeforces, handle: "new-user")

        XCTAssertEqual(model.accounts.first?.handle, "new-user")
        XCTAssertTrue(model.profiles.isEmpty)
        XCTAssertNil(model.lastProfile)
        XCTAssertTrue((try await store.load()).profiles.isEmpty)
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

private final class DelayedHTTPClient: HTTPClient, @unchecked Sendable {
    let payload: Data
    let delayNanoseconds: UInt64
    private(set) var returnedAt: Date?

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
