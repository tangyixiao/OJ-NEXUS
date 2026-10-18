# Localization pass — Spec

Date: 2026-10-18
Phase: 113 (docs/LONG_RANGE_PLAN.md:301-303)

## Goal

Audit every user-visible string is in `strings.xml` (EN+zh), no hardcoded UI text.
DoD: lint-style check script committed under `tools/`.

## What the script checks (tools/check-localization.ps1)

1. **Key parity**: the set of `string name="…"` keys in `app/src/main/res/values/strings.xml`
   must equal the set in `app/src/main/res/values-zh-rCN/strings.xml`. Any missing key on either
   side fails with the key name.
2. **Format-argument parity**: for every key present on both sides, the multiset of printf
   specifiers (`%1$s`, `%1$d`, …) extracted from EN must equal zh — a mismatch means a crash at
   runtime when the formatter gets the wrong arity. Script strips non-specifier `%` first
   (`%%`) so literal percents don't false-positive.
3. **Hardcoded `stringResource`-bypass candidates in Kotlin**: scans
   `app/src/main/java/com/ojnexus/**` for `Text(` calls whose `text =` argument is a non-empty
   string **literal** (e.g. `Text("SUBMIT")`). Data strings that legitimately live in code
   (judge names, handle placeholders, verdict raw text) are exempt by AGENTS.md — the script
   maintains an explicit allowlist of files where such literals are data, not UI copy, and the
   allowlist file list is reviewed in the plan result. Any `Text("…")` hit outside the
   allowlist fails.

## Known-good allowances (verified by reading each hit)

- `Verdict` / `SyncPhase` names rendered via `.uppercase()` / `.name` are data states, always
  paired with a labeled tag color + text per AGENTS.md ("state is never color-only" is honored
  via the neighboring string resource).
- Uppercased judge identifiers (`judge.id.uppercase()`) are data.
- `"…"` inside comments / log calls are ignored (script only matches `Text(` and
  `contentDescription = "`).

## Enforcement posture

The script runs standalone now and exits 0/1; wiring it into CI is a one-line future change
(`tools/` script + workflow step) and is noted but not required by the DoD.
