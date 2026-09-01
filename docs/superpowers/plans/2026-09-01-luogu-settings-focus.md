# Plan: Luogu settings focus correction / 洛谷设置定位修正

**Goal:** Make the Dashboard Luogu first-use route scroll to the Luogu settings panel.

**Scope:** Pure local navigation behavior. Preserve OpenApp focus, ordinary Settings, data,
network, credential, and release boundaries.

### Task 1: Regression test first

- [x] Add `SettingsFocusTest` covering OpenApp focus, Luogu focus, no focus, and missing coordinates.
- [x] Run the focused test and verify the expected RED failure because the predicate is absent.

### Task 2: Minimal Compose guard correction

- [x] Add the pure predicate with the documented truth table.
- [x] Replace the existing `focusOpenApp`-only guard with the predicate.
- [x] Run the focused test and the existing Settings/ViewModel regression tests.

### Task 3: Verify and release

- [x] Run `git diff --check` and `clean test assembleDebug lintDebug assembleRelease`.
- [x] Install the signed Release APK over the existing emulator app without clearing data; verify version, Luogu focus route, and online device state.
- [x] Update bilingual README/Roadmap/Release notes, commit, push branch, tag, publish GitHub Release, and audit remote assets and SHA-256.
