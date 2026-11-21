# Phase 138 — Task 6 Independent Review Record

Scope: the full phase-138 diff vs `9bfdf21` on
`codex/phase-138-windows-problem-search` (Core domain/schema/query, CLI
verbs, WPF ledger page, tests, smoke, contract corpus wiring). Reviewer:
independent read-only subagent with a fresh context (no role in the
implementation); the oracle gate (`check.py --cases`) was re-run by the
reviewer itself (28/28 PASS) and the F2 casing claim was verified
empirically on net8.0.

## Verdict

**PASS-WITH-OBSERVATIONS** — no P1/P2 findings; seven P3 observations, all
dispositioned below.

## Verified-correct highlights (reviewer's anchors)

- SQL parity end to end: frozen ASCII trim set matches the oracle exactly;
  LIKE escaping (backslash first); derived-status precedence; difficulty
  bounds exclude unknown when either bound set; tag EXISTS exact/BINARY;
  code-point tie-break via `lower(judge)` normalization; tuple ≡ concatenated
  key order for the frozen judge set.
- Parity seeds mirror the store's real write shape and the `expect` lists are
  oracle-consistent — the gate is not a tautology (vectors 007/028 pin astral
  code-point order; 026's NBSP search byte was verified raw).
- Migration: full ladder 0-8 + fresh + current-validate; guarded adds are
  re-runnable for the house demote-fresh pattern; teardown order correct.
- Concurrency: the Busy classification for the uncommitted-first-run window
  is what REPAIRS the phase-128 "only READY or BUSY" contract; Migrate runs
  in one transaction so openers never see a torn version.
- WPF: honest virtualization (fixed viewport defeats the infinite-height
  parent); persistence save-on-commit/load-once; detail sub-state never
  touches the row collection; summary strictly from SQL aggregates.
- Tests: the STA probe is a real behavioral gate; the CLI show parser keeps
  external ids verbatim; hygiene scan of all 2,694 added lines clean.

## Findings and disposition

| ID | Severity | Location | Description | Disposition |
|---|---|---|---|---|
| F1 | P3 | WindowsStorage.cs | This branch added a UTF-8 BOM and GBK-mangled an em-dash in a doc comment (`鈥?`); the only non-ASCII damage in the diff. | **FIXED** — bytes restored (`E9 88 A5 3F` → `E2 80 94`), BOM stripped; scripted scan now shows exactly one non-ASCII run (the legitimate em-dash). |
| F2 | P3 | Design doc decision 1 / ProblemModels hedge / runtime behavior | `ToLowerInvariant(U+0130 İ)` is unchanged on net8.0 while the oracle's `str.lower()` maps it to `i`+U+0307; SQLite `lower(external_id)` is ASCII-only — İ titles and non-ASCII external-id search diverge from the oracle/Android. Not covered by the 28 vectors; the design doc's equivalence claim was factually wrong on the shipped runtime. | **FIXED (recorded deviation)** — the design-doc claim is corrected and the divergence is recorded as an explicit deviation (same policy as the difficulty/tags ingest deviation). Closing it needs a new vector + a contract-level decision on full-mapping lowercase; deferred rather than silently claimed. The `ProblemSearch` doc comment's vector-scoped hedge stands (correct as written). |
| F3 | P3 | DesktopViewModel SetProblemSearch/SetProblemTag | UI commit path used .NET `string.Trim()` (strips all Unicode whitespace) before the store's frozen ASCII-only trim — UI-committed NBSP-padded queries diverged from vector-026 semantics. | **FIXED** — `ProblemSearch.TrimAscii` added and used by both commit paths. |
| F4 | P3 | DesktopViewModel LoadMoreProblemsAsync | Re-entrant LOAD MORE during an in-flight store await would read the same `offset = Problems.Count` and append the page twice. | **FIXED** — `_problemAppendInFlight` guard around the append path (reset paths are page-0 idempotent and intentionally not gated). |
| F5 | P3 | InMemorySyncStore SummarizeUnfiltered | The in-memory summary counted the RAW stored InReview flag while rows use the derived value — test double lied for the seeded-flag + review-row combination. | **FIXED** — summary counts `EffectiveEntry(row).InReview`. |
| F6 | P3 | DesktopViewModel ProjectProblems | Dead method left from the pre-138 single-load path. | **FIXED** — deleted. |
| F7 | P3 | SqliteSyncStore/InMemorySyncStore GetProblemDetailAsync | Detail lookup trimmed the external id although identity is byte-exact and the CLI show parser promises the verbatim remainder. | **FIXED** — `.Trim()` dropped in both stores; a padded key now honestly resolves to PROBLEM NOT FOUND. |

## Authorship caveat

Recorded honestly: the review was produced by a controller-dispatched
subagent that had no role in writing the implementation; its verdict and
findings were validated against the actual revision by the controller before
each disposition. The controller authored this record.
