# Store / F-Droid metadata — Spec

Date: 2026-10-18
Phase: 110 (docs/LONG_RANGE_PLAN.md:288-291)

## Goal

Fastlane-style metadata folder (descriptions EN/zh, screenshots) in the repo.
Constraints: no marketing copy; telemetry tone.

## Layout shipped

```
fastlane/metadata/
  README.md                    — structure contract + honesty rules
  android/en-US/title.txt      — "OJ NEXUS"
  android/en-US/short_description.txt
  android/en-US/full_description.txt
  android/en-US/changelogs/100.txt   (versionCode 100)
  android/zh-CN/title.txt      — "OJ NEXUS"
  android/zh-CN/short_description.txt
  android/zh-CN/full_description.txt
  android/zh-CN/changelogs/100.txt
```

## Content rules enforced

- Telemetry tone, no marketing: no superlatives, no user counts, no "revolutionary/best"
  wording, no emoji. Feature list reads like a spec sheet.
- Every factual claim maps to a shipped phase: windowed library (104), review ladder (Android
  ReviewScheduler), training ladder (83), deep links (92), Luogu training catalog (95),
  snapshot export/import + conflict preview (86/89), per-judge wipe (88), housekeeping panel
  (90), cold-start/memory/index work (105/106/108) are all real.
- Data boundary paragraph states the credential rule verbatim in spirit: no passwords/cookies/
  tokens; Luogu open-platform credential lives in no-backup storage and never enters backups,
  exports, or logs (Phase 86/95 probes).
- `video.txt` and `screenshots/` are deliberately absent — no device is attached to this host
  and screenshots are never invented. Device captures extend the folder later without schema
  changes.

## Changelog policy

One `changelogs/<versionCode>.txt` per release going forward (F-Droid reads these directly);
versionCode 100 covers everything through Phase 109. Future releases append a new file as part
of the release bookkeeping step (same commit as the ROADMAP/plan updates).
