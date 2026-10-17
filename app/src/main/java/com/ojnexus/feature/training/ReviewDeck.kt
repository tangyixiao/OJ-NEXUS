package com.ojnexus.feature.training

import com.ojnexus.core.model.ReviewQueueItem
import java.util.Random

/**
 * How a review run deck is ordered.
 *
 * - [SCHEDULE] — the due-day schedule order (today's behaviour, deterministic).
 * - [SHUFFLE] — the same deck in a seeded shuffle, stable for one local day.
 * - [CRAM] — a user-picked judge + tag slice, ignoring the schedule entirely.
 */
enum class ReviewDeckMode {
    SCHEDULE,
    SHUFFLE,
    CRAM,
}

/**
 * Cram slice chosen by the user: one judge and an optional knowledge tag.
 *
 * The judge is matched by [com.ojnexus.core.model.JudgeId.displayName] because that is
 * the only judge identity a review queue row carries; the tag is matched
 * case-insensitively against the problem's own tags.
 */
data class ReviewCramFilter(
    val judge: String? = null,
    val tag: String = "",
) {
    /** A cram deck needs a judge; the tag alone never selects a judge. */
    val isUsable: Boolean get() = !judge.isNullOrBlank()

    /** True when the row belongs to the chosen judge and matches the tag filter. */
    fun cramMatches(item: ReviewQueueItem): Boolean {
        val chosenJudge = judge?.takeIf { it.isNotBlank() } ?: return false
        if (item.judge.displayName != chosenJudge) return false
        val tag = this.tag.trim()
        return tag.isEmpty() || item.tags.any { it.equals(tag, ignoreCase = true) }
    }
}

/**
 * One review run deck: the captured rows plus how they were ordered.
 *
 * [seed] is the local-day seed that produced the order, kept so the UI can show why the
 * deck is stable and so tests can assert determinism.
 */
data class ReviewDeck(
    val items: List<ReviewQueueItem>,
    val mode: ReviewDeckMode,
    val seed: Long,
) {
    val isEmpty: Boolean get() = items.isEmpty()
}

/**
 * Stable per-day shuffle seed. Same local day ⇒ same deck on every device and every
 * process start; the next day reshuffles. 62 bits keep it positive for [Random].
 */
fun reviewDeckSeed(epochDay: Long): Long =
    (epochDay * 31L + 0x9E3779B9L) and 0x3FFF_FFFF_FFFF_FFFFL

/** Total order the deck modes share: due day, then due time, then problem id. */
private val SCHEDULE_ORDER: Comparator<ReviewQueueItem> =
    compareBy<ReviewQueueItem> { it.dueDayIndex }
        .thenBy { it.dueAt }
        .thenBy { it.problemId }

/**
 * Captures only the reviews due when a focused run starts, in [SCHEDULE_ORDER].
 *
 * Orders the same way as the [ReviewDeckMode.SCHEDULE] deck so a run and a deck never
 * disagree about "what comes first".
 */
fun captureReviewRunQueue(
    queue: List<ReviewQueueItem>,
    todayEpochDay: Long,
): List<ReviewQueueItem> = queue
    .filter { it.dueDayIndex <= todayEpochDay }
    .sortedWith(SCHEDULE_ORDER)

/**
 * Seeded shuffle. The input is first put into a total schedule order so the shuffle is
 * independent of how the caller happened to order the rows; Fisher–Yates then permutes
 * the copy with [java.util.Random] (a JDK-stable algorithm, no Android dependency).
 *
 * The output is a permutation of the input: every row appears exactly once.
 */
fun reviewDeckShuffled(items: List<ReviewQueueItem>, seed: Long): List<ReviewQueueItem> {
    if (items.size < 2) return items
    val ordered = items.sortedWith(SCHEDULE_ORDER).toMutableList()
    val random = Random(seed)
    for (index in ordered.indices.reversed()) {
        val swap = random.nextInt(index + 1)
        if (swap == index) continue
        val previous = ordered[index]
        ordered[index] = ordered[swap]
        ordered[swap] = previous
    }
    return ordered
}

/**
 * Builds the deck for one run.
 *
 * [SCHEDULE] keeps the schedule order and ignores [seed]; [SHUFFLE] shuffles the whole
 * queue; [CRAM] first narrows to [filter] and then shuffles the slice with the same
 * seed, so a cram deck is stable too.
 */
fun buildReviewDeck(
    items: List<ReviewQueueItem>,
    mode: ReviewDeckMode,
    seed: Long,
    filter: ReviewCramFilter = ReviewCramFilter(),
): ReviewDeck = when (mode) {
    ReviewDeckMode.SCHEDULE -> ReviewDeck(items.sortedWith(SCHEDULE_ORDER), mode, seed)
    ReviewDeckMode.SHUFFLE -> ReviewDeck(reviewDeckShuffled(items, seed), mode, seed)
    ReviewDeckMode.CRAM -> ReviewDeck(
        reviewDeckShuffled(items.filter { filter.cramMatches(it) }, seed),
        mode,
        seed,
    )
}
