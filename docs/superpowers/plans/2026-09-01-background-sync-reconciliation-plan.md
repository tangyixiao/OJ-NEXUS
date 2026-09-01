# Background Sync Reconciliation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore periodic sync scheduling for existing active background-sync judge accounts at app startup.

**Architecture:** A pure `JudgeSyncBootstrap` performs account/capability filtering and calls an injected periodic-enqueue function. `OjNexusApplication` supplies Room-backed lookup and the existing `JudgeSyncWorker` enqueue operation. Settings adds only a localized capability label.

**Tech Stack:** Kotlin, coroutines, WorkManager, Room, Jetpack Compose, JUnit.

**Spec:** `docs/superpowers/specs/2026-09-01-background-sync-reconciliation-design.md`

## Global Constraints

- Use the existing `JudgeSyncWorker` and six-hour periodic policy; do not add a second sync engine.
- Schedule only active accounts for adapters advertising `JudgeCapability.BACKGROUND_SYNC`.
- Do not add passwords, cookies, sessions, CSRF state, cloud sync, or submission work.
- All new UI strings must exist in `values/strings.xml` and `values-zh-rCN/strings.xml`.

---

### Task 1: Add the pure startup reconciliation policy

**Files:**
- Create: `app/src/main/java/com/ojnexus/judge/sync/JudgeSyncBootstrap.kt`
- Create: `app/src/test/java/com/ojnexus/judge/sync/JudgeSyncBootstrapTest.kt`

**Interfaces:**
- `JudgeSyncBootstrap(activeAccount: suspend (JudgeId) -> JudgeAccountEntity?, backgroundJudges: Set<JudgeId>, enqueuePeriodic: (JudgeId, Long) -> Unit)`
- Produces one `reconcile()` suspend function that schedules each eligible account once.

- [ ] **Step 1: Write the failing test** — assert that enabled accounts are scheduled, disabled/missing accounts are skipped, unsupported judges are ignored, and one throwing enqueue callback does not stop later judges.
- [ ] **Step 2: Run the focused test**

```powershell
.\tools\gradlew-local.bat :app:testDebugUnitTest --tests com.ojnexus.judge.sync.JudgeSyncBootstrapTest --no-daemon --console=plain
```

Expected: FAIL because `JudgeSyncBootstrap` does not exist.

- [ ] **Step 3: Implement the minimal pure policy** — iterate `backgroundJudges.sortedBy { it.id }`, fetch the account, require `enabled`, and invoke `runCatching { enqueuePeriodic(judge, account.id) }`.
- [ ] **Step 4: Run the focused test again** — expected PASS.
- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/ojnexus/judge/sync/JudgeSyncBootstrap.kt app/src/test/java/com/ojnexus/judge/sync/JudgeSyncBootstrapTest.kt
git commit -m "feat: reconcile background sync / 校准后台同步"
```

### Task 2: Wire startup and expose the capability

**Files:**
- Modify: `app/src/main/java/com/ojnexus/OjNexusApplication.kt`
- Modify: `app/src/main/java/com/ojnexus/feature/settings/SettingsScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`

- [ ] **Step 1: Add the failing resource mirror assertion or compile use** — reference the new localized setting label from the connected capability panel.
- [ ] **Step 2: Run the focused resource/settings tests** — expected FAIL until both locale keys and wiring exist.
- [ ] **Step 3: Wire `OjNexusApplication`** — after container construction, create `JudgeSyncBootstrap` with `findActive`, the registry's background-sync judge set, and `JudgeSyncWorker.enqueuePeriodic`; launch `reconcile()` in the existing IO scope.
- [ ] **Step 4: Add the English/Chinese capability label** — render it only for a connected account whose adapter has `BACKGROUND_SYNC`.
- [ ] **Step 5: Run focused tests again** — expected PASS.
- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/ojnexus/OjNexusApplication.kt app/src/main/java/com/ojnexus/feature/settings/SettingsScreen.kt app/src/main/res/values/strings.xml app/src/main/res/values-zh-rCN/strings.xml
git commit -m "feat: restore sync on startup / 启动恢复后台同步"
```

### Task 3: Verify, document, and publish

**Files:**
- Create: `docs/releases/v0.3.26.md`
- Modify: `README.md`
- Modify: `docs/ROADMAP.md`

- [ ] **Step 1: Run `git diff --check` and full `clean test assembleDebug lintDebug`** — all must finish with `BUILD SUCCESSFUL`.
- [ ] **Step 2: Install and launch the existing emulator** — verify the focused app is `com.ojnexus/.MainActivity` and no `FATAL EXCEPTION` appears; do not shut down the emulator.
- [ ] **Step 3: Add bilingual Phase 30 and Release v0.3.26 notes without deleting prior history.**
- [ ] **Step 4: Commit, push `codex/phase-5-arena`, tag `v0.3.26`, create the GitHub Release with the verified APK, and compare local/remote commit and asset SHA-256.**
