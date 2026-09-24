package io.github.brunogutierre.bitpocket.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.brunogutierre.bitpocket.R

// Bundled OFL fonts (licenses in assets/licenses): Inter for UI, JetBrains Mono for numbers,
// addresses, txids and seed words.
val Inter =
    FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
    )

val JetBrainsMono =
    FontFamily(
        Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
        Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
        Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
    )

private fun inter(
    size: Int,
    lineHeight: Int,
    weight: FontWeight = FontWeight.Normal,
    letterSpacing: Double = 0.0,
) = TextStyle(
    fontFamily = Inter,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
)

internal val AppTypography =
    Typography(
        displayLarge = inter(57, 64, FontWeight.SemiBold),
        displayMedium = inter(45, 52, FontWeight.SemiBold),
        displaySmall = inter(36, 44, FontWeight.SemiBold),
        headlineLarge = inter(32, 40, FontWeight.SemiBold),
        headlineMedium = inter(28, 36, FontWeight.SemiBold),
        headlineSmall = inter(24, 32, FontWeight.SemiBold),
        titleLarge = inter(22, 28, FontWeight.Medium),
        titleMedium = inter(16, 24, FontWeight.Medium),
        titleSmall = inter(14, 20, FontWeight.Medium),
        bodyLarge = inter(16, 24),
        bodyMedium = inter(14, 20),
        bodySmall = inter(12, 16),
        labelLarge = inter(14, 20, FontWeight.Medium),
        labelMedium = inter(12, 16, FontWeight.Medium),
        labelSmall = inter(11, 16, FontWeight.Medium, letterSpacing = 0.5),
    )

/** Monospace styles for balances, amounts, addresses, txids and seed words. */
@Immutable
data class MonoTypography(
    val balance: TextStyle,
    val amount: TextStyle,
    val address: TextStyle,
    val small: TextStyle,
)

private fun mono(
    size: Int,
    lineHeight: Int,
    weight: FontWeight,
) = TextStyle(
    fontFamily = JetBrainsMono,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontFeatureSettings = "tnum",
)

internal val AppMonoTypography =
    MonoTypography(
        balance = mono(32, 40, FontWeight.SemiBold),
        amount = mono(16, 24, FontWeight.Medium),
        address = mono(16, 26, FontWeight.Normal),
        small = mono(13, 18, FontWeight.Normal),
    )

val LocalMonoTypography = staticCompositionLocalOf { AppMonoTypography }
