# Plan — Luogu Public Training Catalog (Phase 95)

Date: 2026-10-18 · Spec: docs/superpowers/specs/2026-10-18-luogu-training-catalog.md

Probe: DONE (see spec — training/list + training/{id} public, ghost 404, team pages 404; 18
official trainings; page 2 ignored by the endpoint).

1. DTOs `LuoguTrainingDtos.kt` (list payload + detail payload, @Serializable, defaults null).
2. `LuoguTrainingCodec.kt` — lentille-context extraction (IndexOf markers, not regex) + kotlinx
   decode, null on mismatch; template/status verified.
3. `LuoguApi` gains `trainingList()` / `training(id)` returning raw `ResponseBody` (HTML), with
   the standard Luogu headers.
4. `LuoguTrainingRepository` — typed errors (TrainingUnavailable for non-200, TrainingNotFound
   for 404), in-memory cache, gate via existing Luogu client path.
5. UI: navigation entries PROBLEMS → TRAININGS → detail; `TrainingCatalogScreen` +
   `TrainingDetailScreen` composables; strings EN/zh; deep link to problem detail.
6. Tests: codec fixture tests (real probe shapes), repository tests (fake api: 200 ok, 404
   typed, template mismatch null), navigation smoke.
7. Gates `test assembleDebug`; versionCode 93 / 0.3.93; ROADMAP prepend; plan mark ✅; commit.

## Result

SHIPPED (v0.3.93). Steps 1–4 as planned: `LuoguTrainingDtos.kt`, `LuoguTrainingCodec.kt`
(marker IndexOf, never throws, template verified), `LuoguApi.trainingListHtml()/trainingHtml(id)`
as `@Streaming` raw HTML `Response<ResponseBody>`, `LuoguTrainingRepository` throwing typed
`LuoguApiError` (HttpError 404 / ParseError on missing payload). UI shipped as
`feature/luogu/LuoguTrainingCatalogScreen.kt` + `LuoguTrainingDetailScreen.kt` with
`LuoguTrainingViewModels.kt` (sealed `LuoguTrainingUiState`: Loading/Empty/Ready/Failed with
notFound), routes `luogu-trainings` + `luogu-training/{id}`, TRAININGS chip entry on the Luogu
remote catalog, deep link into `luogu-problem/{pid}`. In-memory cache dropped (screens refetch on
entry — payload is small and public; state stays honest). Tests: codec 7, repository 4 (fake
`LuoguApi` with fixture HTML bodies, 404 → HttpError, missing payload → ParseError), pure UI
row helpers 3. Gates: `test assembleDebug` BUILD SUCCESSFUL 1m40s; 158 suites / 718 tests /
0 failures. versionCode 93 / 0.3.93; ROADMAP "LUOGU PUBLIC TRAINING CATALOG" prepended;
docs/LONG_RANGE_PLAN.md PHASE 95 ✅.
