# Implementation Plan — Phase 79 Review deck shuffle & cram mode

Per `docs/LONG_RANGE_PLAN.md` procedure. Spec: `docs/superpowers/specs/2026-10-17-review-deck-shuffle-cram-design.md`.

## Step 1 — Pure deck model

Create `app/src/main/java/com/ojnexus/feature/training/ReviewDeck.kt`:

- `enum class ReviewDeckMode { SCHEDULE, SHUFFLE, CRAM }`
- `data class ReviewCramFilter(val judge: String? = null, val tag: String = "")` with
  `val isUsable: Boolean get() = judge != null && judge.isNotBlank()`
- `data class ReviewDeck(val items: List<ReviewQueueItem>, val mode: ReviewDeckMode, val seed: Long)`
- `fun reviewDeckSeed(epochDay: Long): Long = (epochDay * 31L + 0x9E3779B9L) and 0x3FFF_FFFF_FFFF_FFFFL`
- `private val SCHEDULE_COMPARATOR` = `compareBy<ReviewQueueItem> { it.dueDayIndex }.thenBy { it.dueAt }.thenBy { it.problemId }`
- `fun reviewDeckShuffled(items, seed)`: sort with the comparator, Fisher–Yates with
  `java.util.Random(seed)` on a mutable copy, return as list.
- `fun buildReviewDeck(items, mode, seed, filter)`:
  - `SCHEDULE` → sorted schedule order.
  - `SHUFFLE` → `reviewDeckShuffled(items, seed)`.
  - `CRAM` → `items.filter { filter.cramMatches(it) }` then shuffle.

`cramMatches` lives on `ReviewCramFilter`:
```kotlin
fun cramMatches(item: ReviewQueueItem): Boolean {
    val judge = this.judge ?: return false
    if (item.judge.displayName != judge) return false
    val tag = this.tag.trim()
    return tag.isEmpty() || item.tags.any { it.equals(tag, ignoreCase = true) }
}
```

## Step 2 — ViewModel wiring

`app/src/main/java/com/ojnexus/feature/training/ReviewRunViewModel.kt`:

- `ReviewRunInternalState` gains `mode: ReviewDeckMode = SCHEDULE`, `cramFilter: ReviewCramFilter = ReviewCramFilter()`.
- `ReviewRunUiState` gains `mode`, `cramFilter`, `availableJudges: List<String>` (derived from captured), and `deckSeed: Long`.
- Capture: `buildReviewDeck(items, internal.value.mode, reviewDeckSeed(todayEpochDay.value), internal.value.cramFilter)`.
- `fun setDeckMode(mode: ReviewDeckMode)`: no-op while a run is in progress
  (`captured != null && completedIds.isNotEmpty()`), else update mode and recapture
  from the current `queue.value`.
- `fun setCramFilter(filter: ReviewCramFilter)`: same guard; only usable filters are
  accepted for CRAM (SCHEDULE/SHUFFLE ignore the filter but keep it stored).
- `captureReviewRunQueue(queue, todayEpochDay)` keeps its signature and delegates to
  `buildReviewDeck(items, SCHEDULE, 0L, ReviewCramFilter())`, preserving the existing
  dashboard behaviour and `ReviewRunTest`.

## Step 3 — UI

`app/src/main/java/com/ojnexus/feature/training/ReviewRunScreen.kt`:

- Deck-mode row (`SCHEDULE / SHUFFLE / CRAM`) rendered with the existing
  `ReviewFilterButton` visual pattern, wired to `viewModel::setDeckMode`.
- CRAM row: judge chips from `uiState.availableJudges` + a tag text field
  (`DialogField`-style BasicTextField already used in the codebase) + hint text.
- Empty cram deck → `ReviewRunEmpty` with the cram-specific message.
- Pass new callbacks (`onDeckMode`, `onCramFilter`) through `ReviewRunContent`.

## Step 4 — Strings

`app/src/main/res/values/strings.xml` and `values-zh-rCN/strings.xml`:

```
review_deck_mode = "DECK MODE"
review_deck_schedule = "SCHEDULE"
review_deck_shuffle = "SHUFFLE"
review_deck_cram = "CRAM"
review_deck_cram_judge = "JUDGE"
review_deck_cram_tag = "TAG"
review_deck_cram_hint = "PICK A JUDGE AND OPTIONALLY A TAG."
review_deck_cram_empty = "NO PROBLEMS MATCH THIS CRAM FILTER."
review_deck_cram_all_judges = "ALL JUDGES"
```

zh-rCN mirrors with Chinese text, placeholders preserved.

## Step 5 — Tests

`app/src/test/java/com/ojnexus/feature/training/ReviewDeckTest.kt` (pure JVM):

1. seed is stable for the same day and differs across days.
2. shuffle is deterministic for a fixed seed (two calls, same order).
3. shuffle is a permutation of the input (same size, same ids).
4. shuffle differs from schedule order for a non-trivial deck.
5. schedule mode ignores the seed and returns schedule order.
6. cram matches judge + case-insensitive tag.
7. cram with a blank tag keeps every problem of that judge.
8. cram with a null judge matches nothing.
9. `captureReviewRunQueue` still returns due-only items in due-time then id order.

`ReviewRunViewModelTest` additions (Robolectric):

10. SHUFFLE yields a deterministic order and completes all items.
11. toggling the mode mid-run does not reorder the running deck.
12. CRAM slice only contains the chosen judge.

## Step 6 — Gates, docs, commit

- `.\tools\gradlew-local.bat assembleDebug`
- `.\tools\gradlew-local.bat test`
- versionCode 77, versionName 0.3.77.
- Prepend `## PHASE 79 — REVIEW DECK SHUFFLE & CRAM MODE` to `docs/ROADMAP.md`
  (EN + / zh), ending with the no-network / no-migration / no-credential boundary
  sentence.
- Mark Phase 79 ✅ (v0.3.77, 2026-10-17) in `docs/LONG_RANGE_PLAN.md`.
- Commit `feat: ...`; commit often.
