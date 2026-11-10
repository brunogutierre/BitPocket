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

/** Receive into the Spending pocket, or the Vault when [vault]. */
@Serializable
data class Receive(
    val vault: Boolean = false,
) : NavKey

/** Send from Spending ([toVault] pre-fills a Vault address, "Move to Vault"), or from the Vault. */
@Serializable
data class Send(
    val toVault: Boolean = false,
    val fromVault: Boolean = false,
    /** Vault heartbeat: send every Vault coin to a new Vault address, restarting the heir timelock. */
    val refreshVault: Boolean = false,
) : NavKey

@Serializable
data object VaultHome : NavKey

/** The heir's claim: needs no wallet on this phone. */
@Serializable
data object ClaimInheritance : NavKey

@Serializable
data object Settings : NavKey

@Serializable
data object ChangePin : NavKey

@Serializable
data object GuardrailsSettings : NavKey

@Serializable
data object DuressSettings : NavKey

@Serializable
data object ServerSettings : NavKey

/** Coin control for the Spending pocket, or the Vault when [vault]. */
@Serializable
data class Coins(
    val vault: Boolean = false,
) : NavKey

/** Vault setup wizard, or import of a backup when [importing]. */
@Serializable
data class VaultSetup(
    val importing: Boolean,
) : NavKey

@Serializable
data class TransactionDetails(
    val txid: String,
    val vault: Boolean = false,
) : NavKey

fun LaunchState.startRoute(): NavKey =
    when (this) {
        LaunchState.ONBOARDING -> Welcome
        LaunchState.LOCKED -> Lock
    }
