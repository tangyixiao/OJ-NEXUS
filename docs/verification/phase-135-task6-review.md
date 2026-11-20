# Phase 135 — Task 6 Independent Review Record

Verdict: **PASS-WITH-OBSERVATIONS**. The independent fresh-context reviewer
(read-only agent) audited the merged phase-135 implementation (PR #83,
`8175fdd`) against the frozen phase-131 contract (`contracts/portable/v1/`
`check.py` + README), the Windows reference (`PortableJson.cs`,
`PortableModels.cs`, `PortableCodec.cs`), and the phase-134 Linux
implementation. Twelve contract vectors were re-derived by hand and compared
against the Swift codec's behavior — **all 12 MATCH**, including the mixed
golden `pw-p01` round trip reproducing the pinned identity `81bb13c1…d44`.

Authorship caveat (disclosed): the findings below come from the independent
review agent; the dispositioned fixes were applied by the controller and
re-verified by CI on the PR #85 head. No finding was fixed blind — each was
confirmed against the actual revision before editing.

## Findings and dispositions

| # | Severity | Area | Finding | Disposition |
|---|----------|------|---------|-------------|
| F1 | P2 | `PortableArchiveStore` | The store had no recovery path: a corrupt main with a decodable backup threw `corrupt` with no route back to `.ready`, unlike the house `LocalWorkspaceStore` recover semantics. | **FIXED** in `720bc40`: `recover()` quarantines the corrupt main under a unique no-clobber name, installs the decodable backup, returns to `.ready`; tests pin quarantine-no-clobber and post-recovery export identity. CI then caught an edge the first fix missed — a **fresh store instance** over a missing main with an on-disk backup was misclassified as a fresh store because the established check used in-memory observation state only. **FIXED** in `d5d7255` mirroring the house rule (`backup != nil \|\| observedMain != nil`). |
| F2 | P2 | PortableViewModel UI states | The typed import/export states (authorization-cancelled, unreadable-file, write-failure, staged flow) had no model-level tests. | **FIXED** in `720bc40` + `3d5b614`: five @MainActor model tests added; the export payload bytes were made internal so the test target can drive the real import path. |
| F3 | P3 | Credential-shaped key rejection | Extra-key credential detection normalized with ASCII-only lowering; `check.py` strips all Unicode whitespace and lowercases all Unicode. | **FIXED** in `720bc40`: normalization now strips all Unicode whitespace and applies Unicode lowercase — `sec\u{00A0}ret` is rejected exactly like the oracle. |
| F4 | P3 | `PortableCodec` counts overflow | C#/Android throw on int64/int32 overflow inside `CheckCounts`; the Swift port silently skips comparison on overflow. | **ACCEPTED as a recorded deviation** (same class as the phase-134 overflow deviation): Swift integer arithmetic traps, so a literal port would crash the app instead of rejecting; the deviation is documented in-code at the counts check. |
| F5 | P3 | Root schema walk order | The Swift root schema validated properties in declaration order rather than the frozen manifest-key order, so a multi-violation package could reject with a different (still valid) category than the oracle. | **FIXED** in `720bc40`: root walk order aligned to `check.py`/Windows (manifest keys first). |
| F6 | P3 | `PortableArchiveStore.clear()` | Backup removal failure was swallowed; the main could be removed while the backup survived, diverging from the atomic-clear intent. | **FIXED** in `720bc40`: removal failures propagate typed (`unavailable`) and leave state untouched; test pins the failure path. |
| F7 | P3 | Rejection-matrix gaps | Tests lacked: multi-byte SIZE_LIMIT discrimination (byte limit vs character `maxLength`), notes duplicate `problemId`, `-0` canonicalization, negative dayKey shape, strict-decoder rejection set. | **FIXED** in `720bc40`: five additional tests; the SIZE_LIMIT case uses 8193 two-byte scalars (16386 UTF-8 bytes, 8193 characters) so only the byte gate can catch it. |

## Checklist

| Requirement | Result | Evidence |
|---|--------|----------|
| Strict JSON decode parity (BOM, duplicate keys, surrogate halves, literal numbers) | PASS | Hand-derived vectors MATCH; strict-decode rejection test set green |
| Canonical writer parity (code-point key sort, no trailing zero, `-0` → `0`) | PASS | Pinned identity reproduced on macOS and in CI |
| Schema walk precedence matches `check.py` | PASS | Rejection matrix category-by-category; root walk order aligned in F5 |
| PATH_UNSAFE raw `$` semantics; pattern translation `$`→`\z`, `\d`→`[0-9]` | PASS | Pattern validator is hand-written (no regex-engine divergence) |
| Store atomicity (flock + CAS + staged install + snapshot-first backup) | PASS | F1/F6 fixes carry tests; recovery quarantine is no-clobber |
| Repository orchestration (identical no-op, cooperative cancel checkpoints) | PASS | `testReimportOfExportedBytesIsIdenticalNoOp` and staged-flow tests green |
| UI security-scoped lifecycle (cancel → typed state, no partial import) | PASS | F2 model tests |
| Boundary audit (no banned substrings) | PASS | CI audit green on every head |
| Test honesty | PASS | Tests assert against `check.py`-derived expectations, not self-referential encodings |

## Re-verification

PR #85 (`720bc40` → `3d5b614` → `d5d7255`) re-ran the full Apple pipeline —
boundary audit, `swift test` (macOS), both product builds, and the iOS
simulator dual suites — and all eight checks succeeded on the final head
`d5d7255` before merging as `1a2ab03`. The verification record's gate row 6
and sign-off are flipped on that evidence; the closeout ledger row 8 flips at
the closeout PR merge.
