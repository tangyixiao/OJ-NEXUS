# Plan: Transaction-safe OpenApp credential replacement / 事务安全的 OpenApp 凭据更换

**Goal:** Rotate an OpenApp credential without exposing or losing the existing value.

### Task 1: Candidate verification contract (TDD)

- [ ] Add a candidate-verification fake and tests for success, authorization rejection, and network failure.
- [ ] Add the failing test for write-after-verification ordering and verify RED.
- [ ] Add `LuoguOpenCredentialVerifier` and implement it in the real OpenApp client.
- [ ] Run the focused OpenApp tests.

### Task 2: Safe settings replacement flow

- [ ] Add a ViewModel replacement action that verifies before writing and preserves the old credential on failure.
- [ ] Add replacement/cancel state to the settings editor without reading the stored secret.
- [ ] Add a ViewModel regression test for old-value preservation and successful replacement.
- [ ] Run focused settings and credential tests.

### Task 3: Verify and release

- [ ] Run `git diff --check` and `clean test assembleDebug lintDebug assembleRelease`.
- [ ] Install the signed Release APK over the existing emulator app without clearing data; verify replacement/cancel labels and device state.
- [ ] Update bilingual README/Roadmap/Release notes, commit, push branch, tag, publish GitHub Release, and audit remote assets and SHA-256.
