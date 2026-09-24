package io.github.brunogutierre.bitpocket.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Fixed identity (no dynamic color): calm slate surfaces, one Bitcoin-amber accent.
// Tertiary (teal) is reserved for "test money" and must never be used for anything else.

internal val LightColors =
    lightColorScheme(
        primary = Color(0xFFB45309),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFFEDD5),
        onPrimaryContainer = Color(0xFF7C2D12),
        inversePrimary = Color(0xFFFDBA74),
        secondary = Color(0xFF475569),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFE2E8F0),
        onSecondaryContainer = Color(0xFF0F172A),
        tertiary = Color(0xFF0F766E),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFCCFBF1),
        onTertiaryContainer = Color(0xFF134E4A),
        background = Color(0xFFF8FAFC),
        onBackground = Color(0xFF0F172A),
        surface = Color(0xFFF8FAFC),
        onSurface = Color(0xFF0F172A),
        surfaceVariant = Color(0xFFDDE3EA),
        onSurfaceVariant = Color(0xFF475569),
        surfaceTint = Color(0xFFB45309),
        inverseSurface = Color(0xFF1E293B),
        inverseOnSurface = Color(0xFFF1F5F9),
        error = Color(0xFFB91C1C),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFEE2E2),
        onErrorContainer = Color(0xFF7F1D1D),
        outline = Color(0xFF64748B),
        outlineVariant = Color(0xFFCBD5E1),
        surfaceBright = Color(0xFFF8FAFC),
        surfaceDim = Color(0xFFE2E8F0),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF1F5F9),
        surfaceContainer = Color(0xFFEEF2F6),
        surfaceContainerHigh = Color(0xFFE2E8F0),
        surfaceContainerHighest = Color(0xFFDDE3EA),
    )

internal val DarkColors =
    darkColorScheme(
        primary = Color(0xFFFDBA74),
        onPrimary = Color(0xFF431407),
        primaryContainer = Color(0xFF9A3412),
        onPrimaryContainer = Color(0xFFFFEDD5),
        inversePrimary = Color(0xFFB45309),
        secondary = Color(0xFFCBD5E1),
        onSecondary = Color(0xFF1E293B),
        secondaryContainer = Color(0xFF334155),
        onSecondaryContainer = Color(0xFFE2E8F0),
        tertiary = Color(0xFF5EEAD4),
        onTertiary = Color(0xFF042F2E),
        tertiaryContainer = Color(0xFF115E59),
        onTertiaryContainer = Color(0xFFCCFBF1),
        background = Color(0xFF0B1120),
        onBackground = Color(0xFFE2E8F0),
        surface = Color(0xFF0B1120),
        onSurface = Color(0xFFE2E8F0),
        surfaceVariant = Color(0xFF273449),
        onSurfaceVariant = Color(0xFF94A3B8),
        surfaceTint = Color(0xFFFDBA74),
        inverseSurface = Color(0xFFE2E8F0),
        inverseOnSurface = Color(0xFF1E293B),
        error = Color(0xFFFCA5A5),
        onError = Color(0xFF450A0A),
        errorContainer = Color(0xFF7F1D1D),
        onErrorContainer = Color(0xFFFEE2E2),
        outline = Color(0xFF64748B),
        outlineVariant = Color(0xFF334155),
        surfaceBright = Color(0xFF1E293B),
        surfaceDim = Color(0xFF0B1120),
        surfaceContainerLowest = Color(0xFF070B14),
        surfaceContainerLow = Color(0xFF111827),
        surfaceContainer = Color(0xFF0F172A),
        surfaceContainerHigh = Color(0xFF1E293B),
        surfaceContainerHighest = Color(0xFF273449),
    )

/** Status colors outside the M3 roles (exposed through [LocalExtendedColors]). */
@Immutable
data class ExtendedColors(
    val success: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color,
)

internal val LightExtendedColors =
    ExtendedColors(
        success = Color(0xFF15803D),
        successContainer = Color(0xFFDCFCE7),
        warning = Color(0xFFB45309),
        warningContainer = Color(0xFFFEF3C7),
    )

internal val DarkExtendedColors =
    ExtendedColors(
        success = Color(0xFF4ADE80),
        successContainer = Color(0xFF14532D),
        warning = Color(0xFFFBBF24),
        warningContainer = Color(0xFF451A03),
    )

val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }
