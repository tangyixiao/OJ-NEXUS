# Phase 135 — Apple Document Exchange — Design

Status: ACTIVE. This design is synthesized by the controller from the
phase-135 recon (read-only report over `apple/` + `apple.yml`, 2026-11-19/20)
and binds the phase-131 portable contract (`contracts/portable/v1/`) to the
Apple clients.

Baseline main: `ce743f0` (phases 131-134 + 136 implementation delivered;
v1.0.5 auto-published via the tag workflow).

## Inputs this design synthesizes

- **Roadmap** (`docs/LONG_RANGE_PLAN_121_220.md`, PHASE 135): dependencies
  130/131; P's Core Codable/store, macOS/iOS document import/export UI.
  Deliverable: inspect/import/export through the platform's document
  interfaces; handle the sandbox-authorized file lifecycle; distinguish a
  portable package from the local recovery snapshot. Acceptance G-P: mixed
  golden package lossless round trip on macOS AND iOS; records that have no
  editing UI yet are archived and reported; authorization cancel, invalid URL
  and write failures all produce state; out-of-schema fields and unsupported
  versions are rejected. (130's interactive-device remainder stays a recorded
  debt; it does not gate this phase — the recon-verified 130A atomic storage
  machinery is the dependency actually consumed here.)
- **Phase 131 contract** (frozen): `contracts/portable/v1/` — check.py
  oracle, schema.json, 15-case golden corpus, pinned mixed-golden canonical
  identity `81bb13c1746381f2d7f95ae69e421503dcc898aae823222e8a3caf40d55b8d44`.
- **Apple recon findings** (verified 2026-11-19, report on file):
  - Storage: `LocalWorkspaceStore` is an `actor` with guarded read-only
    startup, flock (`state.json.lock`) + raw-byte CAS + staged
    `install()`/rename-or-link atomic writes, snapshot-first backup,
    quarantine-restore (`LocalWorkspaceStore.swift:33-205`); raw POSIX IO in
    internal `WorkspaceFileIO` (`read/withLock/install`,
    `WorkspaceFileIO.swift:5-102`) — reusable inside OJNexusCore.
  - Errors: `WorkspaceStorageFailure` is a non-raw enum whose `rawValue` is a
    computed uppercase display label (`WorkspaceStorageStatus.swift:3-21`);
    cases: notLoaded/corrupt/unsupportedVersion/unavailable/busy/conflict/
    backupUnavailable/recoveryUnavailable/uncertainOutcome/missingEstablished.
  - Codec: `WorkspaceFileCodec` uses JSONSerialization + JSONDecoder with
    `knownKeys` + integral-version gate (`WorkspaceFileCodec.swift:5-43`) —
    **unusable for the portable path** (duplicate keys last-wins; NSNumber
    folds numbers through double): same reason phase 134 banned QJson.
  - Domain: no problem/workspace record types exist (only submissions carry
    optional `problemID`/`problemName` strings, `Domain.swift:180-257`) — the
    portable archive is greenfield, tiered like every other platform.
  - UI: `NexusDashboardModel` is a `@MainActor ObservableObject` holding the
    store actor; mutations follow optimistic-apply → persist → rollback; async
    style is actors + `async throws`, zero completion handlers; UI buttons
    wrap calls in `Task { await model… }` and disable via `canMutate`
    (`NexusDashboardModel.swift:13-70`, `NexusRootView.swift`); the only
    presentation modifier today is one `.confirmationDialog` — a document
    picker introduces the first `.sheet`.
  - Tests: no Bundle resources anywhere (fixtures are inline string
    literals); the iOS simulator run selects exactly
    `OJNexusCoreTests/WorkspaceRecoveryTests` (`test-ios-recovery.sh:31`,
    validated by `validate_ios_recovery.py --suite`, line 377).
  - CI: `apple.yml` runs on macos-14 — boundary audit (forbids the
    substrings `token`, `TODO`, `FIXME`, `fatalError(`, `password`,
    `cookie`, `webview`, … in ALL Apple sources/tests), `swift test`,
    both product builds, simulator recovery script, evidence artifact.

## Decisions

1. **No JSONSerialization / JSONDecoder in the portable path.** The Swift
   portable codec (`Sources/OJNexusCore/Portable/PortableJson.swift`) is a
   hand-written strict decoder + canonical writer porting
   `windows/src/OJNexus.Windows.Core/Portable/PortableJson.cs` semantics:
   byte-level scan, BOM/trailing-data/duplicate-key/NaN/Infinity/lone-surrogate
   rejection, depth ≤ 200, numbers preserved as **literal text** (no double
   folding), canonical output with code-point-sorted keys and no insignificant
   whitespace. The local recovery-snapshot codec is untouched.
2. **Portable models + codec are greenfield** (`PortableModels.swift`,
   `PortableCodec.swift`): the same value tree and schema rules as the other
   three platforms (frozen schema subset, credentials/extra/path/size rules,
   `$`→`\z` translation for schema patterns, raw `$` for PATH_UNSAFE), so the
   golden corpus and the pinned identity are reproducible byte-for-byte.
3. **Archive storage is a file-based tiered archive, not SQLite**
   (`PortableArchiveStore.swift`): an `actor` holding one versioned archive
   document (`portable-archive.json`, `formatVersion 1`, tiered record
   arrays) written through the existing `WorkspaceFileIO` machinery — flock +
   staged install/rename = the single transaction; guarded read-only startup
   mirrors the house store; full-replace import semantics match Windows
   `PortableArchiveStore` (replace/clear/getRecords). Records without editing
   UI are archived verbatim and reported in counts (roadmap deliverable).
4. **Repository orchestration** (`PortableRepository.swift`): preview →
   explicit apply → cancel, plus lossless export, mirroring the Android
   semantics (staged intent superseded/cancelled by later intents; cancel
   aborts only an in-flight apply; export = archive re-read in canonical
   form; preview identity == export identity).
5. **Wrong-kind detection**: a file that is neither a valid portable package
   nor loadable as anything else reports a typed status distinguishing
   `unsupportedVersion` / schema-invalid (`corrupt` family) from the local
   recovery snapshot shape — the local snapshot's key set is not accepted by
   the portable decoder and vice versa; both rejections are explicit states,
   never silent.
6. **UI = one new `NexusPage.portable` case** + detail branch in
   `NexusRootView`, model state on `NexusDashboardModel` (`@Published
   portableStatus`, staged intent fields), buttons gated by `canMutate`:
   - Import via SwiftUI `.fileImporter` (first `.sheet` in the codebase —
     deliberate, single presentation surface): `startAccessingSecurityScoped`
     /`stop` around the read; **authorization cancel** and **invalid URL**
     each land in a typed status line (roadmap requirement), never an abort.
   - Export via `.fileExporter` with a portable-package document (UTF-8 JSON
     bytes); write failure → typed status, prior archive untouched.
   - No alerts/dialogs beyond the existing recovery confirmation pattern;
     states are visible text, not color-only (house a11y rule).
7. **Tests, no Bundle resources**: golden fixtures are inline string
   literals (house style). New `PortableExchangeTests` class covers: mixed
   golden `pw-p01` decode → preview counts → apply → export → **identity ==
   pinned `81bb13c1…d44`**; unsupported version, unknown fields, duplicate
   ids, path-unsafe names rejected with typed reasons; authorization-cancel /
   invalid-URL / write-failure states; wrong-kind snapshot rejection.
   macOS evidence = `swift test` in CI; **iOS evidence** = the simulator run
   extended to also select `OJNexusCoreTests/PortableExchangeTests`
   (`test-ios-recovery.sh` gains a second `-only-testing` flag;
   `validate_ios_recovery.py` gains a multi-suite validate pass writing a
   second summary) — additive, existing recovery gate untouched.
8. **Boundary-audit compliance**: no Apple source/test/comment may contain
   the banned substrings (`token`, `TODO`, `FIXME`, `fatalError(`, …);
   contract vocabulary stays clear of them by design (the portable package
   excludes credentials entirely).

## Out of scope

Local-ledger/workspace snapshot changes, sync transport, problem library
(phases 137-140), interactive device acceptance of phase 125/130 debts
(separate recorded debt), App Store packaging/signing.

## Verification shape (G-P)

- `swift test` green on macos-14 including `PortableExchangeTests`
  (macOS round-trip evidence, CI run recorded).
- Simulator run extended gate: `PortableExchangeTests` executed > 0, failed
  == 0 on a real iOS simulator (iOS round-trip evidence, second summary in
  the `ios-recovery-evidence` artifact).
- Boundary audit + builds green in the same CI run.
- Independent review record + verification record with measured values;
  roadmap tick; delivery PR to `main`.
