import OJNexusCore
import SwiftUI

@MainActor
public struct NexusRootView: View {
    @StateObject private var model: NexusDashboardModel
    @State private var newJudge: JudgeID = .codeforces
    @State private var newHandle = ""
    @State private var historyFilter = "ALL"
    @State private var expandedSubmissionAccountIDs = Set<String>()

    public init(model: NexusDashboardModel? = nil) {
        _model = StateObject(wrappedValue: model ?? NexusDashboardModel())
    }

    public var body: some View {
        NavigationSplitView {
            List(NexusPage.allCases, selection: $model.selectedPage) { page in
                Text(page.rawValue)
                    .font(.system(.caption, design: .monospaced).weight(.semibold))
                    .tag(page)
            }
            .navigationTitle("OJ NEXUS")
            .scrollContentBackground(.hidden)
            .background(NexusPalette.background)
        } detail: {
            detail
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(NexusPalette.background)
        }
        .tint(NexusPalette.blue)
        .preferredColorScheme(.dark)
        .task { await model.load() }
    }

    @ViewBuilder
    private var detail: some View {
        switch model.selectedPage ?? .dashboard {
        case .dashboard: dashboard
        case .connectors: connectors
        case .history: history
        }
    }

    private var dashboard: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: NexusLayout.rowSpacing) {
                header("LOCAL OPERATIONS")
                Text("\(model.ledger.operations.count)")
                    .font(.system(size: 42, weight: .bold, design: .monospaced))
                    .foregroundStyle(NexusPalette.primaryText)
                Text("LOCAL LEDGER IS THE SOURCE OF TRUTH. NETWORK SYNC REMAINS CANCELLABLE AND OFFLINE-SAFE.")
                    .font(.system(.callout, design: .monospaced))
                    .foregroundStyle(NexusPalette.mutedText)
                localStateBanner
                if model.isSyncing {
                    Button("CANCEL SYNC") { model.cancelSync() }
                        .buttonStyle(.bordered)
                }
                Button("SYNC ALL") { model.startFullSyncAll() }
                    .disabled(model.isSyncing || model.enabledAccountCount == 0)
                if let profile = model.lastProfile {
                    header("LAST PUBLIC PROFILE")
                    Text("\(profile.judge.displayName) / \(profile.handle)")
                        .font(.system(.body, design: .monospaced).weight(.semibold))
                        .foregroundStyle(NexusPalette.primaryText)
                    if let displayName = profile.displayName, displayName != profile.handle {
                        Text(displayName)
                            .font(.system(.caption, design: .monospaced))
                            .foregroundStyle(NexusPalette.mutedText)
                    }
                    HStack(spacing: NexusLayout.rowSpacing) {
                        if let rating = profile.rating {
                            Text("RATING \(rating)")
                        }
                        if let rank = profile.rank {
                            Text(rank.uppercased())
                        }
                    }
                    .font(.system(.caption, design: .monospaced))
                    .foregroundStyle(NexusPalette.mutedText)
                    if let fetchedAt = profile.fetchedAt {
                        Text("UPDATED \(fetchedAt.formatted(.iso8601))")
                            .font(.system(.caption2, design: .monospaced))
                            .foregroundStyle(NexusPalette.mutedText)
                    }
                }
                statRow(label: "ENABLED ACCOUNTS", value: "\(model.enabledAccountCount)")
                statRow(label: "LAST SIGNAL", value: model.syncStatus?.rawValue ?? model.ledger.operations.first?.status.rawValue ?? "NONE")
            }
            .frame(maxWidth: 720, alignment: .leading)
            .padding(NexusLayout.pagePadding)
        }
    }

    private var connectors: some View {
        List {
            if model.loadError != nil || model.isLoading {
                localStateBanner
            }

            Section("PUBLIC ACCOUNT") {
                Picker("JUDGE", selection: $newJudge) {
                    ForEach(JudgeID.allCases) { judge in
                        Text(judge.displayName).tag(judge)
                    }
                }
                TextField("HANDLE", text: $newHandle)
                Button("SAVE ACCOUNT") {
                    Task {
                        await model.configurePublicAccount(judge: newJudge, handle: newHandle)
                        newHandle = ""
                    }
                }
                .disabled(newHandle.isEmpty)
            }

            Section("CONFIGURED ACCOUNTS") {
                if model.accounts.isEmpty {
                    Text("NO PUBLIC ACCOUNTS")
                        .font(.system(.caption, design: .monospaced))
                        .foregroundStyle(NexusPalette.mutedText)
                } else {
                    ForEach(model.accounts) { account in
                        VStack(alignment: .leading, spacing: NexusLayout.rowSpacing) {
                            HStack {
                                Text(account.judge.displayName)
                                    .font(.system(.body, design: .monospaced).weight(.semibold))
                                Spacer()
                                Text(account.enabled ? "ENABLED" : "DISABLED")
                                    .font(.system(.caption2, design: .monospaced))
                                    .foregroundStyle(account.enabled ? NexusPalette.blue : NexusPalette.mutedText)
                            }
                            Text(account.handle)
                                .font(.system(.caption, design: .monospaced))
                                .foregroundStyle(NexusPalette.mutedText)
                            if let operation = model.lastOperation(for: account) {
                                HStack(spacing: NexusLayout.rowSpacing) {
                                    Text("LAST SYNC \(operation.status.rawValue)")
                                    if let error = operation.error {
                                        Text("ERROR \(error.rawValue)")
                                    }
                                    if let finishedAt = operation.finishedAt {
                                        Text(finishedAt.formatted(.iso8601))
                                    }
                                }
                                .font(.system(.caption2, design: .monospaced))
                                .foregroundStyle(NexusPalette.mutedText)
                            }
                            if let profile = model.cachedProfile(for: account) {
                                HStack(spacing: NexusLayout.rowSpacing) {
                                    if let rating = profile.rating {
                                        Text("RATING \(rating)")
                                    }
                                    if let rank = profile.rank {
                                        Text(rank.uppercased())
                                    }
                                    if let fetchedAt = profile.fetchedAt {
                                        Text("UPDATED \(fetchedAt.formatted(.iso8601))")
                                    }
                                }
                                .font(.system(.caption2, design: .monospaced))
                                .foregroundStyle(NexusPalette.mutedText)
                            }
                            if let rating = model.latestRating(for: account) {
                                VStack(alignment: .leading, spacing: NexusLayout.rowSpacing) {
                                    Text("RATING HISTORY \(model.ratingHistory(for: account).count)")
                                    Text("LATEST \(rating.newRating)")
                                    if let contestName = rating.contestName {
                                        Text("LAST CONTEST \(contestName)")
                                    }
                                }
                                .font(.system(.caption2, design: .monospaced))
                                .foregroundStyle(NexusPalette.mutedText)
                            }
                            if let submission = model.latestSubmission(for: account) {
                                VStack(alignment: .leading, spacing: NexusLayout.rowSpacing) {
                                    Text("SUBMISSIONS \(model.submissionHistory(for: account).count) / LATEST \(submission.verdict)")
                                    if let problemID = submission.problemID {
                                        Text("LAST PROBLEM \(problemID)")
                                    }
                                }
                                .font(.system(.caption2, design: .monospaced))
                                .foregroundStyle(NexusPalette.mutedText)

                                Button(expandedSubmissionAccountIDs.contains(account.id)
                                       ? "HIDE SUBMISSIONS"
                                       : "SHOW SUBMISSIONS") {
                                    if expandedSubmissionAccountIDs.contains(account.id) {
                                        expandedSubmissionAccountIDs.remove(account.id)
                                    } else {
                                        expandedSubmissionAccountIDs.insert(account.id)
                                    }
                                }
                                .buttonStyle(.plain)
                                .font(.system(.caption2, design: .monospaced).weight(.semibold))
                                .foregroundStyle(NexusPalette.blue)

                                if expandedSubmissionAccountIDs.contains(account.id) {
                                    VStack(alignment: .leading, spacing: NexusLayout.rowSpacing) {
                                        ForEach(model.recentSubmissions(for: account)) { submission in
                                            VStack(alignment: .leading, spacing: NexusLayout.rowSpacing) {
                                                HStack(spacing: NexusLayout.rowSpacing) {
                                                    Text(submission.verdict)
                                                    if let problemID = submission.problemID {
                                                        Text(problemID)
                                                    }
                                                    if let submittedAt = submission.submittedAt {
                                                        Text(submittedAt.formatted(.iso8601))
                                                    } else {
                                                        Text("TIME UNKNOWN")
                                                    }
                                                }
                                                if let problemName = submission.problemName {
                                                    Text(problemName)
                                                }
                                                if let language = submission.language {
                                                    Text(language)
                                                }
                                            }
                                            .font(.system(.caption2, design: .monospaced))
                                            .foregroundStyle(NexusPalette.mutedText)
                                            Rectangle()
                                                .fill(NexusPalette.rule)
                                                .frame(height: NexusLayout.ruleWidth)
                                        }
                                    }
                                }
                            }
                            HStack {
                                Spacer()
                                Button(account.enabled ? "DISABLE" : "ENABLE") {
                                    Task { await model.setPublicAccountEnabled(judge: account.judge, enabled: !account.enabled) }
                                }
                                .disabled(model.isSyncing)
                                Button(model.isSyncing ? "SYNC BUSY" : "SYNC") {
                                    model.startProfileSync(for: account)
                                }
                                .disabled(model.isSyncing || !account.enabled)
                                if supports(account, capability: "RATING") {
                                    Button(model.isSyncing ? "RATING BUSY" : "SYNC RATING") {
                                        model.startRatingSync(for: account)
                                    }
                                    .disabled(model.isSyncing || !account.enabled)
                                }
                                if supports(account, capability: "SUBMISSIONS") {
                                    Button(model.isSyncing ? "SUBMISSIONS BUSY" : "SYNC SUBMISSIONS") {
                                        model.startSubmissionSync(for: account)
                                    }
                                    .disabled(model.isSyncing || !account.enabled)
                                }
                                Button("REMOVE") {
                                    Task { await model.removePublicAccount(judge: account.judge) }
                                }
                                .disabled(model.isSyncing)
                            }
                        }
                    }
                }
            }

            Section("CAPABILITIES") {
                ForEach(JudgeCatalog.descriptors) { descriptor in
                    HStack {
                        Text(descriptor.judge.displayName)
                            .font(.system(.body, design: .monospaced).weight(.semibold))
                        Spacer()
                        Text(descriptor.summary)
                            .font(.system(.caption2, design: .monospaced))
                            .foregroundStyle(NexusPalette.mutedText)
                    }
                }
            }
        }
        .scrollContentBackground(.hidden)
        .background(NexusPalette.background)
    }

    private var history: some View {
        List {
            if model.loadError != nil || model.isLoading {
                localStateBanner
            }

            Picker("FILTER", selection: $historyFilter) {
                Text("ALL").tag("ALL")
                ForEach(JudgeID.allCases) { judge in
                    Text(judge.displayName).tag(judge.rawValue)
                }
            }
            .pickerStyle(.menu)

            let selectedJudge = JudgeID.parse(historyFilter)
            let operations = model.history(for: selectedJudge)
            if operations.isEmpty {
                Text("NO LOCAL OPERATIONS")
                    .font(.system(.caption, design: .monospaced))
                    .foregroundStyle(NexusPalette.mutedText)
            } else {
                ForEach(operations) { operation in
                    VStack(alignment: .leading, spacing: 4) {
                        HStack {
                            VStack(alignment: .leading, spacing: 4) {
                                Text("\(operation.account.judge.displayName) / \(operation.status.rawValue)")
                                    .font(.system(.body, design: .monospaced).weight(.semibold))
                                Text(operation.account.handle)
                                    .font(.system(.caption, design: .monospaced))
                                    .foregroundStyle(NexusPalette.mutedText)
                                if let error = operation.error {
                                    Text("ERROR / \(error.rawValue)")
                                        .font(.system(.caption2, design: .monospaced))
                                        .foregroundStyle(NexusPalette.mutedText)
                                }
                                Text(operation.moduleSummary)
                                    .font(.system(.caption2, design: .monospaced))
                                    .foregroundStyle(NexusPalette.mutedText)
                            }
                            Spacer()
                            if operation.status != .success && operation.error != .unsupported {
                                Button("RETRY") {
                                    model.retrySync(for: operation)
                                }
                                .disabled(model.isSyncing)
                            }
                        }
                    }
                }
            }
        }
        .scrollContentBackground(.hidden)
        .background(NexusPalette.background)
    }

    private func header(_ value: String) -> some View {
        Text(value)
            .font(.system(.caption, design: .monospaced).weight(.semibold))
            .foregroundStyle(NexusPalette.mutedText)
    }

    private var localStateBanner: some View {
        Text(model.localStateMessage)
            .font(.system(.caption, design: .monospaced).weight(.semibold))
            .foregroundStyle(model.loadError == nil ? NexusPalette.blue : NexusPalette.primaryText)
    }

    private func statRow(label: String, value: String) -> some View {
        HStack {
            Text(label)
                .font(.system(.caption, design: .monospaced))
                .foregroundStyle(NexusPalette.mutedText)
            Spacer()
            Text(value)
                .font(.system(.body, design: .monospaced).weight(.semibold))
                .foregroundStyle(NexusPalette.primaryText)
        }
        .padding(.vertical, 10)
        .overlay(alignment: .bottom) {
            Rectangle().fill(NexusPalette.rule).frame(height: NexusLayout.ruleWidth)
        }
    }

    private func supports(_ account: JudgeAccount, capability: String) -> Bool {
        JudgeCatalog.descriptors.first { $0.judge == account.judge }?.capabilities.contains(capability) == true
    }
}
