# Windows review clock implementation plan

- [x] Confirm isolated worktree at `11555095e2a66c8dfc4dd9918118ed81d97720da`; create `codex/phase-123-review-clock` from it.
- [x] Read repository rules, clock/store/test code, and TDD instructions. The requested `docs/LONG_RANGE_PLAN_121_220.md` and referenced Phase 121–125 kickoff plan are absent at this base revision.
- [x] Run existing focused regression test and record expected RED.
- [x] Add fixed-clock tests for before-due, due-day, and next-day behavior, using the mark instant as the clock baseline.
- [x] Inject optional `IClock` into `SqliteSyncStore`; preserve one-argument construction and explicit mark time.
- [x] Re-run focused RED/GREEN and full solution restore, test, and Release build.
- [x] Run CLI and WPF UI smoke checks; document that existing scripts do not cover review.
- [x] Record evidence and limits in `docs/verification/phase-123-review-clock.md`.
- [x] Review scoped diff/secrets, stage explicit files, and commit without pushing or publishing.
