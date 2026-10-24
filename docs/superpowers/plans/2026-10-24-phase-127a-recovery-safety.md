# Phase 127A Recovery Safety Implementation Plan

> **For agentic workers:** Use superpowers:subagent-driven-development or superpowers:executing-plans
> task-by-task. The user authorized subagents; Luna implements, root plans/reviews/accepts.

**Goal:** preserve restore sources across interruption and prohibit application data access until
the recovered target and generation are coherent.

**Architecture:** one real file coordinator with an injected file-operation boundary; explicit
startup readiness owns graph creation; Activity and Worker entry points consume published state.

**Tech Stack:** existing Kotlin, Room, SQLite, Compose, WorkManager, JUnit/Robolectric.

**Spec:** [binding design](../specs/2026-10-24-phase-127a-recovery-safety-design.md).

## Global Constraints

- Base `b096045`, branch `codex/phase-127-recovery-safety`; integration base `codex/phase-127-android-base`.
- Native dependencies, schema, credentials, backup format and app version remain unchanged.
- Protocol and startup gates are one shipping boundary. Do not publish a core-only runtime release.
- Keep existing v1 corpus/nine legacy decision projections intact; no simulated recovery engine.
- No open application DB raw-copy, automatic reset, raw errors/paths, or power-loss claim.
- Broader Phase127 ENOSPC, strict worker generation, old SQLite compatibility remain open.
- Root owns spec/plan/roadmap/evidence/PR and full final gates; implementers stage exact owned code.

## Review Focus

1. Initial OLD source saving can split main/WAL too; retain originals until complete-copy intent.
2. Metadata failure after APPLIED must never trigger rollback; journal generation must match disk.
3. Legacy partial moves and orphan artifacts must block instead of empty database creation.
4. Worker starts before Application.onCreate must distinguish Starting from terminal Blocked.
5. Test fixtures must retain real WAL-only rows and verify bytes/records, not just outcome enums.

## Task 1 — Coordinator protocol, source preservation and actual file-fault tests

**Files:** modify `core/data/restore/RestoreProtocol.kt`, `DatabaseRestoreCoordinator.kt`,
`feature/settings/SettingsViewModel.kt` only for its exhaustive restore-status mapping;
create `core/data/restore/RestoreFileOperations.kt` under main package;
create `app/src/test/java/com/ojnexus/core/data/restore/DatabaseRestoreRecoveryFaultTest.kt`
and a focused SQLite fixture helper if needed; extend `RestoreProtocolTest.kt` for new stages.
All abbreviated main paths are under `app/src/main/java/com/ojnexus/`.

**Interfaces:** existing public two-argument/default coordinator construction remains valid;
internal three-argument construction injects RestoreFileOperations for tests. Add the exact
RestoreOutcome.Blocked / RecoveryDisposition / RestoreReadiness types from the spec and
`resolveBeforeRoomOpen(): RestoreReadiness`. Keep `restoreBeforeRoomOpen(): RestoreOutcome`.
Add stages SAVING_ROLLBACK, INSTALLING, ROLLING_BACK; add BLOCK_RECOVERY routing for nonterminal
blocked states while preserving legacy nine classifier projections. Task2 consumes readiness.
The Settings mapping handles Blocked as no Settings banner because pregraph screen owns it.

- [ ] Write RV015 real SQLite fixture test first: APPLIED NEW target + OLD generation + OLD rollback;
  assert on-disk exact NEW generation, unique NEW row and second-start idempotence. Run focused
  test to demonstrate the present defect (wrong OLD generation), not a missing-file compile error.
- [ ] Add real file-operation interface/defaults and implement shared forward finalizer with checked
  metadata and cleanup. Test generation replacement failure and post-generation cleanup failure;
  assert no OLD rollback under NEW generation, sources/intent retained, later forward convergence.
- [ ] Replace initial multi-file moves with source-preserving SAVING_ROLLBACK copies; write
  INSTALLING before touching live. Test each copy/sync failure and safe abort to validated OLD,
  plus uncertain SAVING state blocking. No preexisting recovery source is overwritten by new staging.
- [ ] Add durable ROLLING_BACK before any rollback mutation, copy/sync install temps retaining original
  main/WAL/SHM, validate live pair before terminal cleanup. Inject failures at each destination;
  two reconstructed starts must preserve original hashes and avoid auto retry. Intent-write failure
  must record zero destructive calls; catch failure cannot throw a replacement raw error.
- [ ] Add real WAL-dependent OLD row fixture plus main-only negative control; prove successful rollback
  restores that row and generation remains OLD. Materialize legacy split live-main/rollback-WAL,
  rollback-main/live-WAL, terminal ROLLBACK_FAILURE, corrupt/orphan journal, missing source tests.
- [ ] Resolve readiness exactly once; clean first install allowed, blocked never creates generation;
  rollback cannot install attempted NEW generation. Clean no-recovery legacy schema remains migratable.
- [ ] Run focused restore tests including all Phase126 corpus/consumer tests. Commit exact owned files
  after diff/secrets check; report RED/GREEN, command/output/XML and explicit limits; stop for review.

## Task 2 — Application ownership, DB-free Activity and actual Worker entry gates

**Files:** modify `OjNexusApplication.kt`, `MainActivity.kt`, three Worker files
`judge/sync/JudgeSyncWorker.kt`, `judge/luogu/open/LuoguOpenResultWorker.kt`,
`core/data/backup/ScheduledBackupWorker.kt`, `core/data/backup/BackupCadence.kt`; create `app/RecoveryStartup.kt` and
`app/RecoveryBlockedScreen.kt`; add resource strings in `res/values/strings.xml` and mirror the
four keys in `res/values-zh-rCN/strings.xml` for the existing locale-key contract. This slice
retains the specified uppercase English recovery copy in both catalogs.
Create `app/src/test/java/com/ojnexus/RecoveryStartupTest.kt`,
`app/src/test/java/com/ojnexus/RecoveryWorkerGateTest.kt`, a shared test WorkerParameters helper,
and `app/src/androidTest/java/com/ojnexus/app/RecoveryBlockedScreenComposeTest.kt`.
All abbreviated main paths are under `app/src/main/java/com/ojnexus/` except resources.

**Interfaces:** consume Task1 `resolveBeforeRoomOpen(): RestoreReadiness`.
AppContainer receives `RestoreReadiness.Ready`; `currentDataGeneration()` returns its immutable
resolved generation, and `restoreOutcome` returns its resolved outcome. Application publishes
`NexusStartupState.Starting`, `Ready(container)`, `Blocked(failure,recovery)` via immutable
safely visible state; `NexusStartupStateOwner` exposes `startupState`. Keep ready-only existing
container getter for compatible guarded callers. A lookup never constructs AppContainer.
Use narrow overridable creation/bootstrap methods or equivalent actual startup factory seam;
no global test flags. Default initial state Starting, all business initialization after Ready.

- [ ] Add failing actual startup test before wiring: a blocked real coordinator fixture makes graph
  and bootstrap factories throw if called; assert neither called and missing OJ DB remains missing.
  Add clean-install positive control, plus Starting state publication before initialization.
- [ ] Move recovery out of AppContainer and into Application exactly once. Gate crash preferences,
  bootstrap, sync reconciliation and scheduled backup reconciliation on Ready. Preserve normal
  ready initialization and startup tracing without reading an absent container.
- [ ] Gate Activity composition and onResume; use exact resource-backed uppercase blocked copy
  from the spec, default NexusTheme/NexusSpacing tokens, no DB preferences or destructive action.
  Add INITIALIZING LOCAL STORAGE copy for Starting. Compose component tests cover text and scaling.
- [ ] Gate all actual Worker doWork methods before any preferences, data generation, ledger, export
  or network calls. Starting returns retry; Blocked failure output result=RECOVERY_BLOCKED. Use
  ready snapshot container for subsequent work. Strict stale/missing generation policy remains127B.
- [ ] Guard every public Judge/Luogu/backup enqueue overload before generation or WorkManager
  resolution. Use Ready process generation instead of a new coordinator; lazy Luogu WorkManager
  lookup must not occur in its constructor on refusal. Starting/Blocked calls are no-op with no
  app-data access or work enqueue; test actual entry overloads, not only the guard predicate.
- [ ] Test actual Workers with valid persisted input, poisoned business dependencies and Starting/
  Blocked test Applications; assert outcomes and zero prefs/files/repository calls. Cached WorkManager
  2.11.2 WorkerParameters public constructor supports this with existing deps; do not add test lib.
- [ ] Run focused startup/worker/restore tests and assembleDebug. Report actual execution, diff check,
  commit exact owned files, stop. Do not change global unit-test resource configuration just to load UI;
  actual Activity launch/resume is validated separately on the dedicated AVD by root.

## Task 3 — Integrated acceptance, evidence and scoped PR

**Owner:** root verification/documentation, with independent whole-branch review after task gates.
**Files:** create `docs/verification/phase-127a-recovery-safety.md`; update DATA_SAFETY and roadmap
Phase127 ACTIVE substage only. Keep 127B strict work policy, 127C typed import errors, old-engine
export compatibility and physical-device limits explicit.

- [ ] Fresh full `testDebugUnitTest --rerun test assembleDebug assembleRelease`, collect XML totals,
  inspect actual added test IDs and log warnings rather than claiming pristine output.
- [ ] Build androidTest and run connected suite on dedicated test AVD; inspect actual blocked Activity
  launch/resume with missing OJ target and journal fixture. Preserve only intended synthetic test data;
  no user device. Verify missing target stays missing and recovery sources retain bytes across starts.
- [ ] Independent whole-branch review of integration-base..HEAD with spec, all checkpoint cases,
  UI/Worker wiring and evidence. Fix real findings and reverify changed paths.
- [ ] Check links/diff/secrets, push integration base and feature branch; create and attach scoped PR.
  Its base ref is an integration of the four upstream PR dependencies, not main or a production release.
- [ ] Record exact implemented scope and remaining Phase127 requirements; do not mark whole127 VERIFIED.
