# Plan: OpenApp credential refresh UX / OpenApp 凭据更换体验

**Goal:** Let users validate and replace an OpenApp credential without exposing the stored secret.

### Task 1: TDD input validation

- [ ] Add `OpenAppCredentialInputValidationTest` for trimmed valid input and both required-field errors.
- [ ] Run the focused test and verify RED because the validator is absent.

### Task 2: ViewModel and Settings UI

- [ ] Add the pure validator and local trim/reject behavior in `SettingsViewModel`.
- [ ] Add a ViewModel test proving invalid input does not write to the credential store.
- [ ] Add a localized replacement action and local editor visibility state; never read the stored secret.
- [ ] Run focused credential verification and Settings regression tests.

### Task 3: Verify and release

- [ ] Run `git diff --check` and `clean test assembleDebug lintDebug assembleRelease`.
- [ ] Install the signed Release APK over the existing emulator app without clearing data; verify package version, configured-panel replacement action, and online device state.
- [ ] Update bilingual README/Roadmap/Release notes, commit, push branch, tag, publish GitHub Release, and audit remote assets and SHA-256.
