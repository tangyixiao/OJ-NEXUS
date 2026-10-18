# Spec — Luogu Public Training Catalog (Phase 95)

## Probe result (documented before any UI, per DoD)

Probed from this host 2026-10-18, no auth, no cookies, official pages only:

- `GET https://www.luogu.com.cn/training/list` → 200, HTML page embedding a
  `<script id="lentille-context" type="application/json">` payload with
  `{"template":"training.list","data":{"trainings":{"perPage":18,"count":18,"result":[…]}}}`.
  Each row: `{id, name, type, provider{uid,name,color,…}, createTime, deadline, problemCount,
  markCount}`. 18 official public trainings total; `?page=2` returns the same payload (the list
  endpoint ignores out-of-range pages).
- `GET https://www.luogu.com.cn/training/{id}` → 200 with `template:"training.show"`,
  `data.training{…, description, problems:[…]}`; each problem entry
  `{pid, type, name, difficulty, fullScore, tags[int], totalSubmit, totalAccepted, flag,
  provider}`.
- `GET /training/99999999` → real 404 (NotFoundHttpException), so missing ids are detectable.
- Team-scoped pages (`/team`, team training pages) → 404 — team data is NOT public; this phase
  stays strictly on the public catalog.

## Goal

Read-only public training catalog: browse the official Luogu training list, open one training to
see its ordered problem ladder, and jump to a problem's local detail.

## Constraints

- Public pages only — no credential flow, no cookies, no scraping of private/team data.
- The lentille-context JSON is embedded in HTML; parse it defensively (template + status
  mismatch → typed failure), never regex into arbitrary markup.
- No schema change; cache is in-memory per session (catalog is small and refetch is cheap).
- Bounded payload: page 1 only for the list (18 items); single training detail per open.
- Reuse the existing Luogu request gate; honor the shared retry/error taxonomy.

## Design

- `judge/luogu/api/LuoguApi.kt` — add `trainingList()` and `training(id)` as plain
  `@GET` returning the raw lentille HTML (`ResponseBody`), parsed by a dedicated codec rather
  than pretending the page is JSON.
- `judge/luogu/api/dto/LuoguTrainingDtos.kt` — `@Serializable` list/detail payload models
  matching the probed shape (`training.list` / `training.show` templates).
- `judge/luogu/LuoguTrainingCodec.kt` — extracts the `lentille-context` script content and
  decodes with kotlinx.serialization; never throws (null on any structural mismatch).
- `judge/luogu/LuoguTrainingRepository.kt` — `suspend list(): LuoguTrainingCatalog?` and
  `suspend training(id): LuoguTrainingDetail?` with in-memory cache + typed probe errors.
- UI: Problems screen gets a TRAININGS entry point → `TrainingCatalogScreen` (list of cards:
  name, provider, problem count, marks) → `TrainingDetailScreen` (ordered ladder rows; tap
  navigates to the problem detail). All strings via resources, EN + zh.
- Tests: codec tests on fixture HTML (real probe shape, template mismatch, ghost 404 → typed
  error), repository tests with a fake api, navigation test.

## DoD

- Capability probe documented above before any UI.
- Fixtures tests decode the real payload shape; 404 surfaces a typed error, not a crash.
- Gates: full `test assembleDebug` BUILD SUCCESSFUL.
