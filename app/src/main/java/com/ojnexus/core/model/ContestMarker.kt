package com.ojnexus.core.model

/** Local-only marker for tracking a problem during a contest. */
enum class ContestMarker {
    NONE,
    WORKING,
    SOLVED,
    SKIPPED,
    ;

    fun next(): ContestMarker = entries[(ordinal + 1) % entries.size]
}
