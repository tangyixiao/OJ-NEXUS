package com.ojnexus.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

enum class NexusThemeSlot {
    NEXUS_BLUE,
    TERMINAL_GREEN,
    AMBER_SIGNAL,
}

/**
 * OJ NEXUS color tokens — the ONLY source of color in the app.
 *
 * Dark-first, single accent (NEXUS BLUE). Feature code must never call Color(0xFF...) directly;
 * everything resolves through [NexusTheme.colors].
 *
 * `*Container` tokens are pre-composited dark fills tinted by their status color. They exist so
 * status fills stay opaque (stable rendering) instead of stacking translucent overlays.
 */
@Immutable
data class NexusColors(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val border: Color,
    val borderStrong: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val onAccent: Color,
    val accentContainer: Color,
    val success: Color,
    val successContainer: Color,
    val danger: Color,
    val dangerContainer: Color,
    val warning: Color,
    val warningContainer: Color,
) {
    companion object {
        fun dark(slot: NexusThemeSlot = NexusThemeSlot.NEXUS_BLUE): NexusColors {
            val accent = when (slot) {
                NexusThemeSlot.NEXUS_BLUE -> AccentPalette(
                    accent = Color(0xFF4FA1FF),
                    onAccent = Color(0xFF05101E),
                    container = Color(0xFF14273D),
                )
                NexusThemeSlot.TERMINAL_GREEN -> AccentPalette(
                    accent = Color(0xFF3ECF8E),
                    onAccent = Color(0xFF04170E),
                    container = Color(0xFF123125),
                )
                NexusThemeSlot.AMBER_SIGNAL -> AccentPalette(
                    accent = Color(0xFFE3B341),
                    onAccent = Color(0xFF211802),
                    container = Color(0xFF332914),
                )
            }
            return NexusColors(
            background = Color(0xFF090B0D),
            surface = Color(0xFF101317),
            surfaceElevated = Color(0xFF161B21),
            border = Color(0xFF262B31),
            borderStrong = Color(0xFF39414B),
            textPrimary = Color(0xFFECEFF1),
            textSecondary = Color(0xFF9AA3AC),
            textTertiary = Color(0xFF5F6871),
            accent = accent.accent,
            onAccent = accent.onAccent,
            accentContainer = accent.container,
            success = Color(0xFF3ECF8E),
            successContainer = Color(0xFF12291F),
            danger = Color(0xFFE5484D),
            dangerContainer = Color(0xFF2B1416),
            warning = Color(0xFFE3B341),
            warningContainer = Color(0xFF2B2413),
            )
        }

        private data class AccentPalette(
            val accent: Color,
            val onAccent: Color,
            val container: Color,
        )
    }
}
