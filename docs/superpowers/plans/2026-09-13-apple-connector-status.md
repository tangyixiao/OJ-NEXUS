# Apple Connector Status Projection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Show each configured Apple account's most recent local sync result in the shared macOS/iOS CONNECTORS view without mixing accounts or adding remote/storage capabilities.

**Architecture:** Add a pure exact-identity query to `NexusDashboardModel` over the existing newest-first `SyncLedger.operations` array. The shared `NexusRootView` will render only the typed status, typed error, and optional completion time returned by that query; no view will perform network or persistence work.

**Tech Stack:** Swift 5.9, Swift Package Manager, SwiftUI, Foundation, XCTest, existing `OJNexusCore` and `OJNexusUI` targets.

**Spec:** `docs/superpowers/specs/2026-09-13-apple-connector-status-design.md`

## Global Constraints

- The feature only projects existing `SyncLedger` data.
- Account identity is the pair `JudgeAccount.judge` and `JudgeAccount.handle`.
- Do not add network requests, remote modules, database fields, credential fields, or background tasks.
- Display only `SyncStatus.rawValue`, `SyncError.rawValue`, and an existing `SyncOperation.finishedAt` value.
- Keep failed and cancelled history available; do not add automatic retry.
- The Apple deployment floors remain macOS 13 and iOS 16.
- When Swift/Xcode is unavailable on Windows, report Apple compilation as unverified rather than passing it by inference.
- Preserve the isolated Linux worktree and run its existing CTest suite as a regression gate.

---

### Task 1: Add exact-account ledger query tests

**Files:**
- Modify: `apple/Tests/OJNexusCoreTests/DomainTests.swift`

**Interfaces:**
- Consumes: `JudgeAccount`, `SyncOperation`, `SyncLedger`, and `NexusDashboardModel`.
- Produces: executable assertions for `NexusDashboardModel.lastOperation(for:)`.

- [ ] **Step 1: Write the failing test**

Add this `@MainActor` test near the existing dashboard-model query tests:

```swift
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
```

- [ ] **Step 2: Run the focused test and record the environment result**

Run from `D:\AndroidAppCoding`:

```powershell
swift test --package-path apple --filter DomainTests/testDashboardModelLooksUpLatestOperationByJudgeAwareAccountIdentity
```

On a macOS host this must fail to compile or fail the assertion because `lastOperation(for:)` is not yet implemented. On the current Windows host, the command is expected to stop earlier with `swift` not found; retain that as an environment limitation, not a test pass.

### Task 2: Implement the model projection

**Files:**
- Modify: `apple/Sources/OJNexusUI/NexusDashboardModel.swift`

**Interfaces:**
- Consumes: newest-first `ledger.operations` and `JudgeAccount` identity fields.
- Produces:

```swift
public func lastOperation(for account: JudgeAccount) -> SyncOperation?
```

- [ ] **Step 1: Add the minimal exact-match query**

Place the method beside `cachedProfile(for:)`:

```swift
public func lastOperation(for account: JudgeAccount) -> SyncOperation? {
    ledger.operations.first {
        $0.account.judge == account.judge && Self.sameHandle(account.judge, $0.account.handle, account.handle)
    }
}
```

Do not sort, mutate the ledger, or fall back to judge-only matching. Use the established judge-aware
handle identity: Codeforces is case-insensitive, while AtCoder and Luogu use their validated
identities. The ledger's `open` method inserts newest operations at index zero, so the first matching
operation is the required result.

- [ ] **Step 2: Run the focused test again**

```powershell
swift test --package-path apple --filter DomainTests/testDashboardModelLooksUpLatestOperationByJudgeAwareAccountIdentity
```

Expected on macOS: the new test passes. Expected on the current Windows host: the same missing-toolchain result remains and must be reported explicitly.

### Task 3: Render status in the shared CONNECTORS view

**Files:**
- Modify: `apple/Sources/OJNexusUI/NexusRootView.swift`

**Interfaces:**
- Consumes: `model.lastOperation(for:)` and the existing `SyncOperation` typed fields.
- Produces: a shared SwiftUI projection used by both `OJNexusMacOS` and `OJNexusIOS`.

- [ ] **Step 1: Add status projection below the account handle**

Inside each configured-account `VStack`, immediately after the handle text, add:

```swift
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
```

The `if let finishedAt` guard prevents a running operation from displaying a fabricated timestamp. Existing profile summary and account action controls remain unchanged.

- [ ] **Step 2: Audit the view for identity and sensitive-data regressions**

Verify the new row calls only `lastOperation(for: account)`, uses no raw error description or URL, and retains the existing explicit `RETRY` button. Check that long ISO timestamps remain in the existing vertical account layout rather than being forced into a fixed-width horizontal table.

### Task 4: Update Apple documentation and run acceptance gates

**Files:**
- Modify: `apple/README.md`

**Interfaces:**
- Consumes: the implemented connector-row behavior and current validation limitations.
- Produces: documentation stating that CONNECTORS shows the latest local status by exact account identity.

- [ ] **Step 1: Document the behavior**

Add one paragraph to the feature description:

```text
CONNECTORS shows the latest local sync status for each configured judge and public handle. Status, typed error, and completion time come from the local ledger and never expose raw transport details.
```

- [ ] **Step 2: Run Apple static audits on Windows**

```powershell
rg -n -i 'password|cookie|webview|react native|flutter|T[O]DO|FIXME|fatalError\(|api key|token' apple/Sources apple/Tests
rg -n '[ \t]+$' apple/Sources apple/Tests apple/Apps
git diff --check
```

The two `rg` commands must produce no matches; `git diff --check` may report only the repository's existing CRLF conversion warnings.

- [ ] **Step 3: Run Apple macOS gates when available**

```sh
cd apple
swift test
swift build --product OJNexusMacOS
xcodebuild -packagePath . -scheme OJNexusIOS -destination 'generic/platform=iOS Simulator' build CODE_SIGNING_ALLOWED=NO
```

Every command must succeed on a macOS/Xcode runner before claiming Apple build readiness. If run on this Windows host, record the missing toolchain instead.

- [ ] **Step 4: Re-run the unchanged Linux regression gate**

```powershell
wsl -d Ubuntu-24.04 -- bash -lc 'cd /mnt/d/AndroidAppCoding/.worktrees/linux-client/linux && cmake --build build --parallel 2 && ctest --test-dir build --output-on-failure'
```

Expected: build success and 9/9 tests passing. Do not stage or commit any Linux worktree files.

- [ ] **Step 5: Commit only the Apple implementation and documentation**

```powershell
git add -- apple/Sources/OJNexusUI/NexusDashboardModel.swift apple/Sources/OJNexusUI/NexusRootView.swift apple/Tests/OJNexusCoreTests/DomainTests.swift apple/README.md
git diff --cached --name-only
git commit -m "feat: show Apple connector sync status"
```

The staged file list must contain exactly the four listed paths. Do not include `.worktrees`, Windows build artifacts, or unrelated root changes.
