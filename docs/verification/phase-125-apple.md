# Phase 125 — Apple build evidence and runtime gate

Status: **BLOCKED ON INTERACTIVE APPLE HOST** (2026-10-23). Core tests, macOS build
and generic iOS Simulator compilation have fresh remote evidence. Neither app has been
launched or operated by this verification lane, so Phase 125 is not VERIFIED.

## Exact remote evidence

[Apple clients run 39960430571](https://github.com/tangyixiao/OJ-NEXUS/actions/runs/39960430571)
completed successfully on 2026-10-23 at 13:22:06 UTC. Its checkout was
`8bca81b9c6d2fcd6c4d19637d9799c688404861d`, the Windows clock-repair branch. That branch
does not change `apple/` or the Apple workflow relative to main `1155509`; the run is
fresh validation of the existing Apple source, not an Apple implementation change.

| Gate | Recorded result | Boundary |
| --- | --- | --- |
| Toolchain | macOS-14 runner; Xcode 15.4; Apple Swift 5.10 | Remote runner identity, not this Windows host |
| Source boundary audit | Passed workflow grep for forbidden source/authentication patterns and trailing whitespace | A text audit does not establish runtime behavior |
| `swift test` | 89 tests, 0 failures, 0 unexpected; All tests passed | Domain/UI-model tests, no SwiftUI application interaction |
| `swift build --product OJNexusMacOS` | Build complete, exit 0 | Executable built, app launch unverified |
| `xcodebuild -scheme OJNexusIOS -destination 'generic/platform=iOS Simulator' build CODE_SIGNING_ALLOWED=NO` | BUILD SUCCEEDED | Generic target compilation; no booted simulator, app install, UI run or device signature |

The full downloaded job log is retained locally at
`%TEMP%/phase125-apple-ci-39960430571.log`. Do not add a second copy of the three repeated
89-test summary lines when counting tests. The workflow contains no launch or UI-test step.

## Host and source checks

On this Windows host, neither `swift` nor `xcodebuild` resolves as an installed command.
No interactive macOS host or booted iOS Simulator is attached to this task. The absence of
those tools is a host limitation, not a failing Swift build, and no compatibility workaround
was installed.

The current package targets macOS 13 and iOS 16 with native SwiftUI entry points under
`apple/Apps/`. The active `LocalWorkspaceStore` actor persists public-handle workspace data;
the source capability inventory is in [the Phase 121 ledger](platform-capabilities.md).
No credential, remote sync service, native package or signing change was made here.

## Required continuation

On an interactive macOS/Xcode host, record the checked-out commit, Xcode/Swift versions,
macOS version, exact iOS scheme and concrete simulator model/OS destination. Re-run G-P
if the source changes. Then launch both native clients and record separately:

- Dashboard, Connectors and Sync History navigation and the actual reachable controls.
- Public-handle configuration, cached profile/rating/submission display, explicit sync and
  cancellation/error state. Label any synthetic fixture and retain its provenance.
- Offline restart with previously stored cache, confirming the active workspace is preserved.
- Missing/disabled/removed identity behavior and typed failure/retry visibility.

Link the resulting evidence to the historical Phase 102/103 runtime debt without changing
those historical records into prior successes. macOS signing/notarization and iOS device
distribution remain separate gates even after simulator interaction passes. Until the
runtime evidence exists, continue independent Android, Windows and Linux work and keep
this phase BLOCKED.
