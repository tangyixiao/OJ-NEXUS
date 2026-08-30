# Luogu Account Binding Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a real, public-handle-only Luogu account binding flow to OJ NEXUS without introducing login credentials or fake sync data.

**Architecture:** Add an isolated Luogu Retrofit API, rate-limited client, DTO and adapter. The adapter exposes only `ACCOUNT_BINDING`; the shared `JudgeAccountRepository` persists the verified canonical handle, while Settings hides sync actions for capabilities not provided by the adapter.

**Tech Stack:** Kotlin, Retrofit, kotlinx.serialization, Coroutines, Room existing account schema, Compose, JUnit.

**Spec:** `docs/superpowers/specs/2026-08-30-luogu-binding-design.md`

## Global Constraints

- Public username binding only; never ask for or store Luogu passwords, cookies, sessions, or CSRF tokens.
- Every judge-specific network type stays under `app/src/main/java/com/ojnexus/judge/luogu`.
- Luogu is `EXPERIMENTAL` and declares only `ACCOUNT_BINDING` until stable sync endpoints are separately designed.
- UI strings go through both `values/strings.xml` and `values-zh-rCN/strings.xml`.
- Preserve the existing single-active-account and local-history retention invariants.
- Run `tools\\gradlew-local.bat clean test assembleDebug lintDebug` before completion.

---

### Task 1: Lock the Luogu public search contract with tests

**Files:**
- Create: `app/src/test/java/com/ojnexus/judge/luogu/LuoguAdapterTest.kt`
- Create: `app/src/test/java/com/ojnexus/judge/luogu/LuoguAccountConnectorTest.kt`

**Interfaces:** The tests define the desired `LuoguUserSummary(uid: Long, name: String, avatar: String? = null, ...)`, `LuoguUserSearchResponse(users: List<LuoguUserSummary?>)`, and `LuoguAccountConnector.bind(rawHandle: String): AccountBinding` contract via the existing `JudgeAccountConnector` interface.

- [ ] **Step 1: Write the failing DTO and connector tests.** Assert the real JSON shape parses, a precise case-sensitive match returns `VERIFIED` and `EXPERIMENTAL`, whitespace is trimmed, a null/nonmatching result throws `AccountBindingError.NotFound`, and invalid/blank input throws `InvalidHandle`.
- [ ] **Step 2: Run the focused tests to verify RED.** Run `tools\\gradlew-local.bat :app:testDebugUnitTest --tests com.ojnexus.judge.luogu.LuoguAdapterTest --tests com.ojnexus.judge.luogu.LuoguAccountConnectorTest`; expect unresolved Luogu production types because the adapter contract is not implemented.
- [ ] **Step 3: Commit the failing tests.** Run `git add app/src/test/java/com/ojnexus/judge/luogu` and `git commit -m "test: define Luogu public account binding"`.

### Task 2: Implement the isolated Luogu transport and adapter

**Files:**
- Create: `app/src/main/java/com/ojnexus/judge/luogu/api/LuoguApi.kt`
- Create: `app/src/main/java/com/ojnexus/judge/luogu/api/dto/LuoguDtos.kt`
- Create: `app/src/main/java/com/ojnexus/judge/luogu/LuoguClient.kt`
- Create: `app/src/main/java/com/ojnexus/judge/luogu/LuoguAdapter.kt`
- Create: `app/src/main/java/com/ojnexus/judge/luogu/LuoguAccountConnector.kt`
- Create: `app/src/main/java/com/ojnexus/judge/luogu/LuoguUrls.kt`
- Test: `app/src/test/java/com/ojnexus/judge/luogu/LuoguAdapterTest.kt`

**Interfaces:** `LuoguApi.searchUsers(keyword: String): LuoguUserSearchResponse`, `LuoguAdapter.searchUser(handle: String): LuoguUserSummary?`, and `LuoguUrls.user(uid: Long): String`.

- [ ] **Step 1: Write the failing client/adapter tests.** Assert the adapter calls the search boundary, exposes `JudgeId.LUOGU`, reliability `EXPERIMENTAL`, exactly `ACCOUNT_BINDING`, and maps an empty user list/null candidate to null rather than throwing.
- [ ] **Step 2: Run the focused adapter test and verify RED.** Run the Luogu focused test command; expect missing production API/adapter symbols.
- [ ] **Step 3: Implement the serializable DTOs.** Keep optional public fields nullable/defaulted and do not add credentials or authenticated response fields.
- [ ] **Step 4: Implement the Retrofit endpoint and bounded client.** Use base URL `https://www.luogu.com.cn/`, `GET api/user/search`, URL-encoded `keyword`, one request at a time with the existing `RateLimitedRequestGate`, maximum three attempts for HTTP 429/5xx/network timeout, and typed parse/network errors. Never add cookies or an OkHttp cookie jar.
- [ ] **Step 5: Implement the adapter and connector against the interface.** Keep response parsing defensive: missing `users`, null candidates, or malformed required fields become a typed unavailable/not-found result; no first-result fuzzy binding. The connector trims input, validates `[A-Za-z0-9_]{1,20}`, requires exact `name == trimmed`, and returns a verified binding.
- [ ] **Step 6: Run the focused adapter and connector tests to verify GREEN.** Confirm all tests pass and no test uses the live network.
- [ ] **Step 7: Commit.** Run `git add app/src/main/java/com/ojnexus/judge/luogu` and `git commit -m "feat: add Luogu public account adapter"`.

### Task 3: Register Luogu and make the settings flow capability-aware

**Files:**
- Modify: `app/src/main/java/com/ojnexus/OjNexusApplication.kt`
- Modify: `app/src/main/java/com/ojnexus/feature/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/ojnexus/feature/settings/SettingsViewModel.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`
- Test: `app/src/test/java/com/ojnexus/judge/JudgeRegistryTest.kt`

**Interfaces:** Register `RetrofitLuoguAdapter` and `LuoguAccountConnector`; expose `JudgeCapability.ACCOUNT_BINDING` to the existing Settings panel; enqueue manual/periodic WorkManager jobs only when the connected judge declares `BACKGROUND_SYNC`.

- [ ] **Step 1: Add failing registry/settings behavior tests.** Assert a registry with Luogu resolves its adapter and connector, and a binding-only judge does not get routed to a sync coordinator.
- [ ] **Step 2: Run the focused registry test to verify RED.** Run `tools\\gradlew-local.bat :app:testDebugUnitTest --tests com.ojnexus.judge.JudgeRegistryTest`; expect the current registry fixture/registration behavior to lack Luogu support.
- [ ] **Step 3: Register the Luogu API/client/adapter/connector.** Reuse the existing shared OkHttp client but give Luogu its own rate gate; include it in the adapter and connector lists while leaving coordinator registration unchanged.
- [ ] **Step 4: Make Settings capability-aware and add localized Luogu copy.** Hide SYNC NOW for adapters without `BACKGROUND_SYNC`; guard WorkManager enqueue calls with the same capability. Add Luogu source/verification/error strings in both resource trees and keep parity tests green.
- [ ] **Step 5: Run focused registry, connector, and resource tests.** Expect GREEN and no untranslated new labels.
- [ ] **Step 6: Commit.** Run `git add app/src/main/java/com/ojnexus/OjNexusApplication.kt app/src/main/java/com/ojnexus/feature/settings app/src/main/res/values app/src/main/res/values-zh-rCN app/src/test/java/com/ojnexus/judge/JudgeRegistryTest.kt` and `git commit -m "feat: expose Luogu account binding in settings"`.

### Task 4: Verify the actual binding delivery surface

**Files:**
- Modify: `docs/OJ_ADAPTERS.md`
- Modify: `docs/MULTI_OJ.md`
- Modify: `README.md`
- Verify: `app/src/main/res/values-zh-rCN/strings.xml`

- [ ] **Step 1: Document that Luogu public binding is implemented and sync remains capability-gated.** Record the endpoint boundary, experimental reliability, no-credential rule, and why no sync button is shown yet.
- [ ] **Step 2: Run the complete verification command.** Run `tools\\gradlew-local.bat clean test assembleDebug lintDebug`; require `BUILD SUCCESSFUL`, zero test failures/errors, and a generated `app/build/outputs/apk/debug/app-debug.apk`.
- [ ] **Step 3: Install and exercise the APK on the Pixel_9 emulator.** Open Settings, confirm the LUOGU panel, enter a known public handle such as `kkksc03`, tap CONNECT, and verify `CONNECTED`, the canonical handle, `VERIFIED`, and no SYNC NOW action. Capture logcat after the action and require no fatal app exception.
- [ ] **Step 4: Verify failure behavior and existing OJ safety.** Use a blank/invalid handle, confirm an inline localized error and no account row; confirm existing Codeforces/AtCoder panels remain present and their sync action still appears.
- [ ] **Step 5: Audit delivery.** Run `git diff --check`, `git status --short`, inspect changed files for secrets, and compute the APK SHA-256. Commit documentation and only then report the actual APK path and commit IDs.
