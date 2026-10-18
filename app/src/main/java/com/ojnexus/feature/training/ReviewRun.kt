package com.ojnexus.feature.training

/** Returns a determinate progress fraction for the run rail. */
fun reviewRunProgress(total: Int, completed: Int): Float =
    if (total <= 0) 0f else (completed.toFloat() / total).coerceIn(0f, 1f)
