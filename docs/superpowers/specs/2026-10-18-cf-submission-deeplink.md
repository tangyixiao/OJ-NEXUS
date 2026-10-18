# Spec: Codeforces submission detail deep-link (Phase 92)

Date: 2026-10-18 · Plan: docs/LONG_RANGE_PLAN.md PHASE 92 (ARC C — Multi-OJ depth)

## Goal

Attempt rows on the problem detail screen open the judge-side submission page when the attempt
was imported from Codeforces. Open-in-browser only (Custom Tabs via the existing helper); no
in-app WebView, no login surface.

## Constraints

- Public site URLs only, built exclusively by `CodeforcesUrls` (the reviewed URL boundary).
- No schema change: the database already stores `source_judge`, `external_submission_id`,
  `contest_id` on `attempts`; the domain `Attempt` model gains the same three nullable fields
  and `AttemptEntity.toDomain()` passes them through.
- Manual entries (null origin) keep their exact current rendering and are not clickable.
- A11y: clickable rows get Role.Button, an onClickLabel and a contentDescription; state is never
  color-only (the accent "OPEN ON CODEFORCES" text is a real label).

## Design

1. `CodeforcesUrls.submission(contestId: Long, submissionId: Long)` →
   `https://codeforces.com/contest/<id>/submission/<id>` (pure function).
2. `Attempt` (core/model/TrainingModels.kt) gains `sourceJudge/externalSubmissionId/contestId`
   nullable with defaults; mapper transparency only.
3. `AttemptRow` computes the deep-link when sourceJudge == CODEFORCES and both ids parse as
   Long; clickable via the screen's existing `OpenInBrowser` (ACTION_VIEW fallback path already
   hardened); otherwise identical to before.
4. Strings: `attempt_open_remote` EN "OPEN ON CODEFORCES" / zh "在 Codeforces 打开".

## DoD

- `CodeforcesUrlsTest` covers submission join, minimal ids, problem/index edge cases
  (multi-char index, space→%20), contest and profile URL exactness.
- Full `test assembleDebug` gate green.

## Out of scope

- AtCoder/Luogu submission deep-links (later phases), in-app submission rendering
  (Phase 94 unified sheet), any authenticated flow.
