# Phase 72 — Dashboard Command Surface

## Goal

Make the dashboard's top area an actionable local command surface. `NOW`, `NEXT`, and `SIGNAL`
must be derived from existing Room-backed tasks, review queue, contests, and judge sync state, with
each actionable cell routing through an existing navigation callback.

## Guardrails

- No fake ratings, remote values, new network calls, or database migrations.
- Keep the existing command deck and all current dashboard sections intact.
- Use design-system tokens only; no gradients, glow, looping animation, emoji, or marketing copy.
- All new UI copy goes through English and Simplified Chinese resources.
- A cell must retain a visible text state and never rely on color alone.

## Implementation tasks

### Task 1 — Add surface projection and tests

Create a pure `NOW / NEXT / SIGNAL` projection. `NOW` prioritizes the first incomplete task for
today, `NEXT` prioritizes the earliest due review and then the next contest, and `SIGNAL` reports
sync attention, linked OJs, or local-ready state from real data. Test all branches and target
actions.

### Task 2 — Render actionable signal cells

Render the projection below the command deck using restrained telemetry cells. Route each cell to
the existing training, review, contests, settings, or no-op callbacks; keep empty states visible.
Add content descriptions and a Compose test for the three states and click routing.

### Task 3 — Version, verify, and publish

Advance the package identity, update README/ROADMAP/release notes, run unit tests, Debug/Release
builds, lint, full connected tests, signed Release install smoke, and push the branch, tag, and
GitHub Release only after all checks pass.
