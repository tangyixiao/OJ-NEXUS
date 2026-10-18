# Localization pass — Plan

Date: 2026-10-18
Phase: 113 (docs/LONG_RANGE_PLAN.md:301-303)
Spec: docs/superpowers/specs/2026-10-18-localization-pass.md

## Steps

1. Write `tools/check-localization.ps1` with three checks: key parity, printf format-argument
   parity, hardcoded `Text("…")` literals outside a reviewed allowlist.
2. Run it, fix real findings, record false-positive causes as script lessons.

## Result

**LOCALIZATION: OK** — 1010 EN keys, 1010 zh keys, parity exact, zero format mismatches, zero
hardcoded UI literals outside the allowlist. Exit 0.

### Script hardening lessons (each was a real false positive first)

1. **Windows PowerShell 5.1 reads BOM-less files as ANSI (GBK on this host)** when launched via
   `powershell -File`, while pwsh 7 defaults to UTF-8. The zh strings decoded as mojibake
   (已解决 → 宸茶В鍐?) and the regex then matched garbage specifiers across tag boundaries —
   27 phantom FORMAT MISMATCH findings. Fix: every read uses
   `[IO.File]::ReadAllText($path, [Text.Encoding]::UTF8)`. After the fix, format parity is
   genuinely clean (0 mismatches).
2. **`Text(` matched `ClipData.newPlainText("SESSION DEBRIEF", …)`** — no, it did not match
   that; the naive pattern `Text\(` matched the `Text(` inside… actually it matched nothing of
   the sort: the 2 hits were (a) a real bullet glyph `Text("• ${block.text}")` in
   ContestFocusScreen.kt:162 (markdown bullet renderer — the bullet is a glyph prefix on data
   text, not UI copy; allowlisted) and (b) `newPlainText("SESSION DEBRIEF", …)` in
   SessionScreen.kt:161 which the pattern `(?<![.\w])Text\(` correctly excludes (preceded by
   `.`). The `(?<![.\w])` lookbehind + comment stripping is the final matcher.
3. The allowlist carries exactly one file (ContestFocusScreen.kt — markdown bullet rendering)
   plus the comment-stripping rule; anything added later must justify itself in this plan.

### Known-good findings (checked, no change)

- `newPlainText("SESSION DEBRIEF", markdown)` — clipboard label, machine-facing, not rendered.
- Judge ids / verdict raw names via `.uppercase()` — data per AGENTS.md.

## Enforcement

Standalone exit 0/1; CI wiring is a one-line workflow step when wanted (not required by DoD).
