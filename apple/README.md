# OJ NEXUS Apple clients

This directory contains the native Apple slice for OJ NEXUS:

- `Sources/OJNexusCore` is platform-independent Swift domain, local-workspace, and ledger code;
  accounts and up to 40 completed sync-history entries are stored together under Application Support.
- `Sources/OJNexusCore/*Adapter.swift` contains public-profile boundaries for Codeforces, AtCoder,
  and Luogu; transport and API failures stay typed and raw response details do not enter the
  domain model. The current sync slice is profile-only; additional modules remain capability-gated.
- `Sources/OJNexusUI` is the shared SwiftUI connection-center surface.
- `Apps/OJNexusMacOS` and `Apps/OJNexusIOS` are the two native app entry points.

The deployment floor is macOS 13 and iOS 16. The clients use SwiftUI and do not use a
WebView, Flutter, React Native, passwords, browser-cookie scraping, or fabricated remote data.
Luogu accounts accept only a numeric UID or the explicit `uid:<number>` form, matching the
Linux client boundary.
The public profile adapters are already routed behind a judge-agnostic boundary; additional
remote modules will be added only after their public source and failure boundaries are validated.
The history view retains failed or cancelled profile operations and exposes an explicit retry
action; no automatic retry is scheduled. Retry and direct sync requests are rejected when the
account is disabled, removed, or no longer matches the configured public handle.
The Dashboard also supports a serial `SYNC ALL` action for enabled public accounts, so one
cancel handle governs the whole batch.
Transport failures caused by a disconnected network are recorded as `OFFLINE`, while other
transport failures remain `NETWORK`; both remain retryable from local history.
Configured accounts can be disabled without losing their handle, or removed without deleting
the profile snapshots and sync history already stored locally.
CONNECTORS shows the latest local sync status for each configured judge and public handle. Status,
typed error, and completion time come from the local ledger and never expose raw transport details.
Successful profile snapshots carry their fetch completion time and the Dashboard renders it, so
local cached data is distinguishable from a live response.

## Validation

Run on a macOS host with Xcode or the Swift toolchain:

```sh
swift test
swift build --product OJNexusMacOS
```

The current Windows host has neither `swift` nor `xcodebuild`, so Apple compilation remains a
pending macOS/Xcode gate. The iOS executable target is source-complete for Xcode integration;
build it for a simulator or device from an iOS application target before claiming device
readiness.

GitHub Actions uses a macOS runner to execute the Core tests, build the macOS product, and
compile the iOS product for a generic iOS Simulator destination. That CI build verifies source,
package, and target-platform compatibility; it is not a substitute for an interactive simulator
launch or App Store signing check.
