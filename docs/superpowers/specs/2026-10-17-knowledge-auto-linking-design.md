# 2026-10-17 — Knowledge auto-linking (tag alias table)

## Context

Phase 80 of `docs/LONG_RANGE_PLAN.md` is "Knowledge auto-linking from tags": the app already lets
the user link a problem to knowledge areas by hand on the problem detail screen, but a Codeforces
problem tagged `dp` or `graphs` starts with zero links, so the mastery chart is empty until the
user remembers to tap ten tags per problem.

The goal is a one-tap deterministic bridge from the tags the problem already carries locally to
the knowledge areas the mastery engine already understands. This is a data-shaping feature, not a
network or AI feature: no request leaves the device, and the alias table is plain Kotlin.

## What exists today

- `core/model/KnowledgeArea.kt` — ten areas (`DATA_STRUCTURE`, `GRAPH`, `DYNAMIC_PROGRAMMING`,
  `STRING`, `MATH`, `GEOMETRY`, `GREEDY`, `SEARCH`, `CONSTRUCTION`, `GAME_THEORY`).
- `core/domain/MasteryEngine.kt` + `KnowledgeEvidence` — mastery is a pure function of attempt
  counts per area; linking more problems only makes the evidence denser.
- `core/data/repository/KnowledgeRepository.kt` — `observeRelations(problemId)`,
  `observeMastery()`, `setRelation(problemId, area, selected)` over
  `problem_knowledge(problem_id, area)`.
- `feature/problems/ProblemDetailViewModel.kt` / `ProblemDetailScreen.kt` — a two-column grid of
  `NexusTag` chips, one per area, toggling `setKnowledge`.
- `core/data/database/entity/ProblemEntity.kt` — the local `problems` row has no tag column; tags
  live on `RemoteProblemEntity.tags` / `RemoteProblemDetailEntity.tags` as serialized lists, and
  `core/model/Problem.kt:7` exposes `val tags: List<String>` in the domain model.

## Decisions

1. **The alias table is a pure map in `core/model`, not a database table.**
   `KnowledgeAutoLink.kt` owns `normalizeKnowledgeAlias(value)` and
   `val KNOWLEDGE_ALIASES: Map<KnowledgeArea, Set<String>>`. Ten areas, English + Chinese
   aliases, hand-written. A Room table would add a migration for a list of strings that changes
   only when a judge renames a tag, and the plan's own constraint is "no schema change".
2. **Matching is exact-after-normalize, never fuzzy.**
   `autoLinkedKnowledgeAreas(tags)` = every area that owns at least one alias equal to a
   normalized tag. `flow`, `srt`, `trees` link nothing. No stemming, no substring match, no
   scoring — an unknown tag is simply unlinked, and the user can still link by hand. This keeps
   the link plan predictable and testable, and it can never link an area the tags do not claim.
3. **Result order is enum declaration order, not input order.**
   `KnowledgeArea.entries.filter { ... }` guarantees the same tags always produce the same list,
   so a UI chip row never reshuffles between renders.
4. **One alias belongs to one area.** The test `alias table never maps one alias to two areas`
   fails the build if two areas ever claim the same alias, so a tag can never create two
   competing links.
5. **Auto-linking never overwrites a manual link.**
   `KnowledgeRepository.autoLink(problemId, tags)` reads the currently linked set first and writes
   only `pendingAutoLinks(tags, existing)`. Tapping AUTO twice links nothing the second time, and
   a manual unlink is not resurrected.
6. **CLEAR AUTO removes only auto-linked areas.**
   `clearAutoLinks()` re-derives the auto set from the problem's current tags and removes those
   relations only. A hand-picked link survives, so the user's own curation is never destroyed by
   a tag edit.
7. **The candidate count lives in UI state, not a database write.**
   `ProblemDetailUiState.autoLinkCandidates` is `pendingAutoLinks(detail.problem.tags,
   knowledge)` recomputed inside the existing `combine(detail, knowledge, noteTemplate)`. No new
   flow, no new subscription; the AUTO / CLEAR AUTO chips appear and disappear with the data.

## Files

- `core/model/KnowledgeAutoLink.kt` (new) — `normalizeKnowledgeAlias`, `KNOWLEDGE_ALIASES`,
  `autoLinkedKnowledgeAreas(tags)`, `pendingAutoLinks(tags, linked)`.
- `core/database/dao/KnowledgeDao.kt` — `findForProblem(problemId)` and `upsertAll(entities)` for
  the repository's auto-link write.
- `core/data/repository/KnowledgeRepository.kt` — added
  `suspend fun autoLink(problemId, tags): List<KnowledgeArea>`; hoisted the `knowledgeDao` field
  so `setRelation` and `autoLink` share one reference.
- `feature/problems/ProblemDetailViewModel.kt` — `autoLinkCandidates` in `ProblemDetailUiState`,
  `autoLinkKnowledge()` and `clearAutoLinks()`.
- `feature/problems/ProblemDetailScreen.kt` — AUTO / CLEAR AUTO `ActionChip`s in the KNOWLEDGE
  section plus a "N AUTO-LINKED" summary line.
- `strings.xml` / `values-zh-rCN/strings.xml` — `knowledge_auto_link`, `knowledge_auto_link_clear`,
  `knowledge_auto_link_count`.
- `app/src/test/java/com/ojnexus/core/model/KnowledgeAutoLinkTest.kt` (new) — 11 tests.

## Tests

`KnowledgeAutoLinkTest` covers: exact alias linking, case/whitespace normalization, Chinese
aliases, one area per alias match, declaration-order output, unknown tags linking nothing, blank
tags ignored, the alias table being one-alias-one-area, every area owning an alias,
`pendingAutoLinks` subtracting the linked set, and determinism across repeated calls.

## Out of scope

Mastery thresholds, tag ingestion for non-Codeforces judges, and any tag-based search are later
phases. Nothing here writes to a network surface.

The feature adds no network request, no Room migration and no credential flow; it only links
existing local tags to existing local knowledge areas.
