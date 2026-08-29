package com.ojnexus.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * OJ NEXUS typography tokens.
 *
 * Two families: the default sans for UI copy and [FontFamily.Monospace] for all data
 * (ratings, timers, problem codes, verdicts) to keep the telemetry character.
 * Sizes stay compact — high information density over display typography.
 */
@Immutable
data class NexusTypography(
    /** Large telemetry values, e.g. a rating of 1842. */
    val displayData: TextStyle,
    /** Prominent data values inside sections, e.g. a contest countdown. */
    val dataLarge: TextStyle,
    /** Default data value. */
    val data: TextStyle,
    /** Dense data: table cells, tags, status labels. */
    val dataSmall: TextStyle,
    /** Uppercase section headers. Pass already-uppercased strings. */
    val sectionLabel: TextStyle,
    /** Screen/panel titles. */
    val title: TextStyle,
    /** Default body copy. */
    val body: TextStyle,
    /** Small supporting copy. */
    val label: TextStyle,
) {
    companion object {
        fun dark(): NexusTypography {
            val mono = FontFamily.Monospace
            return NexusTypography(
                displayData = TextStyle(
                    fontFamily = mono,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 28.sp,
                    letterSpacing = 0.5.sp,
                ),
                dataLarge = TextStyle(
                    fontFamily = mono,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp,
                    letterSpacing = 0.3.sp,
                ),
                data = TextStyle(
                    fontFamily = mono,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                ),
                dataSmall = TextStyle(
                    fontFamily = mono,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    letterSpacing = 0.2.sp,
                ),
                sectionLabel = TextStyle(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    letterSpacing = 1.4.sp,
                ),
                title = TextStyle(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    letterSpacing = 0.2.sp,
                ),
                body = TextStyle(
                    fontWeight = FontWeight.Normal,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                ),
                label = TextStyle(
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    letterSpacing = 0.4.sp,
                ),
            )
        }
    }
}
