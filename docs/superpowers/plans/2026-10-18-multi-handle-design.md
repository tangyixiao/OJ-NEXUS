# Plan — Multi-Handle Per Judge, Design-Gated (Phase 97)

1. Verify ground truth in code (unique index, sync_states PK, work identity, generation). DONE —
   anchors recorded in the spec.
2. Write spec `docs/superpowers/specs/2026-10-18-multi-handle-design.md` with stage split,
   generation decision, and the 8-step v14 migration test plan. DONE.
3. Prototype `core/data/sync/MultiHandlePrototype.kt` — pure planner (ReuseExisting /
   AddSecondary / ReplaceActive at the 2-handle cap, work staleness by account existence,
   switch removal list). NOT wired into production flows. DONE.
4. Tests `MultiHandlePrototypeTest` — 6 tests covering all decisions + judge scoping +
   disabled-account work semantics. DONE.
5. Gates `test assembleDebug`, versionCode 95/0.3.95, ROADMAP prepend, plan ✅ mark, commit feat:.

## Result

SHIPPED (v0.3.95). Spec with verified ground-truth anchors + 8-step v14 migration test plan;
`MultiHandlePrototype.kt` pure planner (ReuseExisting / AddSecondary / ReplaceActive at cap 2,
`isWorkStaleForAccount` by account existence, `accountsToRemoveOnSwitch`), 6 unit tests. Not
wired into production `connect()`. Gates: `test assembleDebug` BUILD SUCCESSFUL 1m32s; 160
suites / 729 tests / 0 failures. versionCode 95 / 0.3.95; ROADMAP "MULTI-HANDLE PER JUDGE —
DESIGN GATE" prepended; docs/LONG_RANGE_PLAN.md PHASE 97 ✅ with outcome note.
