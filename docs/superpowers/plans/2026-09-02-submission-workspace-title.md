# Submission workspace title restoration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Preserve the locally stored Luogu problem title when the submission center opens its related workspace.

**Architecture:** Extend the existing submission-center callback with a nullable title and feed it into the existing encoded workspace route. PID remains the required identity and blank titles use the existing PID-only route.

**Tech Stack:** Kotlin, Jetpack Compose, Navigation Compose, JUnit, existing Room entity and route helpers.

**Spec:** `docs/superpowers/specs/2026-09-02-submission-workspace-title-design.md`

## Global Constraints

- Do not add main-site passwords, cookies, sessions, CSRF state, cloud sync, local compilation, custom-input execution, or automatic submission POST retry.
- Do not add a database migration or network field; this phase consumes the v0.3.45 local title only.
- Keep all user-visible copy in `strings.xml` and retain English/简体中文 behavior.
- Verify with `git diff --check` and `.\tools\gradlew-local.bat clean test assembleDebug lintDebug assembleRelease --no-daemon --rerun-tasks --console=plain`.

---

### Task 1: Lock the navigation contract with tests

**Files:**
- Test: `app/src/test/java/com/ojnexus/app/NexusRoutesTest.kt`
- Test: `app/src/test/java/com/ojnexus/feature/submissions/SubmissionCenterNavigationTest.kt`

**Interfaces:**
- Consumes: `NexusRoutes.workspace(pid: String, title: String?)` and the intended submission callback shape.
- Produces: failing tests that require title-aware submission-center navigation while preserving PID-only behavior.

- [x] **Step 1: Write the failing test**

  Add a small pure callback adapter test that calls `submissionWorkspaceContext("P1001", "A+B")`
  and expects `SubmissionWorkspaceContext("P1001", "A+B")`; add a null-title case expecting the
  same PID with null title. The production helper is intentionally absent at this point.

- [x] **Step 2: Run the focused test to verify it fails**

  Run `.\tools\gradlew-local.bat :app:testDebugUnitTest --tests com.ojnexus.feature.submissions.SubmissionCenterNavigationTest --no-daemon --rerun-tasks --console=plain`.
  Expected: compilation fails because `submissionWorkspaceContext` and its context type do not yet exist.

- [x] **Step 3: Implement the smallest production contract**

  Add the internal data type/helper in the submission feature and change the composable callback to
  pass `job.pid.orEmpty()` plus the trimmed nullable `job.title`. Update `NexusApp` to accept both
  values and call `NexusRoutes.workspace(pid, title)`.

- [x] **Step 4: Run focused tests to verify they pass**

  Run the same focused test plus `com.ojnexus.app.NexusRoutesTest`; expect all selected tests to pass.

### Task 2: Preserve UI and bilingual release documentation

**Files:**
- Modify: `README.md`
- Modify: `docs/ROADMAP.md`
- Create: `docs/releases/v0.3.46.md`

**Interfaces:**
- Consumes: the title-aware callback behavior from Task 1.
- Produces: bilingual phase and release explanation without deleting prior notes.

- [x] **Step 1: Update bilingual documentation**

  Document that Submission Center → workspace retains local title context, while PID remains the
  identity and blank titles remain PID-only. State that no new network, credential, database, or
  cloud behavior was added.

- [x] **Step 2: Check documentation and diff hygiene**

  Run `git diff --check` and scan the new phase/release text for `TODO`, `TBD`, or contradictory
  security-boundary claims.

### Task 3: Gate, install, publish, and audit

**Files:**
- Create: `docs/releases/SHA256SUMS-v0.3.46.txt`
- Build artifact: `app/build/outputs/apk/release/OJ-NEXUS-v0.3.46.apk`

**Interfaces:**
- Consumes: verified implementation and bilingual docs from Tasks 1–2.
- Produces: signed, checksum-verified GitHub Release v0.3.46 and a clean remote branch/tag.

- [x] **Step 1: Run the full fresh gate**

  Run `.\tools\gradlew-local.bat clean test assembleDebug lintDebug assembleRelease --no-daemon --rerun-tasks --console=plain` and require `BUILD SUCCESSFUL`.

- [x] **Step 2: Sign and verify the Release APK**

  Sign `app-release-unsigned.apk` with the existing local debug keystore using Build Tools 37.0.0,
  verify v2/v3 signatures with `apksigner verify --verbose`, compute SHA-256, and write the exact
  digest to `SHA256SUMS-v0.3.46.txt`.

- [x] **Step 3: Install and smoke-test without reset**

  Run `adb install -r app/build/outputs/apk/release/OJ-NEXUS-v0.3.46.apk`, launch the app, open
  the Submission Center, and check package version, running PID, and `AndroidRuntime:E` output.
  Do not call `pm clear`, `adb uninstall`, emulator wipe, or shutdown.

- [x] **Step 4: Commit, push, tag, and create Release**

  Use bilingual commit text `release: restore submission workspace titles v0.3.46 / 恢复提交工作区题名`;
  push the branch, create annotated tag `v0.3.46`, and upload the signed APK and checksum with
  `gh release create` using `docs/releases/v0.3.46.md` as notes.

- [x] **Step 5: Audit remote identity**

  Compare local HEAD with `git ls-remote` branch SHA, compare the peeled tag with the release
  commit, compare the GitHub asset digest with the local APK digest, confirm the release is public
  and non-draft/non-prerelease, then require a clean `git status`.
