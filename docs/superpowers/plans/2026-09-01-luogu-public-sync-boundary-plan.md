# Luogu Public Sync Boundary Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop Luogu account synchronization from calling a submission-record endpoint that the adapter does not advertise.

**Architecture:** Keep all existing repository stages and the OpenApp submission path. Narrow only `LuoguSyncCoordinator` to the four real public stages and update the coordinator contract test to assert success without a submission call.

**Tech Stack:** Kotlin, coroutines, Room, Retrofit adapters, JUnit/Robolectric.

**Spec:** `docs/superpowers/specs/2026-09-01-luogu-public-sync-boundary-design.md`

## Global Constraints

- Luogu account sync uses only public profile, rating, contest, and problem catalog stages.
- Do not add password, Cookie, Session, CSRF, cloud, or private submission-history handling.
- Keep local OpenApp submission jobs and their result workers unchanged.
- Preserve all historical phase notes and Releases.

---

### Task 1: Lock the public-only coordinator behavior

**Files:**
- Modify: `app/src/test/java/com/ojnexus/judge/luogu/LuoguSyncCoordinatorTest.kt`
- Modify: `app/src/main/java/com/ojnexus/judge/luogu/LuoguSyncCoordinator.kt`

- [ ] **Step 1: Change the existing coordinator test expectation first** — rename it to public stages, expect `SyncPhase.SUCCESS`, assert `report.allOk`, and assert `"submissions" !in adapter.calls`.
- [ ] **Step 2: Run the focused coordinator test**

```powershell
.\tools\gradlew-local.bat :app:testDebugUnitTest --tests com.ojnexus.judge.luogu.LuoguSyncCoordinatorTest --no-daemon --console=plain
```

Expected: FAIL because the current coordinator still runs the submission stage and returns `PARTIAL`.

- [ ] **Step 3: Implement the minimal change** — remove only `outcomes += syncRepository.syncSubmissions(account, force)` from `syncAccount`; retain the existing active-account checks after each remaining public stage.
- [ ] **Step 4: Run the focused test again** — expected PASS.
- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/ojnexus/judge/luogu/LuoguSyncCoordinator.kt app/src/test/java/com/ojnexus/judge/luogu/LuoguSyncCoordinatorTest.kt
git commit -m "fix: keep Luogu sync public-only / 修正洛谷仅同步公开数据"
```

### Task 2: Document and verify the corrected boundary

**Files:**
- Create: `docs/releases/v0.3.27.md`
- Modify: `README.md`
- Modify: `docs/ROADMAP.md`
- Modify: `app/src/main/java/com/ojnexus/judge/luogu/LuoguSyncCoordinator.kt` (KDoc only if needed)

- [ ] **Step 1: Add bilingual Phase 31 and Release notes** — explain that public sync is now successful when its four public stages succeed, while private submission history remains unsupported and local OpenApp submissions remain separate.
- [ ] **Step 2: Run `git diff --check` and full `clean test assembleDebug lintDebug`** — all must finish with `BUILD SUCCESSFUL`.
- [ ] **Step 3: Install and launch the existing emulator** — verify `com.ojnexus/.MainActivity` and no `FATAL EXCEPTION`; do not shut down the emulator.
- [ ] **Step 4: Commit, push `codex/phase-5-arena`, tag `v0.3.27`, create the GitHub Release with the verified APK, and compare local/remote commit and asset SHA-256.**
