# Phase 123-b implementation plan

1. Confirm the existing WPF view model, XAML, screenshot renderer and UI smoke already expose
   the Problems page; keep the change in the smoke unless runtime evidence requires a minimal
   screenshot extension.
2. Update the smoke to clear stale `problems.png`, navigate to `PROBLEMS`, assert the real
   ledger heading, two ComboBox filters and empty-store message, then capture and validate all
   four distinct page images. Preserve process cleanup and temporary data isolation.
3. Build and run the Windows G-W checks, CLI smoke and four-view UI smoke with the isolated
   output paths. Inspect screenshot dimensions/hashes and CLI exit/count evidence.
4. Write `docs/verification/phase-123-problems-smoke.md` with exact fresh results and explicit
   clean-VM/signing limitations; check the diff and commit only scoped implementation/docs.

The implementation does not change package identity, version metadata, published artifacts,
or the already-published PR #13 branch.
