# Luogu Workspace Drafts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist and restore one local Luogu editor draft per judge/problem without transmitting source code or input.

**Architecture:** Add a Room entity/DAO and a repository value boundary, migrate the database from v9 to v10, inject the repository into `WorkspaceViewModel`, and render localized draft persistence state in the existing native screen. Debounced writes keep typing responsive while a user edit always wins over a late restore.

**Tech Stack:** Kotlin, Jetpack Compose, Room, Coroutines/Flow, JUnit, existing manual `AppContainer`.

**Spec:** `docs/superpowers/specs/2026-09-01-luogu-workspace-draft-design.md`

## Global Constraints

- Local-first only; no cloud account or cross-device synchronization.
- No Luogu main-site password, Cookie, Session, or CSRF state.
- `submission_jobs` must continue to exclude source code and standard input.
- All UI strings go through `res/values/strings.xml` and `values-zh-rCN/strings.xml`.
- Schema migrations are non-destructive and the exported schema stays committed.
- Every new production behavior is introduced after a failing test.

---

### Task 1: Add the Room draft storage boundary

**Files:**
- Create: `app/src/main/java/com/ojnexus/core/database/entity/WorkspaceDraftEntity.kt`
- Create: `app/src/main/java/com/ojnexus/core/database/dao/WorkspaceDraftDao.kt`
- Create: `app/src/main/java/com/ojnexus/core/data/repository/WorkspaceDraftRepository.kt`
- Modify: `app/src/main/java/com/ojnexus/core/database/OjNexusDatabase.kt`
- Modify: `app/src/main/java/com/ojnexus/OjNexusApplication.kt`
- Test: `app/src/test/java/com/ojnexus/core/database/WorkspaceDraftRepositoryTest.kt`

**Interfaces:**
- `WorkspaceDraftRepository.find(judge: JudgeId, pid: String): WorkspaceDraft?`
- `WorkspaceDraftRepository.save(judge: JudgeId, pid: String, draft: WorkspaceDraft)`
- `WorkspaceDraft` contains `code: String`, `input: String`, `language: String`, and `o2: Boolean`.

- [ ] **Step 1: Write the failing repository test**

Create an in-memory Room database using the existing test database helper, save a
`WorkspaceDraft` for `JudgeId.LUOGU/P1001`, read it back, and assert all fields. Save
another draft for `P1002` and assert both rows remain independent.

- [ ] **Step 2: Run the test to verify RED**

Run:
`./tools/gradlew-local.bat testDebugUnitTest --tests com.ojnexus.core.database.WorkspaceDraftRepositoryTest --no-daemon --console=plain`

Expected: compilation failure because the entity, DAO, repository, and database
accessor do not exist yet.

- [ ] **Step 3: Write the minimal Room implementation**

Use composite primary key `judge,pid`, non-null text fields, and an `updated_at`
epoch-millis column. Add `workspaceDraftDao()` to the database, increase
`OJ_NEXUS_SCHEMA_VERSION` to 10, and add `MIGRATION_9_10` that creates the table
without touching existing tables. Implement `RoomWorkspaceDraftRepository` with
the injected `Clock`, mapping `JudgeId.id` to the entity and using
`OnConflictStrategy.REPLACE`.

- [ ] **Step 4: Run the repository test to verify GREEN**

Run the same focused command and expect the read/write isolation assertions to pass.

- [ ] **Step 5: Commit the storage boundary**

```bash
git add app/src/main/java/com/ojnexus/core/database app/src/main/java/com/ojnexus/core/data/repository app/src/main/java/com/ojnexus/OjNexusApplication.kt app/src/test/java/com/ojnexus/core/database/WorkspaceDraftRepositoryTest.kt
git commit -m "feat: add local workspace draft storage / 增加本地工作区草稿存储"
```

### Task 2: Add schema migration coverage

**Files:**
- Modify: `app/src/test/java/com/ojnexus/core/database/MigrationTest.kt`
- Create or regenerate: `app/schemas/com.ojnexus.core.database.OjNexusDatabase/10.json`

- [ ] **Step 1: Write the failing migration test**

Add a migration test that creates schema 9, inserts an existing `submission_jobs`
row, opens the database with `MIGRATION_9_10`, asserts the old row remains, and
asserts `PRAGMA table_info('workspace_drafts')` contains `judge`, `pid`, `code`,
`input`, `language`, `o2`, and `updated_at`.

- [ ] **Step 2: Run the migration test to verify RED**

Run:
`./tools/gradlew-local.bat testDebugUnitTest --tests com.ojnexus.core.database.MigrationTest --no-daemon --console=plain`

Expected: compilation failure until the migration and schema version 10 exist.

- [ ] **Step 3: Register migration and export schema**

Register `MIGRATION_9_10` in `OjNexusDatabase.build`, add the migration test, and
run the Room schema-producing build so version 10 is exported. Do not delete schema
files 1 through 9.

- [ ] **Step 4: Run migration and database tests to verify GREEN**

Run:
`./tools/gradlew-local.bat testDebugUnitTest --tests com.ojnexus.core.database.MigrationTest --tests com.ojnexus.core.database.OjNexusDatabaseTest --no-daemon --console=plain`

Expected: all migration and database tests pass and schema versions 1–10 remain.

### Task 3: Restore and debounce-save drafts in the workspace ViewModel

**Files:**
- Modify: `app/src/main/java/com/ojnexus/feature/workspace/WorkspaceViewModel.kt`
- Modify: `app/src/main/java/com/ojnexus/feature/workspace/WorkspaceScreen.kt`
- Modify: `app/src/main/java/com/ojnexus/OjNexusApplication.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`
- Test: `app/src/test/java/com/ojnexus/feature/workspace/WorkspaceViewModelTest.kt`

**Interfaces:**
- Add `WorkspaceDraftState { DISABLED, LOADING, CLEAN, SAVING, SAVED, ERROR }`.
- Add `draftState: WorkspaceDraftState` to `WorkspaceState`.
- Add optional constructor parameter `drafts: WorkspaceDraftRepository? = null` after existing optional parameters so existing callers remain source-compatible.
- Add `delayForDraft: suspend (Long) -> Unit` with default `delay(it)` for deterministic tests.

- [ ] **Step 1: Write failing ViewModel tests**

Add tests for: an existing draft restores code/input/language/O2; a user edit made
before a blocked read remains after the read completes; a mutation calls save after
the injected debounce; and a repository exception changes state to `ERROR` without
changing the editor fields.

- [ ] **Step 2: Run the tests to verify RED**

Run:
`./tools/gradlew-local.bat testDebugUnitTest --tests com.ojnexus.feature.workspace.WorkspaceViewModelTest --no-daemon --console=plain`

Expected: compilation failure for the missing repository parameter/state and failing
draft assertions.

- [ ] **Step 3: Implement minimal restore/save behavior**

Initialize `LOADING` only when a repository is provided. Load once in the existing
test/application scope; apply the loaded draft only when no setter has marked the
state edited. Setters call a 300 ms latest-write-wins debounce. A save failure sets
`ERROR` while leaving `code` and `input` intact. Keep submission request persistence
unchanged and never put source code/input into `SubmissionJobEntity`.

- [ ] **Step 4: Add the visible localized status**

Render one compact status line near the editor using resources for `DRAFT LOADING`,
`DRAFT SAVING`, `DRAFT SAVED`, `DRAFT NOT SAVED`, and their Chinese translations.
Pass `it.workspaceDraftRepository` from `WorkspaceScreen`.

- [ ] **Step 5: Run ViewModel and workspace tests to verify GREEN**

Run:
`./tools/gradlew-local.bat testDebugUnitTest --tests com.ojnexus.feature.workspace.WorkspaceViewModelTest --tests com.ojnexus.feature.problems.RemoteProblemWorkspaceTest --no-daemon --console=plain`

Expected: all workspace tests pass.

- [ ] **Step 6: Commit the feature**

```bash
git add app/src/main/java/com/ojnexus/feature/workspace app/src/main/java/com/ojnexus/OjNexusApplication.kt app/src/main/res/values/strings.xml app/src/main/res/values-zh-rCN/strings.xml app/src/test/java/com/ojnexus/feature/workspace/WorkspaceViewModelTest.kt
git commit -m "feat: persist Luogu workspace drafts / 持久化洛谷工作区草稿"
```

### Task 4: Document and verify the usable end state

**Files:**
- Modify: `README.md`
- Modify: `docs/ROADMAP.md`
- Create: `docs/releases/v0.3.23.md`
- Preserve: all previous phase and release documents

- [ ] **Step 1: Add bilingual documentation**

Append a Phase 27 section and a bilingual v0.3.23 note describing local Room draft
restore, per-problem isolation, visible save state, and the explicit non-goals.
Change the current status label to Phase 27 without removing Phase 0–26 history.

- [ ] **Step 2: Run full verification**

Run:
`git diff --check; ./tools/gradlew-local.bat clean test assembleDebug lintDebug --no-daemon --rerun-tasks --console=plain`

Expected: `BUILD SUCCESSFUL`, zero test failures, and lint completes. Install the APK
in the emulator, open a Luogu workspace, edit code and language, leave, reopen the
same workspace, and verify fields plus the localized saved-state text remain.

- [ ] **Step 3: Commit documentation and verification record**

```bash
git add README.md docs/ROADMAP.md docs/releases/v0.3.23.md
git commit -m "docs: document workspace drafts / 记录工作区草稿"
```

- [ ] **Step 4: Push and publish GitHub Release**

Push the current branch, create tag `v0.3.23` at the verified HEAD, upload
`app/build/outputs/apk/debug/app-debug.apk`, and verify the public release is not a
draft or prerelease. Compare the GitHub asset digest to `Get-FileHash` and compare
the tag commit SHA to `git rev-parse HEAD` before reporting the download link.
