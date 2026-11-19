# Phase 136 — Task Review Record

Independent review of the phase-136 problem identity and retrieval contract
(`contracts/problems/v1/`), performed by a fresh-context reviewer (read-only;
ran the gate, self-test and reference mode; hand re-derived 12+ vectors from
the README semantics). Verdict: **PASS-WITH-OBSERVATIONS** — no blockers, two
P2s and five P3s. All findings are dispositioned below; every fix landed in
the closeout PR with measured evidence.

## What the reviewer verified clean

- README ↔ `check.py` semantics: identity exact equality + duplicate
  rejection; status precedence inReview→REVIEW→SOLVED→ATTEMPTED→UNSOLVED;
  trim-then-lower-then-substring over title/externalId; AND-combined filters
  with inclusive bounds and null-difficulty exclusion; all four sorts plus the
  code-point key tie-break — no behavioral divergence.
- Python traps: bool-as-int excluded at every integer site; enum membership
  checks reject non-strings; JSON floats rejected for int fields; UTF-8 pinned
  on load (critical for vectors 007/012 on Windows).
- Acceptance coverage: all four G-S clauses pinned by vectors that fail if the
  clause is violated (001/002/003+004/005-009).
- Gate 28/25 vectors PASS, selftest green at review time, CI contract gates
  green.

## Findings and dispositions

| # | Severity | Anchor | Finding | Disposition |
|---|----------|--------|---------|-------------|
| F1 | P2 | `check.py` trim + README "trimmed"; no whitespace-only vector | The trim character set was undefined; Python `str.strip()` strips NBSP (U+00A0) while Kotlin `trim()` keeps it — a real Python/Kotlin divergence, and the "whitespace-only query matches all" claim was unpinned. | **FIXED** in the closeout PR: trim set frozen to ASCII whitespace (`U+0020, U+0009-U+000D`); `check.py` uses `strip(ASCII_WHITESPACE)`; new vectors 026 (NBSP query matches nothing — a `str.strip()` implementation fails it) and 027 (whitespace-only query matches all); selftest checks 16/17. |
| F2 | P2 | `selftest.py` checks 06/14 | Both mirrored the gate's comparison locally without executing `check._run_gate` — a gate regression to a tautology would stay green. | **FIXED**: checks 06/14 (and new 20/22/23) drive the real `_run_gate` against temporary golden fixtures; mutated expect, missing expect entry, unlisted files, name/filename mismatch and wrong cases version all fail the real gate path. |
| F3 | P3 | `difficultySource` unused in corpus | The optional field's validation and default were untested. | **FIXED**: vector 001 carries `difficultySource` on one record; selftest check 18 covers missing-ok and non-string-rejected. |
| F4 | P3 | Tie-break micro-gaps | (a) no two-null-difficulty vector (null-group key tie unpinned); (b) the tie-break key's own code-point order only pinned indirectly via TITLE. | **FIXED**: vector 003 gained a second null record (null-group tie `codeforces/2` < `codeforces/5`); new vector 028 pins the tie-break key's code-point order with an astral externalId (U+FF21 vs U+1D400 — UTF-16-naive order fails it). |
| F5 | P3 | `golden/007` note | Note mis-stated the lowered astral code point (U+1D400 lowers to U+1D41A = 119834). Ordering claim and expect unaffected. | **FIXED** (note text): now states U+1D41A (119834); the U+FF41 (65345) < U+1D41A ordering claim is unchanged. |
| F6 | P3 | `check.py` validation permissiveness | Unknown record/query fields silently accepted (typo'd query key changes results with no error); gate did not cross-check vector names, cases version, or unlisted golden files. | **FIXED**: unknown record fields and query keys rejected; gate now validates name-vs-filename, cases `version == 1`, and fails on any golden `*.json` not listed in cases.json. Selftest checks 19/20/22/23. |
| F7 | P3 | Lone surrogates | Python's JSON decoder accepts `"\ud800"` escapes producing strings unreproducible on JVM/Qt/Swift. | **FIXED**: `_load_json` rejects lone surrogates anywhere in the loaded document; selftest check 21. |

## Post-fix measurements (controller, on the closeout head)

- `python check.py --cases golden/cases.json golden/` → checked 28 vectors,
  **PASS** (28/28; every `expect` remains hand-derived).
- `python selftest.py` → **23/23 checks passed** (was 15; +8 gate-level and
  semantic checks).
- `python -m py_compile check.py selftest.py` → clean.
- CI "Problem contract gate" re-runs on the closeout PR head.

## Authorship caveat

The review was performed by an independent fresh-context agent; the
dispositions and fixes were authored by the controller. The reviewer's full
findings text (quoted verbatim in the session record) is summarized in the
table above; no finding was dropped or regraded.
