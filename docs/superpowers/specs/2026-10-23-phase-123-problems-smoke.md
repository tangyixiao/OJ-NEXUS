# Phase 123-b: WPF Problems-screen acceptance

## Goal

Extend the existing isolated Windows UI smoke to visit the implemented WPF Problems view,
verify its local-ledger heading, both filters and empty state, and capture a fourth distinct
view. Keep the existing Dashboard, Connectors and Sync History assertions and captures.

## Boundaries

- Exercise the real WPF controls with the smoke's temporary `OJ_NEXUS_DATA_DIRECTORY`.
- Do not add product functionality, fabricate a Review page, use synthetic network output, or
  change source/package version metadata or the published RC artifact.
- Use the existing in-process screenshot support for `Problems`; only extend it if evidence
  shows the actual page capture is missing.
- Restore process environment and terminate the launched desktop process on all exit paths.
- Record clean-VM installation/signing as unverified.

## Acceptance

The fresh WPF smoke visits all four existing pages, verifies `LOCAL PROBLEM LEDGER`, finds the
two problem filters and `NO PROBLEMS RECORDED` in the isolated empty store, and produces four
present, nonidentical screenshots. Run the Windows G-W build/test/release gates and the CLI and
four-view UI smoke; preserve test counts, command outcomes, screenshot paths, and remaining
distribution limits in a new verification record.
