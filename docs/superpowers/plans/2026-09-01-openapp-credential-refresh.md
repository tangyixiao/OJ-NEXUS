# Plan: OpenApp credential refresh UX / OpenApp 凭据更换体验

**Goal:** Make first-time OpenApp credential validation actionable without exposing secrets.

### Task 1: TDD input validation

- [x] Add `OpenAppCredentialInputValidationTest` for trimmed valid input and both required-field errors.
- [x] Run the focused test and verify RED because the validator is absent.

### Task 2: ViewModel and Settings UI

- [x] Add the pure validator and local trim/reject behavior in `SettingsViewModel`.
- [x] Add a ViewModel test proving invalid input does not write to the credential store.
- [x] Add localized field-specific input errors; keep the existing configured/clear flow and never read the stored secret.
- [x] Run focused credential verification and Settings regression tests.

### Task 3: Verify and release

- [x] Run `git diff --check` and `clean test assembleDebug lintDebug assembleRelease`.
- [x] Install the signed Release APK over the existing emulator app without clearing data; verify field-specific setup errors and online device state.
- [ ] Update bilingual README/Roadmap/Release notes, commit, push branch, tag, publish GitHub Release, and audit remote assets and SHA-256.
