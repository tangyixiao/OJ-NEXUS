# Phase 129A Linux Storage Startup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reject unsafe existing SQLite stores without converting them to fresh workspaces and make first schema creation transactional.

**Architecture:** SqliteStore remains the public startup authority. A small internal helper handles schema validation and atomic creation; existing CLI/GUI consume the same Result<void>. Read-result API changes and recovery features follow separately.

**Tech Stack:** C++20, Qt 6.4+, QSQLITE, Qt Test, CMake/Ninja, WSL Ubuntu-24.04.

**Spec:** `docs/superpowers/specs/2026-10-24-phase-129a-linux-storage-startup.md`

## Global Constraints

- Native C++20 / Qt 6.4+ / QSQLITE; no new dependency or storage authority.
- Storage failures retain SyncError::Storage and CLI exit code 6.
- English uppercase telemetry UI; no marketing, credentials, cookies or sessions.
- Tests use isolated temporary data and never normal user XDG directories.
- No Android, Windows or Apple runtime changes; no PR/main merge or force push.
- Preserve the pre-existing TEMP/ directory and the separate dirty linux-client worktree.

## Review Focus

- A losing first-use initializer must not write/delete another creator's file (Task 1 competition test).
- A failed startup must not retain schemaVersion=2 from an earlier successful call (Task 1 reinitialization failure test).
- A valid legacy v1 migration need not have new-v2 sync_modules foreign keys (Task 1 migrate/reopen test).
- quick_check error rows and metadata coercion must not become success (Task 1 real corrupt and typed-version cases).
- Read-only WAL preflight has sidecar effects; a live valid WAL store must remain usable (Task 1 two-connection WAL test and evidence limits).

### Task 1: Guard existing stores and transact initial schema

**Files:**
- Modify: `linux/src/core/store.cpp`, `linux/src/core/include/ojnexus/core/store.h` only if private declarations are needed.
- Create: internal `linux/src/core/storage_schema.h` and `.cpp` if needed; register in `linux/src/core/CMakeLists.txt`.
- Test: `linux/tests/core/SqliteStoreTest.cpp`, `linux/tests/cli/CommandParserTest.cpp`, `linux/tests/gui/MainViewModelTest.cpp`; tests CMake only if internal-header include path needs it.
- Update: `linux/README.md` for the precise startup boundary, not broader recovery promises.

**Interfaces:**
- Consumes: existing `Result<void> SqliteStore::initialize()` and `SyncError::Storage`.
- Produces: same public signatures. If split, use internal `Result<int> validateExistingSchema(QSqlDatabase&)` and `Result<void> createInitialSchema(QSqlDatabase&)` in `OjNexus::StorageSchema`; the latter owns BEGIN/COMMIT/ROLLBACK and accepts an already opened connection, not a path. No public bypass/test flag.
- Every behavioral requirement in the spec is binding for this task. Read the spec after this brief; do not read another plan or SDD workspace.

- [x] **Step 1: Write real SQLite failing tests.**
  Add named cases for `rejectsExistingEmptyFileWithoutMutation`, `rejectsMalformedExistingStores_data`/test (non-SQLite, truncated, no metadata, empty metadata, duplicate rows, NULL/text/real versions, future version, missing table/column/PK, wrong affinity), `repeatedFailureClearsSchemaVersion`, `rollsBackPartialInitialSchema`, `firstUseDoesNotOverwriteCompetitor`, `opensValidWalStoreWithLiveConnection`.
  In rejection tests assert result.error()==Storage, schemaVersion()==0, closed main bytes equal before/after two attempts, and no new schema objects. Seed non-empty valid data for reopen/migration tests and preserve the existing live/stale operation tests. Add CLI storage exit 6 and GUI unavailable-state checks using rejected isolated data. The atomic-DDL test can call the internal creation helper against a SQLite fixture with a deliberately colliding later table; earlier DDL/version must roll back while the preexisting fixture table/data survives. Record actual WAL sidecar observations without asserting unsupported byte immutability.
- [x] **Step 2: Run focused tests on unchanged production code and retain RED logs.**
  Configure both GUI/tests ON with existing CMake/Ninja in an isolated build directory; run the new store/CLI/GUI cases. An implementation missing the protection must demonstrate the zero-byte or incomplete-store failure. A new-helper declaration/link failure alone is not the behavioral RED evidence.
- [x] **Step 3: Implement the guard and initial transaction.**
  Atomically reserve genuinely absent files, read-only validate existing files, strictly check quick_check/metadata/schema, reopen writable and revalidate before writes, then retain supported v1 migration and stale-operation recovery. Keep schemaVersion zero until successful initialization. On DDL/migration commit failure attempt rollback. Close and remove all owned probe connections only after all QSqlQuery objects are destroyed. Avoid long-lived duplicate connections and ad hoc table-name input.
- [x] **Step 4: Verify focused GREEN then full Linux build and CTest.**
  Use only Ubuntu-24.04, existing Qt6 tools, offscreen/software Qt, unique XDG/data/runtime directories. Run CMake GUI/tests ON, `cmake --build ... --parallel 2`, and `ctest --test-dir ... --output-on-failure --parallel 1`; expect all eight targets PASS. Capture raw stdout/exit codes and copy logs plus complete build tree to a fresh Windows Temp evidence directory before WSL exits. No default user data or OJ requests. Do not run Android/Windows builds in this task.
- [x] **Step 5: Review intended diff and commit.**
  Update the Linux README with exact accepted/rejected startup behavior and limits. Run `git diff --check`, inspect status/diff for secrets, stage only intended code/tests/README. Commit `fix(linux): reject unsafe stores before schema writes`. Write full TDD, build, tests, paths, hashes where measured and concerns to this task's report. No push/release/merge/subagents.

### Task 2: Record independent evidence and publish the scoped PR

**Files:** `docs/verification/phase-129a-linux-storage-startup.md`, `docs/verification/evidence/phase-129a-summary.json`, phase 129 row in `docs/LONG_RANGE_PLAN_121_220.md`, Linux section of `docs/verification/platform-capabilities.md`.

**Interfaces:** Consumes Task 1's committed source and independently approved review. Produces an honest verification record and open scoped PR against `codex/phase-129-linux-base`; no release unless a separately verified useful artifact is ready.

- [x] **Step 1:** Root reviews task report/commit range with a fresh task reviewer, then resolves findings under the SDD fix-loop rules.
- [x] **Step 2:** Root independently exercises the actual compiled CLI on isolated absent/zero/corrupt/incomplete/future cases; record exit and unchanged bytes. Treat offscreen GUI tests separately from visible GUI evidence. Run Android test/assembleDebug if integration or final scope requires it; never reuse historical evidence as a fresh build.
- [x] **Step 3:** Root records tested source, fresh results, platform/host limits and pending 129B/recovery gates in docs. Mark 129A only, leaving full129 unchecked. Self-review docs/schema JSON; no mirrored tests for prose.
- [ ] **Step 4:** Whole-branch independent review, at most one final fix wave and one scoped rereview. Check/push base and feature using HTTPS credential helper, create and attach scoped PR. Verify actual remote head, open/unmerged state and hosted checks. No merge, force push or retag of earlier release.

## Self-review and continuation

Startup requirements map to Task 1; honest publication and full129 boundaries map to Task 2. Public signatures are unchanged; private schema helper is a testable production unit, not a parallel storage authority. Source-column order is not incorrectly required, and supported legacy FK differences are explicit. This plan reuses the active isolated Linux worktree with its pre-existing TEMP/ preserved; no new checkout is needed. The prior baseline is reference evidence only; Task 1 runs new code gates.
