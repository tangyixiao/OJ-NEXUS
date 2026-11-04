# Phase 131 Portable Workspace v1 — Handoff

Status: contract delivered (contract only).
Date: 2026-11-04 (Asia/Shanghai).
Baseline main: `6eddcf5`.
Controller branch: `codex/phase-131-portable-contract`.

> Current state at a glance
> Phase 131 shipped the Portable Workspace v1 **contract** and its reference
> tooling only. No platform import or export consumer exists yet. The reference
> checker, the golden corpus, the CI gate, and the developer/verification docs
> are the whole deliverable. Phases 132–135 bind real import/export on Android,
> Windows, Linux, and Apple. This document is a self-contained handoff for
> whoever continues that work or opens Phase 132.

This section is the authoritative status for the contract-only delivery. The
numbered sections below record what shipped, the decisions behind it, how to
verify it, what remains open, the host gotchas, and the links. All facts here
are limited to Phase 131; earlier phases have their own handoff documents.

## 1. What shipped

The deliverable is `contracts/portable/v1/` plus its supporting documentation
and CI. The following items are part of the Phase 131 delivery:

- `contracts/portable/v1/README.md` — the human-readable contract.
- `contracts/portable/v1/schema.json` — a JSON Schema draft-07 subset.
- `contracts/portable/v1/check.py` — a stdlib-only reference checker.
- `contracts/portable/v1/golden/` — `cases.json` plus 15 synthetic packages
  (one positive mixed package, PW-P01, and the negative cases).
- `contracts/portable/v1/selftest.py` — checker self-test covering the PW-N08
  identity property and the PW-N12 unassigned-provenance property.
- `.github/workflows/contracts.yml` — the CI gate that runs the golden corpus
  on `contracts/**` changes.
- `docs/PORTABLE_WORKSPACE.md` — the developer guide.
- `docs/verification/phase-131-portable-contract.md` — the verification record.
- The SDD plan ledger (`docs/superpowers/plans/2026-11-01-phase-131-portable-workspace.md`).

**No platform consumer ships in 131.** There is no Android, Windows, Linux, or
Apple import/export code, and no platform test beyond the golden corpus and the
checker self-test. The checker is a reference implementation of the contract,
not proof that any platform honors it.

## 2. Key decisions and rationale

- **Single-file UTF-8 JSON container, no ZIP.** One package is one JSON
  document. Rejecting an archive format removes the entire "stowaway file in an
  archive" class of attack and keeps the format trivially inspectable.
- **Whole-package reject on future majors.** `formatVersion` must be exactly 1.
  Any other integer is rejected as `UNSUPPORTED_VERSION` for the whole package;
  there is no partial acceptance and no automatic upgrade path. v2 will define
  its own version stance when it is written.
- **Strict duplicate-key decode.** Duplicate JSON object keys anywhere are an
  `INVALID_INPUT` error, not a last-one-wins normalization. The checker decodes
  through an object-pairs hook so duplicates are detected before materialization.
- **Unknown keys rejected with the offending key named.** All objects are closed
  (`additionalProperties: false`). Any unknown key, enum violation, or schema
  mismatch is reported as `UNKNOWN_FIELD` with the offending key, value, keyword,
  and JSONPath-ish location. Nothing is silently normalized or dropped.
- **Only `extra` is a lossless extension point.** `extra` maps string keys to
  scalars or arrays of scalars, at most 2 levels deep, with stored-only
  semantics: values are preserved value-for-value, never promoted to editable
  fields and never normalized. Credential-shaped keys are rejected even inside
  `extra`.
- **Path-shape defense in depth on all string values.** Every string is checked
  against `^([A-Za-z]:\\)|^/|\.\./|^\.\.$` and rejected as `PATH_UNSAFE` on a
  match. v1 has no file fields; this is a deliberate extra layer.
- **Credential-shaped keys rejected.** Keys matching tokens such as `token`,
  `password`, `secret`, `cookie`, `credential`, `apikey`, `authorization`,
  `sessionid`, `privatekey`, `accesskey` are rejected as `UNKNOWN_FIELD`.
  No credentials, cookies, tokens, or account configs are portable.
- **Unassigned origin must never be attributed to the importing account.** A
  record with `origin: "unassigned"` is accepted, but the importer must keep it
  unattributable; it is never silently reassigned to the importing scope.
- **Honest counts reconciliation.** The manifest `counts` must equal the actual
  array lengths, or the package is `COUNTS_MISMATCH`. Target-side capability
  tiers (editable / storedOnly / rejected) must report three honest counts so
  nothing is silently lost.
- **Canonical re-encode identity.** Identity is the SHA-256 of the canonical
  re-encode: sorted keys, fixed separators, UTF-8, and a trailing newline,
  computed via `--identity`. Re-encoding an accepted package must reproduce
  identical bytes; this is the lossless round-trip criterion.

## 3. Error categories

The checker reports exactly one error — the first failing category in its
precedence order — with its location. The eight categories:

- `INVALID_INPUT` — non-UTF-8 bytes, malformed JSON, trailing data, NaN/Infinity,
  duplicate object keys, lone surrogate escapes, non-object root, or a missing /
  malformed `formatVersion`.
- `UNSUPPORTED_VERSION` — `formatVersion` is an integer but is not 1.
- `UNKNOWN_FIELD` — any closed-schema violation (including unknown keys, enum
  violations, and credential-shaped keys), or `extra` values deeper than the
  2-level limit; the detail names the offending key.
- `DUPLICATE_ID` — duplicate `stableId` inside a collection, or duplicate
  `problemId` inside `notes[]` (one note per problem).
- `MISSING_REFERENCE` — an `attempts[].problemId`, `notes[].problemId`,
  `reviews[].problemId`, or `sessions[].problemIds[]` entry not present in
  `problems[].stableId`.
- `SIZE_LIMIT` — package over 64 MiB (pre-decode), a collection over 50 000
  records, or any single string over 16 384 UTF-8 bytes (16 KiB).
- `PATH_UNSAFE` — a string matching the path-shape pattern above.
- `COUNTS_MISMATCH` — manifest `counts` disagree with actual array lengths.

## 4. How to verify

Run from `contracts/portable/v1/`:

```
python3 check.py --cases golden/cases.json golden/
python3 check.py --identity <valid-package.json>
python3 selftest.py
python3 -m py_compile check.py
```

- `--cases CASES_JSON GOLDEN_DIR` runs every golden case and compares against
  `cases.json` expectations.
- `--identity FILE` prints the SHA-256 of the canonical re-encode of a valid
  package; the same input must always yield the same digest.
- `selftest.py` exercises the checker's PW-N08 identity and PW-N12 unassigned-
  provenance assertions.
- `-m py_compile check.py` checks the checker compiles.

Exit codes:
- `0` — valid package, or every case matched its expectation.
- `1` — invalid package (category + detail on stderr), or at least one case
  deviated.
- `2` — checker misuse: bad arguments, unreadable package file, missing or
  unusable `schema.json`, or unreadable/malformed cases file.

CI (`.github/workflows/contracts.yml`) runs the corpus on `contracts/**`
changes. Android gates are unaffected by 131 because there are no `app/`
changes, but PR CI runs them anyway. No third-party dependencies and no network
access are needed for `check.py`. All golden fixtures are synthetic.

## 5. Open work / next phases

Phases 132–135 bind real import/export on Android, Windows, Linux, and Apple.
The binding work must:

- Run the mixed PW-P01 golden package through import then export and show a
  lossless round-trip for accepted records: the canonical re-encode bytes must
  be identical.
- Implement capability tiers `editable` / `storedOnly` / `rejected` with three
  honest counts, so no record is silently dropped.
- Handle same-ID-different-content explicitly, and handle same-package reimport
  idempotently.
- Keep `origin: "unassigned"` records unattributable to the importing account.

v2 will define its own version stance; there is no automatic upgrade path from
v1. No credentials, cookies, tokens, or account configs are portable in any
phase.

## 6. Gotchas for the next agent

- **SSH remote is broken on this host.** `git@github.com:tangyixiao/OJ-NEXUS.git`
  fails because port 443 is closed. Use the full HTTPS URL
  `https://github.com/tangyixiao/OJ-NEXUS.git` for `git push` / `git fetch`,
  and `gh` (API/HTTPS) for PRs.
- **The controller branch is the integration base.** It carries the spec+plan
  commit `0aa419c` that `main` lacks. Child PRs must base on
  `codex/phase-131-portable-contract`, not on `main`.
- `check.py` has no third-party dependencies and no network access.
- All golden fixtures are synthetic; do not treat them as real user data.
- Phase 131 is a contract-only delivery. Do not assume any platform import or
  export code already exists.

## 7. Links

Relative markdown paths:

- Spec: [docs/superpowers/specs/2026-11-01-phase-131-portable-workspace-design.md](superpowers/specs/2026-11-01-phase-131-portable-workspace-design.md)
- Plan: [docs/superpowers/plans/2026-11-01-phase-131-portable-workspace.md](superpowers/plans/2026-11-01-phase-131-portable-workspace.md)
- Human contract: [contracts/portable/v1/README.md](../contracts/portable/v1/README.md)
- Schema: [contracts/portable/v1/schema.json](../contracts/portable/v1/schema.json)
- Reference checker: [contracts/portable/v1/check.py](../contracts/portable/v1/check.py)
- Golden cases: [contracts/portable/v1/golden/cases.json](../contracts/portable/v1/golden/cases.json)
- Verification record: [docs/verification/phase-131-portable-contract.md](verification/phase-131-portable-contract.md)
- Developer guide: [docs/PORTABLE_WORKSPACE.md](PORTABLE_WORKSPACE.md)
- CI workflow: [.github/workflows/contracts.yml](../.github/workflows/contracts.yml)

Handoff status: this document is the current authority for the Phase 131
contract-only delivery. Sections 1–7 are authoritative; earlier phases keep
their own handoff documents.
