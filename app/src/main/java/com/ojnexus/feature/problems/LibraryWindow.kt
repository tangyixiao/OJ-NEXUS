package com.ojnexus.feature.problems

/**
 * Manual library windowing. The full filtered+sorted list stays the source of truth; the
 * LazyColumn only ever tracks a bounded window that grows as the user asks for more, keeping
 * recomposition cost flat at 10k+ rows.
 */
const val LIBRARY_WINDOW_INITIAL = 60
const val LIBRARY_WINDOW_STEP = 60

/** Bounded view over [list]: at most [count] items, never negative. */
fun <T> windowList(list: List<T>, count: Int): List<T> = list.take(count.coerceAtLeast(0))

/** True while the window hides rows that exist in [fullSize]. */
fun hasMoreLibrary(fullSize: Int, visibleCount: Int): Boolean = visibleCount < fullSize
