# Phase 130 Apple State Safety Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Protect the active native Apple state file from failed startup, stale writers and interrupted replacement, with explicit snapshot recovery and real native tests.

**Architecture:** LocalWorkspaceStore remains the single authority. Separate disk format validation and synchronous Darwin file operations from the actor; the dashboard and SwiftUI expose typed readiness. Native CI executes the same recovery XCTest suite on macOS and an actual iOS simulator.

**Tech Stack:** Swift 5.9+, Foundation, Darwin, actors, SwiftUI, XCTest, SwiftPM, existing GitHub macos-14/Xcode runner; no new package dependencies.

**Spec:** `docs/superpowers/specs/2026-10-25-phase-130-apple-state-safety-design.md`

## Global Constraints

- macOS 13 / iOS 16 deployment floors; existing native architecture and public handles only.
- Sole authority is state.json; state.json.bak is a recovery snapshot; legacy LocalLedgerStore remains test-only.
- New flat file formatVersion is integer 1; compatible unversioned known-root files load without rewrite.
- Every save requires prior successful load; failed admission never performs network work or mutates state.
- Nonblocking persistent flock, exact-byte baseline comparison, same-directory staging, no-overwrite first installation.
- Snapshot before main install, quarantine before explicit corrupt-main restore, no automatic restore/downgrade.
- No broad refactoring, new dependencies, main merge, force push, real user-data inspection, or claims of full power-loss durability.
- Windows cannot execute SwiftUI/Apple XCTest; controller runs remote native RED/GREEN and records actual evidence.
- Commit intended source/docs only; no local machine paths, build artifacts or credentials. Implementers do not push.

## Review Focus

- A missing file behind an inaccessible/non-directory parent must not become a fresh empty writable workspace.
- Two independently opened stores or flock holders must not lose updates or bypass each other's lock.
- A fault after main rename must not report ordinary failure while rolling memory back behind committed disk.
- UI/load/account/sync overlap must not start a request or replace bytes after storage admission fails.
- Native iOS test discovery must fail on zero tests, wrong scheme/destination, or absent xcresult, not report a generic build as recovery PASS.

## File responsibilities

- `apple/Sources/OJNexusCore/LocalWorkspaceStore.swift`: existing domain state plus actor admission, baseline, snapshot/recovery orchestration.
- New `WorkspaceFileCodec.swift`: strict supported flat-root disk format and new version encoding.
- New `WorkspaceFileIO.swift`: small synchronous Darwin read/stage/link/rename/flock implementation and internal per-instance fault hooks.
- New `WorkspaceStorageStatus.swift`: public typed failures/status shared with UI.
- Existing `NexusDashboardModel.swift`, `NexusRootView.swift`: readiness/status and explicit recovery; retain feature/adapter behavior.
- Existing `DomainTests.swift`: migrate fixture preconditions, preserving assertions.
- New `WorkspaceStartupRegressionTests.swift`, `WorkspaceRecoveryTests.swift`: focused behavioral and file-fault tests.
- New `apple/scripts/test-ios-recovery.sh` plus a small Python result/discovery helper if warranted: CI-only discovered native execution and evidence validation.
- `.github/workflows/apple.yml`: preserve existing gates, add native iOS recovery and generated-log artifacts.
- `apple/README.md`, `docs/verification/phase-130-apple-state-safety.md`, roadmap entry: honest delivered scope/evidence.

### Task 1: Active store, dashboard and recovery behavior

**Files:** create/modify the Core/UI/test files above only. Read the binding spec first, then relevant existing source. Do not change workflow, README, roadmap or other platforms in this task.

**Interfaces:** preserve `LocalWorkspaceState` and `LocalWorkspaceStore(url:)`. Add public `WorkspaceStorageFailure: Error, Equatable, Sendable` with cases notLoaded, corrupt, unsupportedVersion, unavailable, busy, conflict, backupUnavailable, recoveryUnavailable, uncertainOutcome, missingEstablished; `WorkspaceStorageStatus: Equatable, Sendable` with unloaded, ready, blocked(WorkspaceStorageFailure), recoverable(WorkspaceStorageFailure). Actor adds `init()`, throwing `defaultURL(fileManager:)`, `storageStatus() -> WorkspaceStorageStatus`, `restorePreviousSnapshot() throws -> LocalWorkspaceState`. Existing `load()` and `save(_:)` remain throwing. Model publishes storageStatus/isSaving/isRecovering and shared mutation/recovery/readiness properties used by view/tests. Internal helper organization may use focused extensions without splitting existing sync logic into a new architecture.

- [ ] **Step 1: Commit behavioral RED checkpoint, using current public APIs only.** Add WorkspaceStartupRegressionTests with four independent temporary-path tests: corrupt JSON model.load then configurePublicAccount preserves raw main and no account; flat formatVersion 99 with otherwise valid empty state must fail load and preserve bytes; valid RUNNING ledger model.load preserves original raw main; direct store.save without load must fail and keep main absent. Follow existing XCTest async/actor patterns, no new production types in these tests. Do not edit production yet. Commit `test: expose unsafe Apple workspace startup writes`. Report NEEDS_CONTEXT awaiting controller's remote native RED proof; this is a partial checkpoint, not Task DONE.
- [ ] **Step 2: Controller executes current Apple workflow on the checkpoint SHA.** Await native result without changing production in parallel. Assertion failures in these four named tests count as RED; compilation/source-boundary failures do not. Controller provides the log/run link and resumes this same implementer.
- [ ] **Step 3: Implement typed format/read/admission.** Implement strict format codec (including bool/fractional/unknown root rejection), real read error classification and no temporary fallback. Require successful explicit load before save. Main absent + backup present/previously observed main is missingEstablished; only main+backup absent is fresh. Pure load must not rewrite/create authority files. Add tests for legacy optional arrays, manual save upgrade, repeated startup raw bytes, future/invalid/unknown format, corrupt typed error, directory/ENOTDIR/permission failure, configured location failure, and direct-notLoaded rejection.
- [ ] **Step 4: Implement locking, snapshot and recovery.** Implement unique owned stage, robust writes, link-exclusive first create, rename replacement, stable independent flock. Hold lock across revalidation/CAS/snapshot/install. Preserve exact predecessor bytes; initial snapshot is intended new bytes before exclusive main create. Validate snapshot and observed corrupt/missing main again under lock before explicit restoration. Unique quarantine first; future format never eligible; restore leaves backup unchanged. Test normal save, exact predecessor raw bytes, first-stage interruption + missing-established recovery, damaged/future backup, corrupt/missing restore, changed main/backup conflict, quarantine failure/no overwrite, stale actors, independent same-process flock and (macOS conditionally) subprocess-held lock.
- [ ] **Step 5: Prove failure boundaries.** Use internal per-instance synchronous hooks (no globals/env switches) for staging/backup/quarantine/pre-install/post-install faults. Assert main and backup bytes, unrelated file preservation and only-owned-stage cleanup. Post-install error with intended bytes returns success/advances baseline; reread failure becomes uncertainOutcome and blocks save. Cover snapshot failure before main replacement and fresh first-create conflict. Do not add save cancellation points that break sync cancellation's final persistence.
- [ ] **Step 6: Wire model/UI admission and migrate fixtures.** Remove startup save. Preserve last cache on load failure. Gate every account action, sync/direct/batch/retry path before mutation/network; serialize account writes across awaits, block reload/restore during active work, stop later batch work on storage error, retain cancel action. Shared UI guards disable all mutations and show storage reason; explicit reload and confirmable restore retain read-only navigation. `refresh()` cannot cancel active RUNNING state. Migrate old test fixture setup to load→save→model.load as needed, preserving existing expected cache/ledger/cancellation semantics. Add unloaded/corrupt/future no-network/no-mutation tests, overlapping action guards, last-cache retention, restore readiness, and storage-failure-stops-batch tests.
- [ ] **Step 7: Native GREEN and self-review.** Run static diff/source-boundary checks locally. Controller runs full macOS swift test and both product builds on actual checkpoint. Fix compilation/behavioral regressions and preserve all existing tests. Final focused WorkspaceRecoveryTests must be runnable as `OJNexusCoreTests/WorkspaceRecoveryTests` for Task 2. Read full own diff, commit coherent source/test change(s), report exact native evidence or explicitly pending evidence. Do not claim Windows executed Swift. Report file holds files, tests, RED/GREEN evidence, concerns and commit IDs.

### Task 2: Real iOS recovery execution and retained native evidence

**Files:** `.github/workflows/apple.yml`; create `apple/scripts/test-ios-recovery.sh` and focused helper(s) only if needed. Do not modify Core/UI recovery behavior. Task 1 produces `OJNexusCoreTests/WorkspaceRecoveryTests`; preserve class identifier or coordinate an explicit controller ruling before changing it.

**Interfaces:** consume the actual package/suite at current Task 1 HEAD; execute nonzero recovery XCTest on installed iOS 16+ simulator; produce CI-only discovery text, full logs, xcresult, and a small machine-readable summary containing commit/scheme/destination/test counts/result. No app data/home directory artifacts.

- [ ] **Step 1: Inspect toolchain discovery evidence.** Baseline native run 40108976919 used Xcode 15.4 / Swift 5.10 / macOS arm64; these are observations, not hardcoded future assumptions. Workflow retains source boundary audit, full macOS swift test, macOS build, generic iOS build. Discover schemes (`xcodebuild -list -json`), devices (`xcrun simctl list devices available -j`) and actual scheme destinations. Select a real testable package scheme and matching available iOS 16+ simulator UDID; verify through xcodebuild instead of inventing names. Missing runtime/test scheme fails with evidence; no silent runtime download.
- [ ] **Step 2: Add bounded native runner.** Bash uses strict/pipefail behavior and records stdout/stderr to generated evidence. Execute xcodebuild test with actual scheme and `platform=iOS Simulator,id=<discovered>` plus `-only-testing:OJNexusCoreTests/WorkspaceRecoveryTests`, fresh absolute xcresult path and CODE_SIGNING_ALLOWED=NO. Preserve xcodebuild failure status. Put a reasonable workflow timeout on discovery/test; do not allow hung sim startup to consume unlimited time. No workflow wildcard selection that could accidentally run zero tests.
- [ ] **Step 3: Validate structured results.** Detect installed xcresulttool API via help; Xcode 15 supports the older JSON object API while newer tools may expose test-results summary. Parse actual test-target/class identities, executed count >0 and failure count ==0; confirm requested WorkspaceRecoveryTests really executed. Reject missing bundle, unsupported parser, skipped-only/zero/failed runs. Write summary with discovered values; log prose alone is not PASS. Test helper against small synthetic successful/zero/failed/wrong-suite structured fixtures without mirroring its exact implementation; fixtures are CI metadata only.
- [ ] **Step 4: Wire artifact retention and execute.** Always upload only CI-generated discovery/log/xcresult/summary with short retention, even on failed discovery/test where present. Run shell syntax/Python checks locally, then controller pushes and executes native job. Iterate toolchain/runner defects using actual logs. Final same-SHA full macOS tests + both builds + iOS recovery count all pass; report no UI/physical-device claim. Commit `ci: execute Apple recovery tests on iOS Simulator` and report paths/commands/native run evidence.

### Task 3: Evidence, final review and scoped delivery

**Files:** `apple/README.md`, `docs/verification/phase-130-apple-state-safety.md`, `docs/LONG_RANGE_PLAN_121_220.md`, PR description. Controller owns documentation and publication; product fixes stay with original task implementer through review process.

**Interfaces:** consume Task 1 source/test contract and Task 2 actual same-SHA CI results. Preserve separate interactive native UI and distribution-release boundaries.

- [ ] **Step 1:** Record baseline, native behavioral RED, final native GREEN, platform/suite counts and exact run/commit links. Document supported legacy upgrade, readiness, snapshot/quarantine, uncertainty behavior and explicit limitations.
- [ ] **Step 2:** Run required Android `test assembleDebug` with process-local JBR/SDK in this isolated checkout, or retain a clearly identified identical-tree current gate after evaluating source identity. Do not alter machine/repository Gradle paths. Diff-check intended files and source boundaries/secrets.
- [ ] **Step 3:** Perform per-task spec/quality review and whole-branch final review following SDD; fix through the original implementation agent. First three task fix rounds original Luna, cap five with escalation; final review has one consolidated fix wave and one scoped rereview, then controller rulings. Do not fabricate passing evidence.
- [ ] **Step 4:** Create/attach scoped PR against codex/phase-130-apple-base; draft while incomplete/RED, ready only after gates. No merges. Mark Phase 130 as partial if interactive macOS/iOS recovery acceptance is still missing. Do not publish an Apple binary release without an actual verified distribution artifact. Keep authorized long-range development goal active after this slice.
