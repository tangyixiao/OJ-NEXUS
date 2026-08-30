package com.ojnexus.core.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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

fun formatRatio(value: Double): String = String.format(Locale.getDefault(), "%.2f", value)

/** 12 -> "12D" (streak / gap lengths in days). */
fun formatDays(value: Int): String = "${abs(value)}D"

/** 61 -> "61%". */
fun formatPercent(value: Int): String = "$value%"

/** 90 -> "1H 30M". */
fun formatDuration(minutes: Long): String {
    val total = abs(minutes)
    return String.format(Locale.getDefault(), "%dH %02dM", total / 60, total % 60)
}

/** 3661 -> "01:01:01" for contest countdowns. */
fun formatCountdown(seconds: Long): String {
    val total = seconds.coerceAtLeast(0L)
    return String.format(
        Locale.getDefault(),
        "%02d:%02d:%02d",
        total / 3_600,
        (total / 60) % 60,
        total % 60,
    )
}

private val dateTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.getDefault())
private val dateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MM-dd", Locale.getDefault())

/** Epoch millis -> "yyyy-MM-dd HH:mm" in the system zone. */
fun formatDateTime(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    dateTimeFormatter.format(Instant.ofEpochMilli(epochMs).atZone(zone))

/** Epoch millis -> "MM-dd" in the system zone. */
fun formatDate(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    dateFormatter.format(Instant.ofEpochMilli(epochMs).atZone(zone))
