# Phase 123 — Windows baseline and distribution evidence

Status: **ACTIVE / PARTIAL** (2026-10-23). The deterministic review-clock repair and
unsigned ZIP candidate have passed their scoped gates. Problems-screen acceptance and a
clean-VM installation are still missing, so Phase 123 is not VERIFIED.

## Source and review

Source `8bca81b9c6d2fcd6c4d19637d9799c688404861d`, based on main `1155509`;
[PR #13](https://github.com/tangyixiao/OJ-NEXUS/pull/13) is ready for review and unmerged.
The optional query clock keeps the existing local-day review semantics while removing the
test's dependence on the date on which CI runs. The mark operation still accepts its explicit
time; no schema or product-version change was made. Independent source review found no
blocking P1/P2. The implementation report is `docs/verification/phase-123-review-clock.md`
on the PR branch.

## Fresh gates

- Release tests: **167 passed / 0 failed / 0 skipped** (Core 81, CLI 65, Desktop 21).
  The coordinating lane independently reran the complete built test set and preserved the
  three TRX files under `%TEMP%/phase-123-review-clock/root-acceptance`.
- Release build: exit 0, 0 warnings and 0 errors. CLI status and WPF startup smoke passed.
- Existing WPF UI smoke passed Dashboard, Connectors and Sync History, with three captured
  views. It did not visit the fourth Problems screen or a nonexistent Review page.
- [Remote Windows job](https://github.com/tangyixiao/OJ-NEXUS/actions/runs/39959960552/job/120435177111)
  passed. The same PR's Android build/release/emulator and Apple Swift jobs also passed.
  These checks validate this branch; unrelated branches based on main still reproduce the
  old date-dependent Windows test until the fix is integrated.

## Actual CLI review across process restart

The coordinating lane used the built Release `ojnexus.exe` in a new temporary data directory.
Every invocation was a separate native process. No public API request or user database was
used. `PHASE123_SYNTHETIC_LOCAL` is a synthetic local review identity, with no problem title
or URL and no claim of a remote submission or solved problem.

1. `review due --json` returned an empty list.
2. `review mark --judge codeforces --problem PHASE123_SYNTHETIC_LOCAL --result pass --json`
   returned `marked=true`, stage 1, PASS and local due day `2026-10-26`.
3. A new `review due --json` process returned an empty list, as the row was not yet due.
4. Independent SQLite inspection after the process exited confirmed one matching `reviews`
   row at stage 1/PASS/due `2026-10-26`, plus one `review_log` row. A separate `status --json`
   process also exited 0.

Evidence is retained under `%TEMP%/phase123-cli-review-1792763822` (before/mark/reopen JSON
and persisted database check). The process environment override was restored afterward.
This proves CLI persistence for this operation; it does not establish future-date behavior
without the separate injected-clock regressions, nor WPF review editing support.

## Published ZIP candidate

[windows-v0.1.1-rc.1](https://github.com/tangyixiao/OJ-NEXUS/releases/tag/windows-v0.1.1-rc.1)
is an unsigned testing prerelease, `latest=false`, with its tag pointing to the exact source
above. It contains `OJ-NEXUS-Windows-v0.1.1-win-x64.zip` (108,789,178 bytes) and a checksum file.

SHA256: `77b0f875c32c3d2713887c2e5c8ddefb4e4a45871577fe0582be126d35cf25b6`.

The packaging script verified 662 hashed files before and after ZIP extraction, ran packaged
CLI/WPF startup and the existing three-view UI smoke, and reported signing SKIPPED because
no certificate was configured. The coordinating lane downloaded both published assets again
and verified the ZIP hash. No signing, installer installation, clean-VM acceptance or stable
release is inferred from this ZIP. The historical latest Android release is unchanged.

## Remaining gates

- Exercise the actual WPF Problems screen and filters, with explicitly sourced local data or
  a clearly marked synthetic fixture; extend the current smoke coverage where useful.
- Establish clean-VM install, launch, upgrade/uninstall and signing evidence independently.
  The earlier VM availability query did not succeed; this is unknown availability, not proof
  that the machine has no usable VM.
- Re-run affected gates for any later source or packaging changes and attach evidence before
  changing this phase to VERIFIED. PR merging remains a separate action.
