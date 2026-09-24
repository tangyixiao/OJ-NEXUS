# Plan: Transaction-safe OpenApp credential replacement / 事务安全的 OpenApp 凭据更换

**Goal:** Rotate an OpenApp credential without exposing or losing the existing value.

### Task 1: Candidate verification contract (TDD)

- [x] Add a candidate-verification fake and tests for success, authorization rejection, and network failure.
- [x] Add the failing test for write-after-verification ordering and verify RED.
- [x] Add `LuoguOpenCredentialVerifier` and implement it in the real OpenApp client.
- [x] Run the focused OpenApp tests.

### Task 2: Safe settings replacement flow

- [x] Add a ViewModel replacement action that verifies before writing and preserves the old credential on failure.
- [x] Add replacement/cancel state to the settings editor without reading the stored secret.
- [x] Add a ViewModel regression test for old-value preservation and successful replacement.
- [x] Run focused settings and credential tests.

### Task 3: Verify and release

- [x] Run `git diff --check` and `clean test assembleDebug lintDebug assembleRelease`.
- [x] Install the signed Release APK over the existing emulator app without clearing data; verify package version, first-use editor, and device state. Replacement/cancel state is covered by focused tests because no real OpenApp credential is configured on the emulator.
- [x] Update bilingual README/Roadmap/Release notes, commit, push branch, tag, publish GitHub Release, and audit remote assets and SHA-256.
