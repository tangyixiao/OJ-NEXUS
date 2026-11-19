import Combine
import Foundation
import OJNexusCore

public enum NexusPage: String, CaseIterable, Hashable, Identifiable, Sendable {
    case dashboard = "DASHBOARD"
    case connectors = "CONNECTORS"
    case history = "SYNC HISTORY"
    case portable = "PORTABLE"

    public var id: String { rawValue }
}

@MainActor
public final class NexusDashboardModel: ObservableObject {
    @Published public var selectedPage: NexusPage? = .dashboard
    @Published public private(set) var accounts: [JudgeAccount]
    @Published public private(set) var ledger: SyncLedger
    @Published public private(set) var profiles: [PublicProfile]
    @Published public private(set) var ratingChanges: [RatingChange]
    @Published public private(set) var submissions: [SubmissionRecord]
    @Published public private(set) var isLoading = false
    @Published public private(set) var loadError: String?
    @Published public private(set) var syncStatus: SyncStatus?
    @Published public private(set) var lastProfile: PublicProfile?
    @Published public private(set) var isSyncing = false
    @Published public private(set) var storageStatus: WorkspaceStorageStatus = .unloaded
    @Published public private(set) var isSaving = false
    @Published public private(set) var isRecovering = false
    // Portable exchange state (phase 135). Status lines are telemetry-style
    // text so state is never color-only.
    @Published public private(set) var portableStatus: String?
    @Published public private(set) var portableSummary: String?
    @Published public private(set) var isPortableBusy = false
    @Published public private(set) var hasStagedImport = false
    @Published public private(set) var stagedImportIdentity: String?
    private var stagedImportBytes: Data?
    private let workspaceStore: LocalWorkspaceStore
    private let httpClient: any HTTPClient
    private let syncService = ProfileSyncService()
    private let ratingSyncService = RatingSyncService()
    private let submissionSyncService = SubmissionSyncService()
    private var syncTask: Task<Void, Never>?
    private let portableArchiveStore: PortableArchiveStore
    private let portableRepository: PortableRepository

    public init(accounts: [JudgeAccount] = [], ledger: SyncLedger = SyncLedger(), profiles: [PublicProfile] = [],
                ratingChanges: [RatingChange] = [],
                submissions: [SubmissionRecord] = [],
                workspaceStore: LocalWorkspaceStore = LocalWorkspaceStore(),
                httpClient: any HTTPClient = URLSessionHTTPClient(),
                portableArchiveStore: PortableArchiveStore = PortableArchiveStore()) {
        self.accounts = accounts
        self.ledger = ledger
        self.profiles = profiles
        self.ratingChanges = ratingChanges
        self.submissions = submissions
        self.lastProfile = Self.configuredProfile(from: profiles, accounts: accounts)
        self.workspaceStore = workspaceStore
        self.httpClient = httpClient
        self.portableArchiveStore = portableArchiveStore
        self.portableRepository = PortableRepository(store: portableArchiveStore)
        self.ledger.recoverInterrupted(at: Date())
    }

    public var enabledAccountCount: Int {
        accounts.filter(\.enabled).count
    }

    public var localStateMessage: String {
        if isLoading { return "LOADING LOCAL STATE" }
        if let loadError { return loadError }
        switch storageStatus {
        case .unloaded: return "LOCAL STATE NOT LOADED"
        case .ready: return "READY / LOCAL STATE"
        case .blocked(let reason): return "LOCAL STATE BLOCKED / \(reason.rawValue)"
        case .recoverable(let reason): return "LOCAL STATE RECOVERY AVAILABLE / \(reason.rawValue)"
        }
    }

    public var canMutate: Bool {
        if case .ready = storageStatus { return !isLoading && !isSaving && !isRecovering && !isSyncing }
        return false
    }

    private var storageIsReady: Bool {
        if case .ready = storageStatus { return true }
        return false
    }

    public func cachedProfile(for account: JudgeAccount) -> PublicProfile? {
        profiles.first {
            $0.judge == account.judge && judgeHandlesMatch(account.judge, $0.handle, account.handle)
        }
    }

    public func ratingHistory(for account: JudgeAccount) -> [RatingChange] {
        ratingChanges
            .filter {
                $0.judge == account.judge && judgeHandlesMatch(account.judge, $0.handle, account.handle)
            }
            .sorted {
                let lhsDate = $0.occurredAt ?? .distantPast
                let rhsDate = $1.occurredAt ?? .distantPast
                if lhsDate != rhsDate { return lhsDate > rhsDate }
                return $0.id > $1.id
            }
    }

    public func latestRating(for account: JudgeAccount) -> RatingChange? {
        ratingHistory(for: account).first
    }

    public func submissionHistory(for account: JudgeAccount) -> [SubmissionRecord] {
        submissions
            .filter {
                $0.judge == account.judge && judgeHandlesMatch(account.judge, $0.handle, account.handle)
            }
            .sorted {
                let lhsDate = $0.submittedAt ?? .distantPast
                let rhsDate = $1.submittedAt ?? .distantPast
                if lhsDate != rhsDate { return lhsDate > rhsDate }
                return $0.id > $1.id
            }
    }

    public func latestSubmission(for account: JudgeAccount) -> SubmissionRecord? {
        submissionHistory(for: account).first
    }

    public func recentSubmissions(for account: JudgeAccount, limit: Int = 20) -> [SubmissionRecord] {
        guard limit > 0 else { return [] }
        return Array(submissionHistory(for: account).prefix(limit))
    }

    public func lastOperation(for account: JudgeAccount) -> SyncOperation? {
        ledger.operations.first {
            $0.account.judge == account.judge && judgeHandlesMatch(account.judge, $0.account.handle, account.handle)
        }
    }

    public func history(for judge: JudgeID?) -> [SyncOperation] {
        guard let judge else { return ledger.operations }
        return ledger.operations.filter { $0.account.judge == judge }
    }

    public func refresh() {
        guard !isSyncing else { return }
        ledger.recoverInterrupted(at: Date())
        objectWillChange.send()
    }

    public func load() async {
        guard !isSyncing, !isSaving, !isRecovering else { return }
        isLoading = true
        defer { isLoading = false }
        do {
            let state = try await workspaceStore.load()
            storageStatus = await workspaceStore.storageStatus()
            accounts = state.accounts
            ledger = state.ledger
            profiles = state.profiles
            ratingChanges = state.ratingChanges
            submissions = state.submissions
            lastProfile = Self.configuredProfile(from: profiles, accounts: accounts)
            ledger.recoverInterrupted(at: Date())
            loadError = nil
        } catch {
            storageStatus = await workspaceStore.storageStatus()
            loadError = "LOCAL STATE UNAVAILABLE / \(storageFailureLabel(storageStatus))"
        }
    }

    public func restorePreviousSnapshot() async {
        guard !isSyncing, !isSaving, !isRecovering else { return }
        isRecovering = true
        defer { isRecovering = false }
        do {
            let state = try await workspaceStore.restorePreviousSnapshot()
            storageStatus = await workspaceStore.storageStatus()
            accounts = state.accounts
            ledger = state.ledger
            profiles = state.profiles
            ratingChanges = state.ratingChanges
            submissions = state.submissions
            lastProfile = Self.configuredProfile(from: profiles, accounts: accounts)
            loadError = nil
        } catch {
            storageStatus = await workspaceStore.storageStatus()
            loadError = "LOCAL RESTORE FAILED / \(storageFailureLabel(storageStatus))"
        }
    }

    // MARK: portable exchange (phase 135)

    /// Reads a picked package through the security-scoped lifecycle and stages
    /// its frozen preview. Authorization cancel and unreadable files are typed
    /// states, never crashes.
    public func importPortablePackage(from url: URL) async {
        guard !isPortableBusy else { return }
        isPortableBusy = true
        defer { isPortableBusy = false }
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        let bytes: Data
        do {
            bytes = try Data(contentsOf: url)
        } catch {
            clearStagedImport()
            portableStatus = "PORTABLE IMPORT FAILED / FILE UNREADABLE"
            return
        }
        do {
            let preview = try await portableRepository.preview(bytes)
            stagedImportBytes = bytes
            hasStagedImport = true
            stagedImportIdentity = preview.identity
            portableSummary = Self.portableSummary(for: preview)
            portableStatus = "PORTABLE PREVIEW — CONFIRM TO APPLY / IDENTITY \(preview.identity.prefix(8))"
        } catch let failure as PortableContractError {
            clearStagedImport()
            portableStatus = "PORTABLE IMPORT REJECTED / \(failure.reject.wire)"
        } catch {
            clearStagedImport()
            portableStatus = "PORTABLE IMPORT FAILED / FILE UNREADABLE"
        }
    }

    /// Applies the staged package. The repository revalidates from the bytes
    /// (never trusting the preview) and writes only after the last checkpoint.
    public func confirmPortableApply() async {
        guard !isPortableBusy else { return }
        guard let bytes = stagedImportBytes else {
            portableStatus = "PORTABLE APPLY SKIPPED / NOTHING STAGED"
            return
        }
        isPortableBusy = true
        defer { isPortableBusy = false }
        do {
            let result = try await portableRepository.apply(bytes)
            clearStagedImport()
            portableSummary = nil
            if result.identical {
                portableStatus = "PORTABLE APPLIED / IDENTICAL PACKAGE — NO RECORDS WRITTEN"
            } else {
                portableStatus = "PORTABLE APPLIED / UP \(result.rowsUpserted) DEL \(result.rowsDeleted)"
            }
        } catch is PortableApplyCancelled {
            portableStatus = "PORTABLE APPLY CANCELLED — NO RECORDS WRITTEN"
        } catch let failure as PortableContractError {
            portableStatus = "PORTABLE IMPORT REJECTED / \(failure.reject.wire)"
        } catch {
            portableStatus = "PORTABLE APPLY FAILED / STORAGE ERROR"
        }
    }

    public func cancelStagedImport() async {
        guard !isPortableBusy else { return }
        await portableRepository.cancel()
        clearStagedImport()
        portableStatus = "PORTABLE IMPORT CANCELLED — NOTHING WRITTEN"
    }

    /// The picker dismissed without a usable authorization: a typed state.
    public func portableImportCancelled() {
        portableStatus = "PORTABLE IMPORT CANCELLED — NOTHING READ"
    }

    /// Builds the export payload for the file exporter; nil means the typed
    /// failure state is already set.
    public func preparePortableExport() async -> PortableExportPayload? {
        guard !isPortableBusy else { return nil }
        isPortableBusy = true
        defer { isPortableBusy = false }
        do {
            let bytes = try await portableRepository.export()
            portableStatus = "PORTABLE EXPORT READY"
            return PortableExportPayload(data: bytes)
        } catch let failure as PortableExportEmpty {
            portableStatus = failure.description
            return nil
        } catch {
            portableStatus = "PORTABLE EXPORT FAILED / STORAGE ERROR"
            return nil
        }
    }

    public func portableExportFinished(_ failureMessage: String?) {
        if let failureMessage {
            portableStatus = "PORTABLE EXPORT FAILED / \(failureMessage)"
        } else {
            portableStatus = "PORTABLE EXPORT WRITTEN"
        }
    }

    private func clearStagedImport() {
        stagedImportBytes = nil
        hasStagedImport = false
        stagedImportIdentity = nil
    }

    private static func portableSummary(for preview: PortablePreview) -> String {
        PortableSpec.collections.compactMap { key -> String? in
            guard let value = preview.counts[key] else { return nil }
            return "\(key)=\(value)"
        }
        .joined(separator: " ")
    }

    public func configurePublicAccount(judge: JudgeID, handle: String) async {
        guard canMutate else { return }
        let existingAccount = accounts.first { $0.judge == judge }
        guard let candidate = JudgeAccount(judge: judge, handle: handle, enabled: existingAccount?.enabled ?? true) else {
            loadError = "HANDLE REQUIRED"
            return
        }
        let account: JudgeAccount
        if let existingAccount, judgeHandlesMatch(judge, existingAccount.handle, candidate.handle) {
            account = existingAccount
        } else {
            account = candidate
        }
        let previousAccounts = accounts
        let previousProfiles = profiles
        let previousRatingChanges = ratingChanges
        let previousSubmissions = submissions
        let previousLastProfile = lastProfile
        let handleChanged = existingAccount.map { !judgeHandlesMatch(judge, $0.handle, account.handle) } ?? true
        accounts.removeAll { $0.judge == judge }
        accounts.append(account)
        if handleChanged {
            profiles.removeAll { $0.judge == judge }
            ratingChanges.removeAll { $0.judge == judge }
            submissions.removeAll { $0.judge == judge }
            lastProfile = Self.configuredProfile(from: profiles, accounts: accounts)
        }
        do {
            try await persistWorkspace()
            loadError = nil
        } catch {
            accounts = previousAccounts
            profiles = previousProfiles
            ratingChanges = previousRatingChanges
            submissions = previousSubmissions
            lastProfile = previousLastProfile
            loadError = "ACCOUNT SAVE FAILED"
        }
    }

    public func setPublicAccountEnabled(judge: JudgeID, enabled: Bool) async {
        guard canMutate else { return }
        guard let index = accounts.firstIndex(where: { $0.judge == judge }) else {
            loadError = "ACCOUNT NOT CONFIGURED"
            return
        }
        let previousAccounts = accounts
        accounts[index].enabled = enabled
        do {
            try await persistWorkspace()
            loadError = nil
        } catch {
            accounts = previousAccounts
            loadError = "ACCOUNT SAVE FAILED"
        }
    }

    public func removePublicAccount(judge: JudgeID) async {
        guard canMutate else { return }
        let previousAccounts = accounts
        let previousLastProfile = lastProfile
        accounts.removeAll { $0.judge == judge }
        lastProfile = Self.configuredProfile(from: profiles, accounts: accounts)
        do {
            try await persistWorkspace()
            loadError = nil
        } catch {
            accounts = previousAccounts
            lastProfile = previousLastProfile
            loadError = "ACCOUNT REMOVE FAILED"
        }
    }

    public func startProfileSync(for account: JudgeAccount) {
        guard !isSyncing, syncTask == nil else { return }
        guard canSync(account) else { return }
        isSyncing = true
        syncTask = Task { [weak self] in
            guard let self else { return }
            await self.performProfileSync(for: account)
            self.isSyncing = false
            self.syncTask = nil
        }
    }

    public func startProfileSyncAll() {
        guard !isSyncing, syncTask == nil else { return }
        guard canMutate else { return }
        let candidates = accounts.filter(\.enabled)
        guard !candidates.isEmpty else {
            syncStatus = .error
            loadError = "NO ENABLED ACCOUNTS"
            return
        }
        isSyncing = true
        syncTask = Task { [weak self] in
            guard let self else { return }
            var sawFailure = false
            var sawSuccess = false
            for account in candidates {
                if Task.isCancelled { break }
                await self.performProfileSync(for: account)
                guard self.storageIsReady else { break }
                if self.syncStatus == .success {
                    sawSuccess = true
                } else if self.syncStatus != .running {
                    sawFailure = true
                }
            }
            if Task.isCancelled {
                self.syncStatus = .cancelled
            } else if sawFailure && sawSuccess {
                self.syncStatus = .partial
            }
            self.isSyncing = false
            self.syncTask = nil
        }
    }

    public func startFullSyncAll() {
        guard !isSyncing, syncTask == nil else { return }
        guard canMutate else { return }
        let candidates = accounts.filter(\.enabled)
        guard !candidates.isEmpty else {
            syncStatus = .error
            loadError = "NO ENABLED ACCOUNTS"
            return
        }
        isSyncing = true
        syncTask = Task { [weak self] in
            guard let self else { return }
            var statuses: [SyncStatus] = []
            for account in candidates {
                if Task.isCancelled { break }
                statuses.append(await self.performFullSync(for: account))
                if !self.storageIsReady { break }
            }
            self.syncStatus = Self.aggregateBatchStatus(statuses, cancelled: Task.isCancelled)
            self.isSyncing = false
            self.syncTask = nil
        }
    }

    public func cancelSync() {
        syncTask?.cancel()
    }

    public func retryProfileSync(for account: JudgeAccount) {
        startProfileSync(for: account)
    }

    public func startRatingSync(for account: JudgeAccount) {
        guard !isSyncing, syncTask == nil else { return }
        guard canSync(account) else { return }
        isSyncing = true
        syncTask = Task { [weak self] in
            guard let self else { return }
            await self.performRatingSync(for: account)
            self.isSyncing = false
            self.syncTask = nil
        }
    }

    public func retryRatingSync(for account: JudgeAccount) {
        startRatingSync(for: account)
    }

    public func startSubmissionSync(for account: JudgeAccount) {
        guard !isSyncing, syncTask == nil else { return }
        guard canSync(account) else { return }
        isSyncing = true
        syncTask = Task { [weak self] in
            guard let self else { return }
            await self.performSubmissionSync(for: account)
            self.isSyncing = false
            self.syncTask = nil
        }
    }

    public func retrySubmissionSync(for account: JudgeAccount) {
        startSubmissionSync(for: account)
    }

    public func retrySync(for operation: SyncOperation) {
        if operation.modules.contains(where: { $0.stage == "SUBMISSIONS" }) {
            retrySubmissionSync(for: operation.account)
        } else if operation.modules.contains(where: { $0.stage == "RATING" }) {
            retryRatingSync(for: operation.account)
        } else {
            retryProfileSync(for: operation.account)
        }
    }

    public func syncProfile(for account: JudgeAccount) async {
        guard !isSyncing, canMutate else { return }
        guard canSync(account) else { return }
        isSyncing = true
        defer { isSyncing = false }
        await performProfileSync(for: account)
    }

    public func syncRating(for account: JudgeAccount) async {
        guard !isSyncing, canMutate else { return }
        guard canSync(account) else { return }
        isSyncing = true
        defer { isSyncing = false }
        await performRatingSync(for: account)
    }

    public func syncSubmissions(for account: JudgeAccount) async {
        guard !isSyncing, canMutate else { return }
        guard canSync(account) else { return }
        isSyncing = true
        defer { isSyncing = false }
        await performSubmissionSync(for: account)
    }

    private func performProfileSync(for account: JudgeAccount) async {
        guard let adapter = Self.adapter(for: account.judge) else {
            syncStatus = .error
            return
        }
        let previousLedger = ledger
        let previousProfiles = profiles
        let previousLastProfile = lastProfile
        syncStatus = .running
        let startedAt = Date()
        let result = await syncService.syncProfile(
            for: account,
            adapter: adapter,
            client: httpClient,
            ledger: ledger,
            generation: "apple-0.1",
            startedAt: startedAt)
        ledger = result.ledger
        if let profile = result.profile {
            profiles.removeAll { $0.judge == profile.judge }
            profiles.insert(profile, at: 0)
            lastProfile = profile
        }
        syncStatus = result.operation.status
        do {
            try await persistWorkspace()
            loadError = nil
        } catch {
            ledger = previousLedger
            profiles = previousProfiles
            lastProfile = previousLastProfile
            syncStatus = .error
            loadError = "SYNC SAVE FAILED"
        }
    }

    private func performRatingSync(for account: JudgeAccount) async {
        guard let adapter = Self.adapter(for: account.judge) else {
            syncStatus = .error
            return
        }
        let previousLedger = ledger
        let previousRatingChanges = ratingChanges
        syncStatus = .running
        let result = await ratingSyncService.syncRating(
            for: account,
            adapter: adapter,
            client: httpClient,
            ledger: ledger,
            generation: "apple-0.1",
            startedAt: Date())
        ledger = result.ledger
        if result.operation.status == .success {
            ratingChanges.removeAll {
                $0.judge == account.judge && judgeHandlesMatch(account.judge, $0.handle, account.handle)
            }
            ratingChanges.append(contentsOf: result.changes)
        }
        syncStatus = result.operation.status
        do {
            try await persistWorkspace()
            loadError = nil
        } catch {
            ledger = previousLedger
            ratingChanges = previousRatingChanges
            syncStatus = .error
            loadError = "SYNC SAVE FAILED"
        }
    }

    private func performSubmissionSync(for account: JudgeAccount) async {
        guard let adapter = Self.adapter(for: account.judge) else {
            syncStatus = .error
            return
        }
        let previousLedger = ledger
        let previousSubmissions = submissions
        syncStatus = .running
        let result = await submissionSyncService.syncSubmissions(
            for: account,
            adapter: adapter,
            client: httpClient,
            ledger: ledger,
            generation: "apple-0.1",
            startedAt: Date())
        ledger = result.ledger
        if result.operation.status == .success {
            submissions.removeAll {
                $0.judge == account.judge && judgeHandlesMatch(account.judge, $0.handle, account.handle)
            }
            submissions.append(contentsOf: result.submissions)
        }
        syncStatus = result.operation.status
        do {
            try await persistWorkspace()
            loadError = nil
        } catch {
            ledger = previousLedger
            submissions = previousSubmissions
            syncStatus = .error
            loadError = "SYNC SAVE FAILED"
        }
    }

    private func performFullSync(for account: JudgeAccount) async -> SyncStatus {
        var statuses: [SyncStatus] = []

        await performProfileSync(for: account)
        if let status = syncStatus { statuses.append(status) }
        guard storageIsReady else { return Self.aggregateBatchStatus(statuses, cancelled: false) }
        if Task.isCancelled || statuses.last == .cancelled {
            return .cancelled
        }

        guard let adapter = Self.adapter(for: account.judge) else {
            return Self.aggregateBatchStatus(statuses, cancelled: false)
        }
        if adapter.supports("RATING") {
            await performRatingSync(for: account)
            if let status = syncStatus { statuses.append(status) }
            guard storageIsReady else { return Self.aggregateBatchStatus(statuses, cancelled: false) }
            if Task.isCancelled || statuses.last == .cancelled {
                return .cancelled
            }
        }
        if adapter.supports("SUBMISSIONS") {
            await performSubmissionSync(for: account)
            if let status = syncStatus { statuses.append(status) }
            if Task.isCancelled || statuses.last == .cancelled {
                return .cancelled
            }
        }

        return Self.aggregateBatchStatus(statuses, cancelled: false)
    }

    private func canSync(_ account: JudgeAccount) -> Bool {
        guard canMutate else { return false }
        guard let configured = accounts.first(where: {
            $0.judge == account.judge && judgeHandlesMatch(account.judge, $0.handle, account.handle)
        }) else {
            syncStatus = .error
            loadError = "ACCOUNT NOT CONFIGURED"
            return false
        }
        guard configured.enabled else {
            syncStatus = .error
            loadError = "ACCOUNT NOT ENABLED"
            return false
        }
        return true
    }

    private func storageFailureLabel(_ status: WorkspaceStorageStatus) -> String {
        switch status {
        case .unloaded: return "NOT LOADED"
        case .ready: return "READY"
        case .blocked(let failure), .recoverable(let failure): return failure.rawValue
        }
    }

    private func persistWorkspace() async throws {
        isSaving = true
        defer { isSaving = false }
        do {
            try await workspaceStore.save(LocalWorkspaceState(
                accounts: accounts, ledger: ledger, profiles: profiles,
                ratingChanges: ratingChanges, submissions: submissions))
        } catch {
            storageStatus = await workspaceStore.storageStatus()
            throw error
        }
        storageStatus = await workspaceStore.storageStatus()
    }

    private static func adapter(for judge: JudgeID) -> (any JudgeAdapter)? {
        switch judge {
        case .codeforces: return CodeforcesAdapter()
        case .atcoder: return AtCoderAdapter()
        case .luogu: return LuoguAdapter()
        }
    }

    private static func aggregateBatchStatus(_ statuses: [SyncStatus], cancelled: Bool) -> SyncStatus {
        if cancelled || statuses.contains(.cancelled) { return .cancelled }
        guard !statuses.isEmpty else { return .error }
        if statuses.allSatisfy({ $0 == .success }) { return .success }
        if statuses.contains(.success) || statuses.contains(.partial) { return .partial }
        if statuses.allSatisfy({ $0 == .offline }) { return .offline }
        return statuses.first ?? .error
    }

    private static func configuredProfile(from profiles: [PublicProfile], accounts: [JudgeAccount]) -> PublicProfile? {
        profiles.first { profile in
            accounts.contains { account in
                account.judge == profile.judge && judgeHandlesMatch(profile.judge, account.handle, profile.handle)
            }
        }
    }

}
