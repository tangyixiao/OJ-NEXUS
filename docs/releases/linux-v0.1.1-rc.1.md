# linux-v0.1.1-rc.1 — Storage Startup Validation — Release Record

Date: 2026-11-01
Package: `ojnexus-0.1.1-Ubuntu24.04-amd64.deb` (unsigned prerelease)
Tag: `linux-v0.1.1-rc.1` at `46b8ddc` (main after the version bump PR #29)
SHA-256: `32a5f42aaa3fd413e8f2eba260eb5b0953a359f76bd954ac18bf812a26735b3f`
(SHA256SUMS attached to the release and re-verified after download.)

## What changed since linux-v0.1.0-rc.1

Phase 129A (merged via #22): startup admission for existing stores — zero-byte, non-SQLite,
truncated, no-metadata, empty-metadata, duplicate and future-schema files are rejected with
typed `Storage` / exit 6 while preserving their main-file bytes; first use is reserved for an
atomically created absent path; initial schema and metadata commit in one transaction.
Regression coverage grew by a 338-line SqliteStore suite.

## Verification (all fresh on this build)

- WSL Ubuntu-24.04, CMake 3.28.3 / Ninja 1.11.1 / GCC 13.3: clean configure + 57-target build
  (GUI and tests on).
- `ctest` offscreen: 8/8 targets passed, 0 failed.
- Real CLI smoke on a fresh temp data directory: `ojnexus status --json` returns `accounts == []`.
- Qt GUI offscreen construction probe: healthy (expected timeout 124).

## Boundaries

The package is unsigned; interactive Qt desktop use and clean-host package installation remain
unverified (Phase 124 scope). Windows has no new release: `windows/` is byte-identical to
`windows-v0.1.2-rc.1` except a 6-line README note. Apple remains CI-only (no signing material).
