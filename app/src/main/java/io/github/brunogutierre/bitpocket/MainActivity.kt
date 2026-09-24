package io.github.brunogutierre.bitpocket

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.brunogutierre.bitpocket.ui.navigation.BitPocketNavHost
import io.github.brunogutierre.bitpocket.ui.navigation.startRoute
import io.github.brunogutierre.bitpocket.ui.theme.BitPocketTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only used for a fresh back stack; a restored one keeps its own routes.
        val startRoute = (application as BitPocketApp).container.launchState().startRoute()
        setContent {
            BitPocketTheme {
                BitPocketNavHost(startRoute)
            }
        }
    }
}
