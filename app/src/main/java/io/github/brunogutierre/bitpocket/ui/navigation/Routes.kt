package io.github.brunogutierre.bitpocket.ui.navigation

import androidx.navigation3.runtime.NavKey
import io.github.brunogutierre.bitpocket.core.session.LaunchState
import kotlinx.serialization.Serializable

// Routes are @Serializable so the back stack survives configuration changes and process death.

@Serializable
data object Welcome : NavKey

@Serializable
data object CreateSeed : NavKey

@Serializable
data object VerifySeed : NavKey

@Serializable
data object RestoreSeed : NavKey

@Serializable
data object SetPin : NavKey

@Serializable
data object Lock : NavKey

@Serializable
data object Home : NavKey

fun LaunchState.startRoute(): NavKey =
    when (this) {
        LaunchState.ONBOARDING -> Welcome
        LaunchState.LOCKED -> Lock
    }
