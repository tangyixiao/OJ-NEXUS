# PHASE 79 — REVIEW DECK SHUFFLE & CRAM MODE

Date: 2026-10-17
Status: APPROVED FOR IMPLEMENTATION
Owner: OJ NEXUS

## Problem

`ReviewRunViewModel` captures the due queue once, in `due_day_index ASC, due_at ASC,
problem_id ASC` order (`ReviewRepository.observeQueue()`), and replays it unchanged.
Every run of the same deck is identical, so users memorize sequence positions instead
of recalling the material. There is no way to drill a chosen slice (one judge, one
knowledge tag) outside the schedule.

## Goal

Review Run gains:

1. A deterministic, seeded shuffle (seeded by the local date), toggleable per run.
2. A CRAM mode that ignores the schedule and drills a user-picked judge + knowledge
   tag slice, without changing review recording semantics.

`PASS / HARD / FAIL / SKIP` behaviour, `ReviewScheduler` intervals, the review
transaction and the Room schema stay exactly as they are.

## Non-goals

- No new persistence: no schema change, no Room version bump, no migration.
- No network, no remote catalogue, no AI/inference, no handle authentication.
- No change to `ReviewRunViewModel.record()` scheduling maths or `ReviewResult`
  handling.
- No review-deck editor: the deck is derived, not stored.

## Design

### 1. Pure ordering helpers (`feature/training/ReviewDeck.kt`)

```kotlin
enum class ReviewDeckMode { SCHEDULE, SHUFFLE, CRAM }

data class ReviewDeck(
    val items: List<ReviewQueueItem>,
    val mode: ReviewDeckMode,
    val seed: Long,
)

fun reviewDeckSeed(epochDay: Long): Long
fun reviewDeckShuffled(items: List<ReviewQueueItem>, seed: Long): List<ReviewQueueItem>
fun buildReviewDeck(items, mode, seed, filter): ReviewDeck
```

- `reviewDeckSeed(epochDay)`: `epochDay * 31 + 0x9E3779B9L`, masked to 62 bits.
  Same local day ⇒ same deck on every device and every process start. Different day
  ⇒ different deck.
- `reviewDeckShuffled(items, seed)`: Fisher–Yates over a copy using
  `java.util.Random(seed)` (JDK-stable algorithm, no Android dependency) and a
  tie-break comparator `dueDayIndex, dueAt, problemId` applied *before* shuffling,
  so the input is total and the shuffle is deterministic. Output keeps every input
  item exactly once; the schedule order is recovered when the seed sequence happens
  to be the identity, which is acceptable.
- `buildReviewDeck(items, mode, seed, filter)`:
  - `SCHEDULE` → schedule order, seed ignored.
  - `SHUFFLE` → `reviewDeckShuffled(items, seed)`.
  - `CRAM` → `filter.cramMatches(item)` then shuffle by the same seed, so a cram deck
    is a stable slice in a stable order.
- `filter.cramMatches(item)`: `judge == cramJudge && tags.any { it.equals(cramTag, ignoreCase = true) }`.
  A blank cramTag matches every tag (judge-only slice); an empty cramJudge matches no
  judge, i.e. an unusable filter, guarded in the UI.

### 2. Cram filter model

```kotlin
data class ReviewCramFilter(
    val judge: String? = null,
    val tag: String = "",
)
```

Stored on `ReviewRunUiState` (`cramFilter`), never persisted. Tags come from
`ReviewQueueItem.tags`; judges from the distinct `ReviewQueueItem.judge.displayName`
values present in the captured queue (local-only derivation, no OJ enumeration).

### 3. ViewModel (`ReviewRunViewModel`)

- New `MutableStateFlow<ReviewDeckMode>` defaulting to `SCHEDULE` and
  `MutableStateFlow<ReviewCramFilter>`; both are captured into `ReviewRunInternalState`
  at capture time (mode + filter are snapshotted with the deck, so a mid-run toggle
  never reorders the running deck).
- Capture path becomes
  `buildReviewDeck(items, mode, seed, filter)` instead of
  `captureReviewRunQueue(items, todayEpochDay.value)`.
- `fun setDeckMode(mode: ReviewDeckMode)`: if the deck is not yet started
  (`completedCount == 0 && currentId == null` → no items recorded) update mode and
  recapture; otherwise keep the running deck and surface nothing (silent no-op) — a
  run in progress must never reorder under the user.
- `fun setCramFilter(filter: ReviewCramFilter)`: same guard; on success recaptures.
- Existing `captureReviewRunQueue(queue, todayEpochDay)` stays for the Training
  dashboard pulse (`ReviewRunTest` keeps passing untouched) and now delegates to
  `buildReviewDeck(items, ReviewDeckMode.SCHEDULE, 0L, ReviewCramFilter())`.
- `record()` / `next()` are unchanged; ordering is the only behavioural change.

### 4. UI (`ReviewRunScreen.kt`)

- Deck-mode selector row above the progress section: `SCHEDULE / SHUFFLE / CRAM`
  using the existing `ReviewFilterButton` pattern (accent border when selected).
- When CRAM is selected a compact filter row appears: judge chips (from the captured
  queue's distinct judges) + a text field for the tag. An unusable filter (no judge
  picked) disables the CRAM chip and shows a hint.
- Everything is reduced-motion safe: the deck row only re-flows via the existing
  `animateContentSize(reduceMotion ? snap() : tween(...))` used by the screen.

### 5. Strings

New keys in `res/values/strings.xml` + `values-zh-rCN/strings.xml`:

`review_deck_mode`, `review_deck_schedule`, `review_deck_shuffle`, `review_deck_cram`,
`review_deck_cram_judge`, `review_deck_cram_tag`, `review_deck_cram_hint`,
`review_deck_cram_empty`, `review_deck_shuffle_seed`, `review_deck_cram_selected`
(`%1$d`).

## Definition of done

- Unit tests (pure JVM, `ReviewDeckTest`): seed stability across days, shuffle
  determinism, shuffle permutation invariant (same multiset), shuffle differs from
  schedule order for a non-trivial deck, cram filter matches judge+tag (case
  insensitive), cram ignores due-day entirely, blank tag matches all tags, null judge
  matches nothing, `captureReviewRunQueue` unchanged ordering.
- ViewModel test (`ReviewRunViewModelTest`): SHUFFLE changes order deterministically,
  mid-run mode toggle does not reorder, CRAM slice only contains the picked judge.
- `.\tools\gradlew-local.bat assembleDebug` and `.\tools\gradlew-local.bat test` green.
- Loading / Empty / Error states handled (empty cram slice shows `review_deck_cram_empty`).
- No schema change, no migration, no network, no credential flow.
