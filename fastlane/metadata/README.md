# Fastlane-style store metadata

Structure: `fastlane/metadata/android/<locale>/` (en-US, zh-CN), the layout F-Droid and
fastlane supply both read. All copy is telemetry-tone, uppercase English / plain Chinese,
no marketing language, no user counts, no "best/top/ultimate" wording.

- `title.txt` — 30 chars max, exact app name.
- `short_description.txt` — 80 chars max, one line.
- `full_description.txt` — feature list, data-boundary statement, no network claim honesty.
- `changelogs/<code>.txt` — one file per versionCode (F-Droid reads these for in-app updates).
- `video.txt`, `screenshots/` — omitted until real device captures exist; never invented.
