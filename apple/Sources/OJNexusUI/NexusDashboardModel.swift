import Combine
import Foundation
import OJNexusCore

public enum NexusPage: String, CaseIterable, Hashable, Identifiable, Sendable {
    case dashboard = "DASHBOARD"
    case connectors = "CONNECTORS"
    case history = "SYNC HISTORY"

    public var id: String { rawValue }
}

@MainActor
public final class NexusDashboardModel: ObservableObject {
    @Published public var selectedPage: NexusPage? = .dashboard
    @Published public private(set) var accounts: [JudgeAccount]
    @Published public private(set) var ledger: SyncLedger
    @Published public private(set) var profiles: [PublicProfile]
    @Published public private(set) var isLoading = false
    @Published public private(set) var loadError: String?
    @Published public private(set) var syncStatus: SyncStatus?
    @Published public private(set) var lastProfile: PublicProfile?
    @Published public private(set) var isSyncing = false
    private let workspaceStore: LocalWorkspaceStore
    private let httpClient: any HTTPClient
    private let syncService = ProfileSyncService()
    private var syncTask: Task<Void, Never>?

    public init(accounts: [JudgeAccount] = [], ledger: SyncLedger = SyncLedger(), profiles: [PublicProfile] = [],
                workspaceStore: LocalWorkspaceStore = LocalWorkspaceStore(url: LocalWorkspaceStore.defaultURL()),
                httpClient: any HTTPClient = URLSessionHTTPClient()) {
        self.accounts = accounts
        self.ledger = ledger
        self.profiles = profiles
        self.lastProfile = Self.configuredProfile(from: profiles, accounts: accounts)
        self.workspaceStore = workspaceStore
        self.httpClient = httpClient
        self.ledger.recoverInterrupted(at: Date())
    }

    public var enabledAccountCount: Int {
        accounts.filter(\.enabled).count
    }

    public func cachedProfile(for account: JudgeAccount) -> PublicProfile? {
        profiles.first {
            $0.judge == account.judge && Self.sameHandle(account.judge, $0.handle, account.handle)
        }
    }

    public func lastOperation(for account: JudgeAccount) -> SyncOperation? {
        ledger.operations.first {
            $0.account.judge == account.judge && Self.sameHandle(account.judge, $0.account.handle, account.handle)
        }
    }

    public func history(for judge: JudgeID?) -> [SyncOperation] {
        guard let judge else { return ledger.operations }
        return ledger.operations.filter { $0.account.judge == judge }
    }

    public func refresh() {
        ledger.recoverInterrupted(at: Date())
        objectWillChange.send()
    }

    public func load() async {
        isLoading = true
        loadError = nil
        defer { isLoading = false }
        do {
            let state = try await workspaceStore.load()
            accounts = state.accounts
            ledger = state.ledger
            profiles = state.profiles
            lastProfile = Self.configuredProfile(from: profiles, accounts: accounts)
            ledger.recoverInterrupted(at: Date())
            try await workspaceStore.save(LocalWorkspaceState(accounts: accounts, ledger: ledger, profiles: profiles))
        } catch {
            loadError = "LOCAL LEDGER UNAVAILABLE"
        }
    }

    public func configurePublicAccount(judge: JudgeID, handle: String) async {
        guard let account = JudgeAccount(judge: judge, handle: handle) else {
            loadError = "HANDLE REQUIRED"
            return
        }
        let previousAccounts = accounts
        let previousProfiles = profiles
        let previousLastProfile = lastProfile
        let handleChanged = accounts.first(where: { $0.judge == judge })?.handle.map {
            !Self.sameHandle(judge, $0, account.handle)
        } ?? true
        accounts.removeAll { $0.judge == judge }
        accounts.append(account)
        if handleChanged {
            profiles.removeAll { $0.judge == judge }
            lastProfile = Self.configuredProfile(from: profiles, accounts: accounts)
        }
        do {
            try await workspaceStore.save(LocalWorkspaceState(accounts: accounts, ledger: ledger, profiles: profiles))
            loadError = nil
        } catch {
            accounts = previousAccounts
            profiles = previousProfiles
            lastProfile = previousLastProfile
            loadError = "ACCOUNT SAVE FAILED"
        }
    }

    public func setPublicAccountEnabled(judge: JudgeID, enabled: Bool) async {
        guard let index = accounts.firstIndex(where: { $0.judge == judge }) else {
            loadError = "ACCOUNT NOT CONFIGURED"
            return
        }
        let previousAccounts = accounts
        accounts[index].enabled = enabled
        do {
            try await workspaceStore.save(LocalWorkspaceState(accounts: accounts, ledger: ledger, profiles: profiles))
            loadError = nil
        } catch {
            accounts = previousAccounts
            loadError = "ACCOUNT SAVE FAILED"
        }
    }

    public func removePublicAccount(judge: JudgeID) async {
        let previousAccounts = accounts
        let previousLastProfile = lastProfile
        accounts.removeAll { $0.judge == judge }
        lastProfile = Self.configuredProfile(from: profiles, accounts: accounts)
        do {
            try await workspaceStore.save(LocalWorkspaceState(accounts: accounts, ledger: ledger, profiles: profiles))
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

    public func cancelSync() {
        syncTask?.cancel()
    }

    public func retryProfileSync(for account: JudgeAccount) {
        startProfileSync(for: account)
    }

    public func syncProfile(for account: JudgeAccount) async {
        guard !isSyncing else { return }
        guard canSync(account) else { return }
        isSyncing = true
        defer { isSyncing = false }
        await performProfileSync(for: account)
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
            try await workspaceStore.save(LocalWorkspaceState(accounts: accounts, ledger: ledger, profiles: profiles))
            loadError = nil
        } catch {
            ledger = previousLedger
            profiles = previousProfiles
            lastProfile = previousLastProfile
            syncStatus = .error
            loadError = "SYNC SAVE FAILED"
        }
    }

    private func canSync(_ account: JudgeAccount) -> Bool {
        guard let configured = accounts.first(where: {
            $0.judge == account.judge && Self.sameHandle(account.judge, $0.handle, account.handle)
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

    private static func adapter(for judge: JudgeID) -> (any JudgeAdapter)? {
        switch judge {
        case .codeforces: return CodeforcesAdapter()
        case .atcoder: return AtCoderAdapter()
        case .luogu: return LuoguAdapter()
        }
    }

    private static func configuredProfile(from profiles: [PublicProfile], accounts: [JudgeAccount]) -> PublicProfile? {
        profiles.first { profile in
            accounts.contains { account in
                account.judge == profile.judge && Self.sameHandle(profile.judge, account.handle, profile.handle)
            }
        }
    }

    private static func sameHandle(_ judge: JudgeID, _ lhs: String, _ rhs: String) -> Bool {
        switch judge {
        case .codeforces:
            return lhs.caseInsensitiveCompare(rhs) == .orderedSame
        case .atcoder:
            return lhs == rhs
        case .luogu:
            let normalized: (String) -> String = { value in
                let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
                return trimmed.lowercased().hasPrefix("uid:") ? String(trimmed.dropFirst(4)) : trimmed
            }
            return normalized(lhs) == normalized(rhs)
        }
    }
}
