package io.github.brunogutierre.bitpocket.ui.components

import android.view.Window
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import java.util.WeakHashMap

// Secure screens currently shown per window. During a transition between two secure screens the
// new one appears before the old one is disposed, so the flag is cleared only at zero.
private val secureScreens = WeakHashMap<Window, Int>()

/**
 * Blocks screenshots, screen recording and the recents thumbnail (FLAG_SECURE) while [content]
 * is shown. Used for recovery words in every build type.
 */
@Composable
fun SecureScreen(content: @Composable () -> Unit) {
    val window = LocalActivity.current?.window
    DisposableEffect(window) {
        if (window != null) {
            secureScreens[window] = (secureScreens[window] ?: 0) + 1
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            if (window != null) {
                val remaining = (secureScreens[window] ?: 1) - 1
                if (remaining <= 0) {
                    secureScreens.remove(window)
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    secureScreens[window] = remaining
                }
            }
        }
    }
    content()
}
