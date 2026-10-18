# Plan — Knowledge auto-linking from tags (Phase 80)

Scope: one deterministic AUTO action on the problem detail KNOWLEDGE section. No schema change,
no network, no AI.

## Steps

1. **`core/model/KnowledgeAutoLink.kt`** — `normalizeKnowledgeAlias(value)` (trim, lowercase,
   collapse inner spaces), `KNOWLEDGE_ALIASES: Map<KnowledgeArea, Set<String>>` covering all ten
   areas in English + Chinese, `autoLinkedKnowledgeAreas(tags)` in enum declaration order,
   `pendingAutoLinks(tags, linked)` = matched minus already-linked.
2. **`KnowledgeDao` + `KnowledgeRepository.autoLink(problemId, tags)`** — add `findForProblem` and
   `upsertAll` to the DAO, hoist `knowledgeDao` to a field, then read the linked set and write
   only the pending areas, returning what was written.
3. **`ProblemDetailViewModel`** — `autoLinkCandidates` in `ProblemDetailUiState` computed inside
   the existing three-flow `combine`; `autoLinkKnowledge()` resolves the problem's tags then calls
   `autoLink`; `clearAutoLinks()` removes only the derived auto set.
4. **`ProblemDetailScreen`** — AUTO / CLEAR AUTO `ActionChip`s (only when non-empty) plus an
   auto-linked count line in the KNOWLEDGE section.
5. **Strings** — `knowledge_auto_link`, `knowledge_auto_link_clear`, `knowledge_auto_link_count`
   in `values/strings.xml` and `values-zh-rCN/strings.xml`.
6. **Tests** — `KnowledgeAutoLinkTest` (11 tests) in `core/model`.

## Gates

- `.\tools\gradlew-local.bat assembleDebug` → BUILD SUCCESSFUL
- `.\tools\gradlew-local.bat test` → BUILD SUCCESSFUL (auto-link tests included)
- `values-zh-rCN` mirrors the new keys with placeholders preserved (LocalizationResourceTest).

## Exit criteria

AUTO links the tag-matched areas in one tap; tapping it again changes nothing; CLEAR AUTO removes
only the auto set; a manual link is never overwritten or removed; unknown tags link nothing.
