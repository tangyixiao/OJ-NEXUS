# Phase 122 — Android regression evidence

Current status: **VERIFIED for the Phase 122 API 37 dedicated-emulator regression scope**, on combined commit `19f0bbcb74cdc2c729874fc5795ff8ab1bc47d24`. D1/D2/D3 were repaired and the final build, 785 unit tests, 33 connected tests, note/review restart and WAL-complete snapshot/preview-cancel checks passed. Physical-device, TalkBack, battery and older SQLite compatibility remain separate debts. Earlier ACTIVE/failure sections below are historical evidence; the final acceptance section supersedes their outstanding-item status. No source/version changes were made by the verification lane.

## Host and device

- Host: Windows 10.0.26300, Android Studio JBR at `D:/Android Studio/jbr`; Android SDK `D:/Android`.
- Initial `adb devices -l` and emulator `-list-avds` were empty. Created dedicated `OJNexus_Phase122_Temp` from installed `system-images;android-37.1;google_apis_playstore_ps16k;x86_64`, pixel_7 profile. Only `emulator-5580` was targeted. No physical user device/data was touched.
- WHPX acceleration check returned 0/usable; emulator log also reported accelerator operational. Boot completed; shell API 37, model sdk_gphone16k_x86_64, system default en-US (`persist.sys.locale` empty, `ro.product.locale=en-US`), 1080x2400, 420dpi, font scale 1.0. No locale, font or size override was applied.
- Headless emulator command: `D:/Android/emulator/emulator.exe -avd OJNexus_Phase122_Temp -port 5580 -no-window -no-audio -no-snapshot -gpu swiftshader -memory 2048` (launcher PID 2716; qemu PID 48944).
- Wi-Fi/mobile data disabled only on this disposable AVD for offline checks. Restoration/shutdown is recorded below when complete. Instrumentation seeds/clears its own synthetic database; seeded DEMO rows are never described as real judge responses.

## Fresh gates

| Command from repository root | Result | Evidence |
| --- | --- | --- |
| `tools/gradlew-local.bat test assembleDebug assembleRelease` | Exit 0; BUILD SUCCESSFUL in 3m23s; debug unit task initially UP-TO-DATE | `%TEMP%/ojnexus-phase122-gradle-20261023.log` |
| `tools/gradlew-local.bat testDebugUnitTest --rerun` | Exit 0; BUILD SUCCESSFUL in 1m53s; test task actually executed | `%TEMP%/ojnexus-phase122-fresh-debug-tests-20261023.log` |
| `$env:ANDROID_SERIAL='emulator-5580'; tools/gradlew-local.bat connectedDebugAndroidTest` | Exit 0; BUILD SUCCESSFUL in 6m13s; device tests 66.474s | `%TEMP%/ojnexus-phase122-connected-20261023.log` |

Fresh unit XML: 170 suites, **779 tests, 0 failures, 0 errors, 0 skipped**, rewritten at 20:51:34 +08:00. Source `app/build/test-results/testDebugUnitTest`; retained copy `%TEMP%/ojnexus-phase122-evidence-20261023/testDebugUnitTest`.

Fresh connected XML root: **33 tests, 0 failures, 0 errors, 0 skipped**, timestamp 2026-10-23T12:56:39Z. Source `app/build/outputs/androidTest-results/connected/debug/TEST-OJNexus_Phase122_Temp(AVD) - 17-_app-.xml`; retained copy `%TEMP%/ojnexus-phase122-evidence-20261023/androidTest-results`. Covers the 10 existing Compose classes, including library-to-session creation/cancel/quick-verdict integration, note-index rendering/filter callbacks, narrow large-font layout, connector failure display and training actions. Component fixtures do not prove complete live network or note-edit persistence.

A preliminary `testDebugUnitTest --rerun testReleaseUnitTest --rerun` attempt failed at task selection because `testReleaseUnitTest` does not exist in this project; no tests ran in that attempt. The corrected fresh debug run above passed. Log `%TEMP%/ojnexus-phase122-fresh-tests-20261023.log`.

Current Room schema is **15** (`OJ_NEXUS_SCHEMA_VERSION`). Fresh `MigrationTest` has 11 passing tests, and `RoomIndexAuditTest` has 2. Although several test names/comments mention older destinations, they instantiate current `OjNexusDatabase` and supply the chain including 14→15, so Room validates the migrated schema against v15. The v1 chain includes 1→2 through 14→15; other historical starting versions also reach v15. This is JVM/Robolectric migration evidence, not an installed-old-APK upgrade claim. Dedicated per-table v14→15 historical-data migration coverage was not added in this lane.

Built APK SHA256 before any defect repair:

- debug `D1AF6D3A40AD7128AB65B2F3755A39CB01917A9B5424F0B2AB5B34E17741E662`
- release unsigned `1D5421E58FF4DECB8910EB472DE4D0D0F0A97F4948E42682184531B67E1C5248`

Release output is `app-release-unsigned.apk`; this is not signing or distribution acceptance. Any later source fix requires fresh affected gates and replacement artifact identity.

## Runtime observations

ADB commands always included `-s emulator-5580`. XML was obtained with `shell uiautomator dump /sdcard/phase122-*.xml` then `adb pull` to `%TEMP%/ojnexus-phase122-*.xml`. These are concrete accessible-tree observations and scoped input actions, not a claim of full visual/manual acceptance.

| Path | Observed result and limitation | Local evidence |
| --- | --- | --- |
| Offline cold launch | After `svc wifi disable` and `svc data disable`, install and `am start -W -n com.ojnexus/.MainActivity` succeeded (COLD, Status ok). Dashboard exposed LOCAL MODE — READY, navigation and local system status. | `ojnexus-phase122-dashboard.xml` |
| Offline local library/detail | Empty library rendered; INSERT DEMO DATA produced 8 problems, 5 solved, 2 reviews. CF1029E detail displayed synthetic attempts/review and opened offline. | `ojnexus-phase122-problems.xml`, `...-seeded.xml`, `...-detail.xml` |
| Navigation/training | Existing connected integration passed editable library→training, cancel leaves no active session, start session and quick WA refreshes session count. Shared bottom-tab test passed all destinations. | Connected XML |
| Notes edit/reopen | **FAILED**: focused KEY INSIGHT does not retain entered characters; see D1. Force-stop/reopen succeeds but empty text is not a persistence pass. | `...-notes2.xml`, `...-edited.xml`, `...-notes-reopened.xml`, `...-notes.png` |
| Input control | Identical `adb shell input text phase122` works in library search, where XML contains phase122. This controls for the input method/ADB path. | `...-search-input.xml` |
| Offline remote catalog | With no remote cache, displays NO CACHED RESULTS — ENTER A KEYWORD and remains usable; no populated offline remote cache was available. | `...-offline-catalog.xml`, `...-catalog-search.xml` |
| Offline public quota error | Settings PROBE returns QUOTA PROBE FAILED — TAP RETRY while Wi-Fi/mobile_data are both 0. This is a quota-error action, not a full connected-account sync-failure run. | `...-probe-result.xml` |
| Import picker cancel | IMPORT DATABASE BACKUP opened system Downloads/Recent picker; Back returned to Settings with 8 problems/16 attempts/2 reviews and no import-result change. | `...-import-picker.xml`, `...-import-canceled.xml` |
| Export/reimport preview | **FAILED**: exported .zip contains raw SQLite, then selecting it stages legacy restore without archive preview; see D2. Only the dedicated synthetic AVD was affected. | `...-export-picker.xml`, `...-export-result.xml`, `...-snapshot.zip`, `...-import-preview.xml` |

The connected task uninstalls its target/test APKs on completion; the debug APK was reinstalled afterward for manual CLI observations. An attempted UI dump during the transition to instrumentation could not reach idle; that dump is not counted as evidence. All 33 connected tests nevertheless finished successfully; final persistence/backup checks must be rerun after repairs.

## Reproducible defects for the implementation lane

**D1 — Note input state does not emit synchronously.**

1. Fresh dedicated AVD, offline; install debug APK; Problems → INSERT DEMO DATA → CF1029E.
2. Scroll to KEY INSIGHT; tap its EditText; XML confirms focused=true.
3. `adb shell input text PHASE122_PERSISTENCE_20261023` (and a single `input keyevent 29`) leaves the field empty and status SAVED. Control input into library search succeeds.
4. Read-only `run-as com.ojnexus` database copy confirms `PRAGMA user_version=15`; `problem_notes` contains `(problem_id=1, key_insight='', implementation_notes='', complexity='', general='')`.

Source diagnosis: `ProblemDetailViewModel.state` combines only repository detail, knowledge and note template; it reads `notesDraft.value` and `notesSaving.value` without collecting either flow. `setNotesField` updates the private draft but the controlled `BasicTextField.value` is not emitted immediately, allowing input to reset. Implementer must verify this with a regression and runtime retest rather than treating the proposed cause as a passed fix.

**D2 — Snapshot export action calls the legacy raw export.**

1. Profile → SETTINGS → EXPORT DATABASE BACKUP. System CreateDocument suggests `oj-nexus-snapshot.zip`; UI promises database + manifest.
2. SAVE gives BACKUP EXPORTED and creates 385024-byte file.
3. Pull file: Python `zipfile.is_zipfile(path)` is False; magic bytes are `SQLite format 3\x00`.
4. IMPORT DATABASE BACKUP → choose the exported file. UI immediately shows BACKUP VERIFIED — RESTART APP TO RESTORE, with no snapshot conflict preview/cancel.

Source diagnosis: `SettingsViewModel.exportBackup` calls `backupRepository.exportTo`, which writes raw SQLite. `BackupRepository.exportSnapshot` already writes manifest.json/database.db ZIP but is not connected here. Legacy raw imports intentionally bypass preview, making this wrong export path also prevent the advertised cancel experience. A same-data legacy restore was staged only inside the disposable AVD; no user data was modified.

## Remaining acceptance

- Repair D1/D2, rerun affected unit/build/device checks and update APK hashes.
- Demonstrate real typed note remains visible immediately, debounce saves, force-stop/reopen preserves marker.
- Export actual archive, open import preview and cancel; compare active data/generation and verify restart does not apply a canceled restore.
- Exercise review change and reopen, and account sync failure with a synthetic public handle; current evidence covers review display and quota error only.
- Full visual/a11y/device-performance and physical-device acceptance remain outside this scoped CLI run. Existing large-font Compose tests are component evidence; no font/locale changes were made to the emulator system.
- Restore temporary network settings and stop the dedicated emulator when follow-up acceptance finishes. Preserve logs/AVD for repair retests.

## Combined-fix acceptance — 2026-10-23 21:34–21:45 +08:00

Status remains **ACTIVE**. D1 and D2 reproduce as fixed in the combined APK, but a newly demonstrated snapshot/WAL data-loss defect D3 prevents Phase 122 verification.

Implementation identity supplied by the coordinating lane: notes `d6cd74d0ac916697bd65b47269722fa4eaff5129`, snapshot wiring `72ef5add5b2a1461743a96f3a910c5132a08dc97`, combined acceptance commit `03948a9f0709b77346caa2b65d2d8cf884582b24`. This local merge is an acceptance composition; it is not a merge into main. Worktree: `C:/Users/tangy/.codex/worktrees/phase-122-note-state/AndroidAppCoding`.

- Coordinator gate: `test assembleDebug assembleRelease assembleDebugAndroidTest`, exit 0 / BUILD SUCCESSFUL in 2m9s. Log `%TEMP%/phase122-combined-gates.log`. XML independently counted and copied: **172 suites / 783 tests / 0 failures / 0 errors / 0 skipped**, fresh at 21:34:08. Preserved `%TEMP%/phase122-fixed-evidence/combined-03948a9-unit`.
- Device validator gate in that worktree: set `JAVA_HOME=D:/Android Studio/jbr`, `ANDROID_HOME=D:/Android`, `ANDROID_SERIAL=emulator-5580`; `gradlew.bat connectedDebugAndroidTest`. Exit 0 / BUILD SUCCESSFUL in 2m9s; all **33 connected tests passed**, no failures/skips. Log `%TEMP%/phase122-fixed-connected.log`; reports preserved `%TEMP%/phase122-fixed-evidence/connected-03948a9`.
- Initial WT invocation could not find the untracked machine-local helper; a direct invocation without ANDROID_HOME then failed SDK discovery before tests. Corrected process-local environment above passed; no machine path was written to repository configuration.
- Combined debug APK SHA256: `356CA20F11CD2D38F121C4710ECE0847B10D631B7FE43B38C861232DFE8A3D90`, version 1.0.3/code105. Installed only after coordinator readiness, after connected testing had finished and uninstalled its target.

Before the fresh connected run, restarting the prior legacy staged restore resulted in **7 problems / 15 attempts / 2 reviews / 0 notes**. This differed from prior live 8/16 counts. That old discrepancy remains an observation, with a mechanism consistent with D3 below; its exact historical cause is not claimed conclusively proved. Connected testing then removed that test app data; reinstall and the UI INSERT DEMO DATA action produced a new **stable 8 problems / 16 attempts / 2 reviews** baseline. A transient seeding frame showed mixed counts before the next stable dump; only the stable frame is used for acceptance.

### Fixed D1: actual input and process restart

Focused KEY INSIGHT on CF1029E, then entered `PHASE122` and continuously appended `_CONTINUOUS_20261023` through the same ADB input path that failed before. XML after each action contains the complete respective text; status SAVED is visible. After `am force-stop com.ojnexus` and COLD restart, reopening the detail still exposes `PHASE122_CONTINUOUS_20261023` and SAVED. This is actual device input/restart evidence, not merely the ViewModel regression test. Device dump latency exceeds the debounce interval, so this demonstrates immediate usability after input and eventual SAVED, not a measured sub-600ms visual timing guarantee.

Evidence: `%TEMP%/phase122-fixed-notes-first.xml`, `...-fixed-notes-continuous.xml`, `...-fixed-notes-saved.xml`, `...-fixed-notes-reopened.xml`, `...-fixed-notes.png`.

### Additional live paths

- CF1029E local review PASS changed STAGE 1 / DUE 10-24 to STAGE 2 / DUE 10-26. After later process restart it remained STAGE 2 / DUE 10-26; live `review_log` count is 1. Evidence `phase122-fixed-review-passed.xml`, `phase122-fixed-after-cancel-notes.xml`.
- Public Codeforces handle `tourist` entered into the real Settings CONNECT control while Wi-Fi and data were disabled. The action returned **API ERROR** and remained **NOT CONNECTED**. This is a real offline connector failure; no credentials were supplied and no successful judge output is claimed. Evidence `phase122-fixed-handle-entered.xml`, `phase122-fixed-offline-connect-result.xml`.
- Existing 33 connected tests again cover the local library/session start, cancel and quick result route. A separate post-fix manual session write was not completed because D3 caused acceptance to switch back to read-only investigation.

### Fixed D2 and newly exposed D3

The UI export now creates a real ZIP: `%TEMP%/phase122-fixed-snapshot.zip`, 8235 bytes, magic `PK 03 04`, exactly `manifest.json` and `database.db`. Manifest schema_version=15 and generation matches the live generation. Selecting **oj-nexus-snapshot (1).zip** in Downloads opens an actual **IMPORT PREVIEW**, with CANCEL and CONFIRM IMPORT. The old raw export remains separately named oj-nexus-snapshot.zip on the disposable AVD and was not selected in this fixed check.

However, the new archive demonstrably omits current committed WAL data:

| Table/data | Archive database + manifest | Live database at preview |
| --- | --- | --- |
| problems | 7 | 8 |
| attempts | 15 | 16 |
| problem_notes | 0, no marker | 1, complete marker |
| review_log | 0 | 1 |
| training_tasks | 0 | 3 |
| reviews | 2 | 2 |

Live `PRAGMA journal_mode` is **wal**, schema15. The actual UI preview itself displays PROBLEMS 7→8, ATTEMPTS 15→16, PROBLEM_NOTES 0→1, REVIEW_LOG 0→1, TRAINING_TASKS 0→3. This is observed data loss in the produced snapshot, not an inference solely from SQL source.

**D3 source diagnosis for implementation:** `core/data/repository/BackupRepository.kt` lines 263–285, `createSnapshotFile()`: line266 issues `query("PRAGMA wal_checkpoint(TRUNCATE)").use { }` without reading the cursor; lines274–276 similarly issue `query("VACUUM INTO '$escapedPath'").use { }`; line277 tests whether snapshot exists; lines279–284 fall back to copying only the main database. Android query cursors can be lazy, so not stepping them can leave both checkpoint and VACUUM unexecuted. The exact repair must prove real WAL mode export includes the latest rows and handles checkpoint failure rather than blindly copying the main file. This source mechanism is a diagnosis to validate in regression tests; the live/archive discrepancy above is already confirmed.

CANCEL was exercised on the actual preview. Before and after cancellation, generation was `8dd213a5-f134-4191-a164-d5984bf140d1`. `run-as com.ojnexus ls files` showed only `oj-nexus-data-generation` and `profileInstalled`, with **no pending restore/journal**. After COLD process restart, live counts remained 8/16/2 reviews/1 review_log/3 tasks/1 note, full marker and STAGE2 persisted. Thus preview cancellation worked for this older-data archive; snapshot fidelity itself remains failed. No confirmation/import was performed for this archive.

Evidence under `%TEMP%`:

- `phase122-fixed-snapshot.zip`, `phase122-fixed-manifest.json`, `phase122-fixed-export.sqlite` (extracted archive database).
- `phase122-fixed-live.sqlite` + `-wal` (read-only adb copy; local inspection may create a local shm sidecar).
- `phase122-fixed-after-cancel.sqlite` + `-wal`, `phase122-fixed-after-cancel-state.json` (schema/journal_mode/counts/full marker).
- `phase122-fixed-import-preview.xml`, `phase122-fixed-import-canceled.xml`, `phase122-fixed-after-cancel-notes.xml`, `phase122-fixed-cancel.png`.

The dedicated AVD remains online, offline network settings retained for retest. No physical device was touched. Need D3 repair and fresh unit/build/archive/device acceptance before VERIFIED. TalkBack, battery, physical-device performance and signed-installation acceptance have not been performed; the scoped emulator result will not erase those wider debts.


## Final acceptance — combined commit 19f0bbc

**VERIFIED for Phase 122's current API 37 dedicated-AVD regression scope**, 2026-10-23. This does not claim all Android versions, physical-device performance, TalkBack or battery acceptance. The full failure-to-fix history above is retained so the original defects and their evidence remain reviewable.

### Final build identity and host gates

- Acceptance commit `19f0bbcb74cdc2c729874fc5795ff8ab1bc47d24`, containing the notes/state repair, snapshot UI wiring and D3 `01f4608b0273e5cd067feaada6f909589c616e31` WAL snapshot repair. The final D3 implementation directly executes VACUUM INTO and does not require a checkpoint or copy the main database file. No checkpoint/busy-path coverage is claimed for the final code.
- Coordinator ran `testDebugUnitTest --rerun test assembleDebug assembleRelease assembleDebugAndroidTest`: exit0, BUILD SUCCESSFUL in 2m12s. Fresh XML independently retained at `%TEMP%/phase122-final-19f0bbc/unit`: **173 suites / 785 tests / 0 failures / 0 errors / 0 skipped**.
- Installed debug APK from `C:/Users/tangy/.codex/worktrees/phase-122-note-state/AndroidAppCoding/app/build/outputs/apk/debug/app-debug.apk`, SHA256 **7DD718D4AECC30E042B3CD500796DE94A4721EE72C59756A99242014907B2315**. Used `adb -s emulator-5580 install -r` before any final connected run, preserving the actual prior 8/16 dataset and existing marker for D3 reproduction.
- Environment stayed the same API37/Android17 dedicated AVD, x86_64 16KB system image, WHPX, 1080x2400/420dpi, en-US, font1.0. Wi-Fi/data remained disabled through all offline runtime checks.
- Actual Android SQLite **3.50.6** was queried using a temporary Android Java diagnostic that creates an in-memory SQLiteDatabase and executes `SELECT sqlite_version()`. It was compiled/dexed under `%TEMP%/phase122-sqlite-probe`, pushed only to `/data/local/tmp/phase122-sqlite-probe.dex` on the dedicated AVD, and run as `CLASSPATH=... app_process /system/bin SQLiteVersionProbe`. Output `%TEMP%/phase122-final-android-sqlite-version.txt`. No app DB was changed by this query. The device lacks a sqlite3 shell binary; earlier `-Djava.class.path` diagnostic invocations aborted before running the query, while the corrected CLASSPATH invocation succeeded. Those aborts belonged to the diagnostic process, not OJ NEXUS. Device diagnostic DEX was deleted afterward.

### Final note/review/WAL snapshot acceptance

The preserved CF1029E note was edited through the actual UI input field, appending `_FINAL_WAL_19F0BBC` to produce:

`PHASE122_CONTINUOUS_20261023_FINAL_WAL_19F0BBC`

The entire string rendered and SAVED appeared. A newly displayed system notification permission prompt was denied on this disposable AVD to continue; no real-device permission changed. Before export, the app WAL sidecar was **449112 bytes**, with the latest note committed. Existing local review remained STAGE2/DUE10-26; its successful PASS transition and persistence were separately observed above.

Through Settings EXPORT DATABASE BACKUP → system Save, the final app created **oj-nexus-snapshot (2).zip**, 8822 bytes. This newer file was selected explicitly; the two older diagnostic exports remained distinct. Saved local copy `%TEMP%/phase122-final-snapshot.zip`.

- ZIP magic `504b0304`; exactly `manifest.json` and `database.db`.
- Extracted standalone DB schema15, integrity_check=ok, opened without the source WAL.
- **Every one of the 22 manifest table counts equals both its standalone DB count and the static live DB count**. Full machine-readable comparison `%TEMP%/phase122-final-compare.json`; manifest `%TEMP%/phase122-final-manifest.json`.
- Complete note content, across all four note text fields and problem ID, matches between archive and live DB, including FINAL_WAL_19F0BBC.

| Acceptance data | Manifest | Standalone archive | Live |
| --- | --- | --- | --- |
| problems | 8 | 8 | 8 |
| attempts | 16 | 16 | 16 |
| problem_notes | 1 | 1 with full final marker | 1 with full final marker |
| review_log | 1 | 1 | 1 |
| training_tasks | 3 | 3 | 3 |
| reviews | 2 | 2 | 2 |

Actual IMPORT DATABASE BACKUP → select **oj-nexus-snapshot (2).zip** opened IMPORT PREVIEW. XML and screenshot show PROBLEMS8→8, ATTEMPTS16→16, NOTES1→1, REVIEW_LOG1→1, TRAINING_TASKS3→3. CANCEL returned to Settings. Before/after generation stayed `8dd213a5-f134-4191-a164-d5984bf140d1`; the files directory contained only generation/profileInstalled, no pending restore/journal.

After force-stop and COLD restart, library still showed 8 problems, CF1029E still showed STAGE2/DUE10-26, full FINAL marker and SAVED. A fresh read-only DB copy confirmed 8 problems/16 attempts/2 reviews/1 review_log/3 tasks/1 note. Generation and no-staging checks still matched. Thus D3's prior loss of current notes/review/tasks is demonstrably fixed, and CANCEL did not stage an older or new restore.

Final runtime evidence under `%TEMP%`:

- `phase122-final-note-before.xml`, `...-final-note-written.xml`, `...-final-note-saved.xml`.
- `phase122-final-live.sqlite` + `-wal` (static source copy), `phase122-final-archive.sqlite` (only archive DB entry), `phase122-final-compare.json`.
- `phase122-final-import-preview.xml`, `phase122-final-preview.png`, `phase122-final-import-canceled.xml`.
- `phase122-final-reopen-library.xml`, `phase122-final-reopen-notes.xml`, `phase122-final-reopened.sqlite` + `-wal`, `phase122-final-reopened-state.json`.
- `phase122-final-dbinfo.txt` records the live Android connection pool in WAL mode.

### Final connected gate and scoped completion

Only after preserving all runtime export/cancel/restart evidence, ran the final same-worktree `gradlew.bat connectedDebugAndroidTest` with process-local JAVA_HOME/ANDROID_HOME/ANDROID_SERIAL as above. Session91751: **exit0, BUILD SUCCESSFUL in 1m25s**. Fresh XML: **33 tests / 0 failures / 0 errors / 0 skipped**, device time70.818s, timestamp2026-10-23T14:26:21Z. Log `%TEMP%/phase122-final-connected.log`; retained reports `%TEMP%/phase122-final-19f0bbc/connected`.

This final gate again verifies the existing library→session editable start/cancel/quick-verdict integration, dashboard and all bottom destinations, note-index components, connector error display and layout cases. It uninstalled target/test APKs and their dedicated test data on completion. The prior actual 8/16/final-note runtime scenario was therefore deliberately verified and retained first. No real app data was cleared.

The earlier offline cold launch/local library/detail, remote-catalog empty cache and actual public-handle CONNECT error were observed on the same API37 AVD; the final APK additionally passed offline note/review/restart and backup paths. No real API success, populated remote-cache scenario, successful account connection or physical-device result is fabricated.

### Cleanup and remaining platform debts

- Restored dedicated AVD Wi-Fi=1 and mobile_data=1 after offline checks. Wi-Fi's initial value1 was captured; mobile_data's initial numeric value had not been separately captured before disabling, so it was re-enabled instead of claiming an exact saved-value restoration.
- Confirmed font_scale1.0, physical1080x2400/420dpi, default en-US, airplane_mode0; no locale/font/size override had been applied.
- Removed the temporary device diagnostic DEX. Called only `adb -s emulator-5580 emu kill`, retained the dedicated AVD files, and checked device inventory after exit. No other AVD/device was stopped or removed.
- Older SQLite below3.27 does not support VACUUM INTO. The final code fails closed instead of offering a stale-file fallback; that compatibility/device-policy decision and an actual old-SQLite test remain **Phase127 follow-up**, not a passed Phase122 coverage claim.
- Physical-device installation/performance, actual TalkBack, battery/background execution, signed distribution and broader locale/font system-level checks remain the separate platform-phase debts. Component large-font instrumentation does not replace those checks.
