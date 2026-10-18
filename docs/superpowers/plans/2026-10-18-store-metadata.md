# Store / F-Droid metadata — Plan

Date: 2026-10-18
Phase: 110 (docs/LONG_RANGE_PLAN.md:288-291)
Spec: docs/superpowers/specs/2026-10-18-store-metadata.md

## Steps

1. Create the fastlane-style folder `fastlane/metadata/android/{en-US,zh-CN}` with title /
   short description / full description / changelog for versionCode 100.
2. Write copy in telemetry tone (no marketing), bilingual, with the data-boundary paragraph.
3. Record the changelog policy (one file per future release, committed with release bookkeeping).
4. Document that screenshots/video are omitted until device captures exist.

## Result

- Folder shipped as specified; `fastlane/metadata/README.md` carries the structure contract.
- Changelogs 100.txt (EN + zh) summarize the snapshot/wipe/housekeeping phase batch.
- No production code touched; no gate run needed beyond confirming the tree is clean of
  accidental files.

### Lessons

- F-Droid reads `changelogs/<versionCode>.txt` (no extension besides .txt) — keep the numeric
  file name equal to the Android versionCode.
