package com.ojnexus.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LocalNexusColors = staticCompositionLocalOf<NexusColors> {
    error("NexusColors not provided — wrap content in NexusTheme")
}

private val LocalNexusTypography = staticCompositionLocalOf<NexusTypography> {
    error("NexusTypography not provided — wrap content in NexusTheme")
}

/**
 * Entry point for all design tokens:
 * ```
 * NexusTheme {
 *     NexusTheme.colors.accent
 *     NexusTheme.typography.data
 * }
 * ```
 * The app is dark-first; v0.1 ships exactly one theme. Material 3's scheme is derived from the
 * same tokens so any M3 component used stays consistent.
 */
object NexusTheme {

    val colors: NexusColors
        @Composable
        @ReadOnlyComposable
        get() = LocalNexusColors.current

    val typography: NexusTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalNexusTypography.current
}

@Composable
fun NexusTheme(content: @Composable () -> Unit) {
    val colors = NexusColors.dark()
    val typography = NexusTypography.dark()

    CompositionLocalProvider(
        LocalNexusColors provides colors,
        LocalNexusTypography provides typography,
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterialScheme(),
            typography = typography.toMaterialScheme(),
            content = content,
        )
    }
}

private fun NexusColors.toMaterialScheme() = darkColorScheme(
    primary = accent,
    onPrimary = onAccent,
    primaryContainer = accentContainer,
    onPrimaryContainer = accent,
    secondary = textSecondary,
    onSecondary = background,
    background = background,
    onBackground = textPrimary,
    surface = surface,
    onSurface = textPrimary,
    surfaceVariant = surfaceElevated,
    onSurfaceVariant = textSecondary,
    outline = border,
    outlineVariant = border,
    error = danger,
    onError = Color.Black,
    errorContainer = dangerContainer,
    onErrorContainer = danger,
)

private fun NexusTypography.toMaterialScheme() = androidx.compose.material3.Typography(
    titleLarge = title,
    titleMedium = title,
    titleSmall = label,
    bodyLarge = body,
    bodyMedium = body,
    bodySmall = label,
    labelLarge = label,
    labelMedium = label,
    labelSmall = sectionLabel,
)
