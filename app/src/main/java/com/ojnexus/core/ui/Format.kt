package com.ojnexus.core.ui

import java.util.Locale
import kotlin.math.abs

/**
 * Number formatting for telemetry values. Centralized so every screen renders counts and
 * deltas identically.
 */

/** 1284 -> "1,284" (locale digit grouping). */
fun formatCount(value: Int): String = String.format(Locale.getDefault(), "%,d", value)

/** 24 -> "+24", -13 -> "-13". */
fun formatDelta(value: Int): String = String.format(Locale.getDefault(), "%+d", value)

/** 12 -> "12D" (streak / gap lengths in days). */
fun formatDays(value: Int): String = "${abs(value)}D"

/** 61 -> "61%". */
fun formatPercent(value: Int): String = "$value%"
