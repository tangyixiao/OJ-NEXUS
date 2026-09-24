# Luogu Rating History Fallback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Preserve Luogu public Rating history when the practice endpoint returns an empty `elo` array.

**Architecture:** Keep the current `LuoguSyncRepository` two-request flow and change only the source-selection rule. A focused repository regression test models the live response shape; no new network or persistence layer is introduced.

**Tech Stack:** Kotlin, Room, Coroutines/Flow, JUnit, Robolectric.

**Spec:** `docs/superpowers/specs/2026-09-01-luogu-rating-history-fallback-design.md`

## Global Constraints

- Luogu synchronization remains public-data-only.
- Do not add passwords, cookies, sessions, CSRF login, cloud accounts, or cross-device sync.
- Do not fabricate submission attempts or alter the OpenApp submission workflow.
- Preserve bilingual README, roadmap, and release explanations.

### Task 1: Add regression coverage and fix source selection

**Files:**
- Modify: `app/src/main/java/com/ojnexus/judge/luogu/LuoguSyncRepository.kt`
- Modify: `app/src/test/java/com/ojnexus/judge/luogu/LuoguSyncRepositoryTest.kt`

**Interfaces:**
- `syncRating(account, force)` persists rows from non-empty practice `elo`, otherwise profile `elo`.

- [ ] **Step 1: Write the failing regression test**

Configure the fake adapter with one profile `LuoguEloEntryDto` and an empty practice list;
call `syncRating`; assert the stage succeeds and the one profile contest row is persisted.

- [ ] **Step 2: Run the focused test and observe the failure**

Run `.\tools\gradlew-local.bat testDebugUnitTest --tests com.ojnexus.judge.luogu.LuoguSyncRepositoryTest --no-daemon --console=plain`.
Expected: the new test fails because the old implementation chooses the empty practice list.

- [ ] **Step 3: Implement the minimal fallback**

Replace the nullable-coalescing selection with:

```kotlin
val entries = practicePage.data?.elo?.takeIf { it.isNotEmpty() }
    ?: profilePage.data?.elo.orEmpty()
```

- [ ] **Step 4: Run the focused test and existing Luogu tests**

Run the same command and confirm `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit the fix**

Run `git add app/src/main/java/com/ojnexus/judge/luogu/LuoguSyncRepository.kt app/src/test/java/com/ojnexus/judge/luogu/LuoguSyncRepositoryTest.kt && git commit -m "fix: preserve Luogu rating history fallback / 修复洛谷 Rating 历史回退"`.

### Task 2: Document, build, and publish

**Files:**
- Modify: `README.md`
- Modify: `docs/ROADMAP.md`
- Create: `docs/releases/v0.3.31.md`

- [ ] **Step 1: Add bilingual Phase 35 notes**

State that the public profile `elo` is used when practice `elo` is empty, preserve all prior
phase notes, and repeat the no-password/no-cookie/no-cloud boundary.

- [ ] **Step 2: Run the full gate**

Run `git diff --check; .\tools\gradlew-local.bat clean test assembleDebug lintDebug --no-daemon --rerun-tasks --console=plain` and require `BUILD SUCCESSFUL`.

- [ ] **Step 3: Install and inspect the existing emulator**

Install `app\build\outputs\apk\debug\app-debug.apk`, launch `com.ojnexus/.MainActivity`,
inspect the Settings/Profile surfaces, and confirm `emulator-5554` remains online.

- [ ] **Step 4: Publish v0.3.31**

Push the branch, create and push tag `v0.3.31`, attach the APK with `gh release create`, and
verify Release visibility, tag target, remote branch SHA, and asset digest.
