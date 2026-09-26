package io.github.brunogutierre.bitpocket.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * Horizontal error shake, replayed each time [trigger] changes (0 = never). Compose scales
 * animation durations with the system setting, so it is skipped when animations are off.
 */
@Composable
fun Modifier.shake(trigger: Int): Modifier {
    val offset = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        offset.animateTo(
            targetValue = 0f,
            animationSpec =
                keyframes {
                    durationMillis = 300
                    -12f at 50
                    12f at 100
                    -8f at 150
                    8f at 200
                    -4f at 250
                },
        )
    }
    return offset { IntOffset(offset.value.dp.roundToPx(), 0) }
}
