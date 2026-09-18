# OJ NEXUS Apple clients

This directory contains the native Apple slice for OJ NEXUS:

- `Sources/OJNexusCore` is platform-independent Swift domain, local-workspace, and ledger code;
  accounts, profiles, rating changes, submissions, and up to 40 completed sync-history entries are stored
  together under Application Support.
- `Sources/OJNexusCore/*Adapter.swift` contains public-profile, rating-history, and submission
  boundaries for Codeforces, AtCoder, and Luogu; transport and API failures stay typed and raw
  response details do not enter the domain model. Additional modules remain capability-gated.
- `Sources/OJNexusUI` is the shared SwiftUI connection-center surface.
- `Apps/OJNexusMacOS` and `Apps/OJNexusIOS` are the two native app entry points.

The deployment floor is macOS 13 and iOS 16. The clients use SwiftUI and do not use a
WebView, Flutter, React Native, passwords, browser-cookie scraping, or fabricated remote data.
Luogu accounts accept only a numeric UID or the explicit `uid:<number>` form, matching the
Linux client boundary.
Codeforces and AtCoder public handles are restricted to the safe identifier characters accepted
by their URL boundaries; query and path delimiters are rejected before any request is built.
All profile/cache/ledger identity checks use the shared `judgeHandlesMatch` rule: Codeforces is
case-insensitive, AtCoder is trimmed exact-match, and Luogu accepts numeric UID and `uid:<number>`
forms as equivalent.
The public profile adapters are already routed behind a judge-agnostic boundary. Codeforces and
AtCoder also expose a public `RATING` module: Codeforces uses the official `user.rating` endpoint,
while AtCoder reads the public `rank_history` page data. Luogu remains profile-only because no
stable public rating-history source is currently part of this client boundary.
Codeforces and AtCoder also expose public `SUBMISSIONS` history through their documented public
endpoints; Luogu remains unavailable for this module without relying on protected site records.
Profile, rating, and submission sync check the adapter's declared capability before constructing a request, so
capability rows and executable behavior cannot silently diverge. An unsupported capability is
recorded as `UNSUPPORTED` when explicitly requested, and the history view does not offer an
invalid retry action. `SYNC ALL` skips such modules entirely.
The CONNECTORS capability rows are derived from those adapter declarations, so the UI cannot
claim a remote module that the current Apple implementation does not provide.
Profile responses are accepted only when the returned public identity matches the requested
handle, with case-insensitive matching where the judge permits canonical casing.
The history view retains failed or cancelled profile, rating, and submission operations and exposes an explicit
retry action; no automatic retry is scheduled. Retry and direct sync requests are rejected when
the account is disabled, removed, or no longer matches the configured public handle.
The Dashboard `SYNC ALL` action serially runs PROFILE and every supported RATING/SUBMISSIONS
module for each enabled public account, so one cancel handle governs the whole batch; unsupported
judge capabilities remain skipped rather than becoming false failures.
Transport failures caused by a disconnected network are recorded as `OFFLINE`, while other
transport failures remain `NETWORK`; both remain retryable from local history.
HTTP 401/403 responses are recorded as `AUTHENTICATION`, preserving a typed status for the
protected endpoint instead of exposing raw transport details.
The default Apple transport uses an ephemeral URL session with a bounded 30-second request and
resource timeout, explicitly disables cookie handling, and identifies public requests with
explicit `Accept` and `User-Agent` headers; callers can inject a session for platform tests
without changing the domain.
Configured accounts can be disabled without losing their handle, or removed without deleting
the profile snapshots and sync history already stored locally.
Re-saving an equivalent handle preserves its disabled state and existing canonical spelling
instead of silently re-enabling or renaming the account.
Persisted account decoding reuses the same handle validation as new configuration, so malformed
local JSON cannot introduce URL delimiters or non-numeric Luogu identities into the sync boundary.
Persisted profile decoding applies that same judge-specific validation, so malformed cached
identities are rejected before they can be displayed or matched against an account.
CONNECTORS shows the latest local sync status for each configured judge and public handle. Status,
typed error, and completion time come from the local ledger and never expose raw transport details.
When rating history is available, CONNECTORS also shows the locally cached change count and latest
rating; the records contain only validated public judge, handle, contest, rating, rank, and time data.
When submission history is available, CONNECTORS shows its local count, latest verdict, and latest
problem identity without exposing source code or raw transport payloads; the latest 20 cached rows can
be expanded in place with their time, problem, verdict, and language.
Successful profile snapshots carry their fetch completion time and the Dashboard renders it, so
local cached data is distinguishable from a live response.
The shared local-state banner is also shown on CONNECTORS and SYNC HISTORY when loading or save
errors occur, so a failed account action remains visible without navigating away.

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
