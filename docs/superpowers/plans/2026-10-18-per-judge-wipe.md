# PLAN — Phase 88: Per-Judge Data Wipe (2026-10-18)

1. DAO queries: `ProblemDao.deleteByJudge`; add `deleteByJudge` to `RemoteProblemDetailDao`,
   `SubmissionJobDao`, `WorkspaceDraftDao`, `ContestProblemMarkerDao`, `SyncOperationDao`.
2. `JudgeAccountRepository`: add `dataGenerationProvider: () -> String = { "" }` ctor param
   (default keeps old call sites compiling), `WIPE_LEDGER_STATUS = "WIPE"` companion const,
   `wipeJudgeData(judge, accountId)` transaction (11 delete calls + WIPE ledger row).
   Wire `AppContainer` to pass `::currentDataGeneration`.
3. `SettingsViewModel`: `wiping`/`wipeFailed` StateFlows + `wipeJudgeData(account)`.
4. `SettingsScreen`: WIPE DATA `SettingsAction` in `JudgeConnectionPanel`, typed-confirm
   `AlertDialog` (confirm enabled only when typed text == judge displayName, case-insensitive),
   `DialogText` gains `enabled` param; wipe-failure inline text.
5. Strings `settings_wipe_data/wiping/wipe_failed/wipe_title/wipe_body/wipe_confirm_action`
   EN + zh-rCN.
6. Test `JudgeWipeRepositoryTest` (Robolectric): cross-judge isolation, WIPE ledger entry
   (generation + timestamp), wrong-account-id rejection leaves data untouched.
7. Gates: full `test assembleDebug` BUILD SUCCESSFUL; bump versionCode 85→86 / versionName
   0.3.85→0.3.86; ROADMAP entry; mark plan ✅; commit `feat:`.
