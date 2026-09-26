package io.github.brunogutierre.bitpocket.ui.navigation

import androidx.navigation3.runtime.NavKey

/**
 * Where a (possibly restored) route may be shown. After process death the back stack is
 * restored but memory is not: the onboarding draft and the unlocked session are gone.
 */
fun guardRoute(
    route: NavKey,
    walletExists: Boolean,
    unlocked: Boolean,
    draftWords: Boolean,
    draftEntropy: Boolean,
): NavKey {
    val home = if (walletExists) Lock else Welcome
    return when (route) {
        Home -> if (unlocked) Home else home
        Lock -> if (walletExists) Lock else Welcome
        Welcome, RestoreSeed -> if (walletExists) Lock else route
        CreateSeed, VerifySeed -> if (!walletExists && draftWords) route else home
        SetPin -> if (!walletExists && draftEntropy) route else home
        else -> route
    }
}
