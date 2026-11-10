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

@Serializable
data object Receive : NavKey

@Serializable
data object Send : NavKey

@Serializable
data object Settings : NavKey

@Serializable
data object ChangePin : NavKey

@Serializable
data object GuardrailsSettings : NavKey

/** Vault setup wizard, or import of a backup when [importing]. */
@Serializable
data class VaultSetup(
    val importing: Boolean,
) : NavKey

@Serializable
data class TransactionDetails(
    val txid: String,
) : NavKey

fun LaunchState.startRoute(): NavKey =
    when (this) {
        LaunchState.ONBOARDING -> Welcome
        LaunchState.LOCKED -> Lock
    }
