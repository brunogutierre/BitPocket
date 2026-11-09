package io.github.brunogutierre.bitpocket

import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import io.github.brunogutierre.bitpocket.ui.navigation.BitPocketNavHost
import io.github.brunogutierre.bitpocket.ui.navigation.startRoute
import io.github.brunogutierre.bitpocket.ui.theme.BitPocketTheme

/** A FragmentActivity because BiometricPrompt (androidx.biometric 1.1.0) needs one. */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // The recents screen shows a blank card instead of balances and addresses.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(false)
        // Only used for a fresh back stack; a restored one keeps its own routes.
        val container = (application as BitPocketApp).container
        val startRoute = container.launchState().startRoute()
        setContent {
            BitPocketTheme {
                BitPocketNavHost(container, startRoute)
            }
        }
    }
}
