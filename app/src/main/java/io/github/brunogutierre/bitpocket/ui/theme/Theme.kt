package io.github.brunogutierre.bitpocket.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * BitPocket theme. Dynamic color is intentionally off: the identity, the status colors and the
 * teal "test money" marker must look the same on every device. Elevation is tonal only.
 */
@Composable
fun BitPocketTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalExtendedColors provides if (darkTheme) DarkExtendedColors else LightExtendedColors,
        LocalMonoTypography provides AppMonoTypography,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

val MaterialTheme.extendedColors: ExtendedColors
    @Composable
    @ReadOnlyComposable
    get() = LocalExtendedColors.current

val MaterialTheme.monoTypography: MonoTypography
    @Composable
    @ReadOnlyComposable
    get() = LocalMonoTypography.current
