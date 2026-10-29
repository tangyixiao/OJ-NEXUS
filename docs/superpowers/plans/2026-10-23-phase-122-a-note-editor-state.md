# Phase 122-a: Note editor state regression plan

- [x] Confirm task scope, protected checkouts, repository rules, and Phase 122 plan context.
- [x] Write the behavior specification before changing production or test code.
- [x] Add a Robolectric regression test using the real ViewModel, Room database, and repositories.
- [x] Run the focused test against the old implementation and record the expected assertion failure (RED).
- [x] Make the smallest state-flow change that exposes draft and saving updates while seeding storage only once.
- [x] Run focused ViewModel tests and confirm the new regression passes (GREEN).
- [x] Add/adjust checks for consecutive edits, debounce persistence, and protection from later upstream emissions.
- [x] Run `test`, `assembleDebug`, and `assembleRelease`; verify fresh XML counts and APK outputs.
- [x] Record exact evidence, limitations, and required device revalidation in `docs/verification/phase-122-note-state.md`.
- [x] Inspect the scoped diff and secrets, stage only named files, and create one scoped commit; do not push or publish.
