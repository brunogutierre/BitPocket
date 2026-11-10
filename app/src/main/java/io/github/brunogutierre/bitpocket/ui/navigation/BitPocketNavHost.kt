package io.github.brunogutierre.bitpocket.ui.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.brunogutierre.bitpocket.core.health.HealthCheck
import io.github.brunogutierre.bitpocket.core.session.LaunchState
import io.github.brunogutierre.bitpocket.di.AppContainer
import io.github.brunogutierre.bitpocket.health.HealthViewModel
import io.github.brunogutierre.bitpocket.health.healthReport
import io.github.brunogutierre.bitpocket.history.TransactionDetailsViewModel
import io.github.brunogutierre.bitpocket.home.HomeViewModel
import io.github.brunogutierre.bitpocket.lock.LockViewModel
import io.github.brunogutierre.bitpocket.onboarding.CreateSeedViewModel
import io.github.brunogutierre.bitpocket.onboarding.RestoreSeedViewModel
import io.github.brunogutierre.bitpocket.onboarding.SetPinViewModel
import io.github.brunogutierre.bitpocket.onboarding.VerifySeedViewModel
import io.github.brunogutierre.bitpocket.onboarding.WelcomeViewModel
import io.github.brunogutierre.bitpocket.receive.ReceiveViewModel
import io.github.brunogutierre.bitpocket.send.SendViewModel
import io.github.brunogutierre.bitpocket.settings.ChangePinViewModel
import io.github.brunogutierre.bitpocket.settings.CoinsViewModel
import io.github.brunogutierre.bitpocket.settings.DuressViewModel
import io.github.brunogutierre.bitpocket.settings.GuardrailsViewModel
import io.github.brunogutierre.bitpocket.settings.ServerViewModel
import io.github.brunogutierre.bitpocket.settings.SettingsViewModel
import io.github.brunogutierre.bitpocket.ui.components.SecureScreen
import io.github.brunogutierre.bitpocket.ui.health.HealthScreen
import io.github.brunogutierre.bitpocket.ui.history.TransactionDetailsScreen
import io.github.brunogutierre.bitpocket.ui.home.HomeScreen
import io.github.brunogutierre.bitpocket.ui.lock.LockScreen
import io.github.brunogutierre.bitpocket.ui.onboarding.CreateSeedScreen
import io.github.brunogutierre.bitpocket.ui.onboarding.RestoreSeedScreen
import io.github.brunogutierre.bitpocket.ui.onboarding.SetPinScreen
import io.github.brunogutierre.bitpocket.ui.onboarding.VerifySeedScreen
import io.github.brunogutierre.bitpocket.ui.onboarding.WelcomeScreen
import io.github.brunogutierre.bitpocket.ui.receive.ReceiveScreen
import io.github.brunogutierre.bitpocket.ui.send.SendScreen
import io.github.brunogutierre.bitpocket.ui.settings.ChangePinScreen
import io.github.brunogutierre.bitpocket.ui.settings.CoinsScreen
import io.github.brunogutierre.bitpocket.ui.settings.DuressScreen
import io.github.brunogutierre.bitpocket.ui.settings.GuardrailsScreen
import io.github.brunogutierre.bitpocket.ui.settings.ServerScreen
import io.github.brunogutierre.bitpocket.ui.settings.SettingsScreen
import io.github.brunogutierre.bitpocket.ui.vault.HeirClaimScreen
import io.github.brunogutierre.bitpocket.ui.vault.VaultScreen
import io.github.brunogutierre.bitpocket.ui.vault.VaultSetupScreen
import io.github.brunogutierre.bitpocket.vault.HeirClaimViewModel
import io.github.brunogutierre.bitpocket.vault.VaultSetupViewModel
import io.github.brunogutierre.bitpocket.vault.VaultViewModel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter

private const val NAV_DURATION_MS = 300

/** Shared-axis X: forward slides in from the end, back slides in from the start. */
private fun sharedAxisX(forward: Boolean): ContentTransform {
    val direction = if (forward) 1 else -1
    return (slideInHorizontally(tween(NAV_DURATION_MS)) { direction * it / 10 } + fadeIn(tween(NAV_DURATION_MS)))
        .togetherWith(slideOutHorizontally(tween(NAV_DURATION_MS)) { -direction * it / 10 } + fadeOut(tween(NAV_DURATION_MS)))
}

/** App navigation (Navigation 3). Each entry gets its own saveable state and ViewModelStore. */
@Composable
fun BitPocketNavHost(
    container: AppContainer,
    startRoute: NavKey,
) {
    val backStack = rememberNavBackStack(startRoute)
    val back: () -> Unit = { backStack.removeLastOrNull() }
    val resetTo: (NavKey) -> Unit = { route ->
        backStack.clear()
        backStack.add(route)
    }
    // Whenever the session locks (auto-lock, Lock, erase), leave the screens that need it.
    LaunchedEffect(container.session) {
        container.session.walletState.drop(1).filter { it == null }.collect {
            val current = backStack.lastOrNull() ?: return@collect
            val draft = container.onboardingDraft
            val walletExists = container.launchState() == LaunchState.LOCKED
            val allowed = guardRoute(current, walletExists, false, draft.words.isNotEmpty(), draft.entropy != null)
            if (allowed != current) resetTo(allowed)
        }
    }
    val top = backStack.lastOrNull()
    LaunchedEffect(top) {
        if (top == null) return@LaunchedEffect
        val draft = container.onboardingDraft
        val allowed =
            guardRoute(
                route = top,
                walletExists = container.launchState() == LaunchState.LOCKED,
                unlocked = container.session.isUnlocked,
                draftWords = draft.words.isNotEmpty(),
                draftEntropy = draft.entropy != null,
            )
        if (allowed != top) resetTo(allowed)
    }
    NavDisplay(
        backStack = backStack,
        onBack = back,
        entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
        transitionSpec = { sharedAxisX(forward = true) },
        popTransitionSpec = { sharedAxisX(forward = false) },
        predictivePopTransitionSpec = { sharedAxisX(forward = false) },
        entryProvider =
            entryProvider {
                entry<Welcome> {
                    WelcomeScreen(
                        viewModel { WelcomeViewModel(container.onboardingDraft, container.mnemonicService) },
                        onShowNewSeed = { backStack.add(CreateSeed) },
                        onRestore = { backStack.add(RestoreSeed) },
                        onClaimInheritance = { backStack.add(ClaimInheritance) },
                    )
                }
                entry<CreateSeed> {
                    SecureScreen {
                        CreateSeedScreen(
                            viewModel { CreateSeedViewModel(container.onboardingDraft) },
                            onBack = back,
                            onWrittenDown = { backStack.add(VerifySeed) },
                        )
                    }
                }
                entry<VerifySeed> {
                    SecureScreen {
                        VerifySeedScreen(
                            viewModel { VerifySeedViewModel(container.onboardingDraft, container.mnemonicService) },
                            onBack = back,
                            onVerified = { backStack.add(SetPin) },
                        )
                    }
                }
                entry<RestoreSeed> {
                    SecureScreen {
                        RestoreSeedScreen(
                            viewModel { RestoreSeedViewModel(container.onboardingDraft, container.mnemonicService) },
                            onBack = back,
                            onRestored = { backStack.add(SetPin) },
                        )
                    }
                }
                entry<SetPin> {
                    SetPinScreen(
                        viewModel { SetPinViewModel(container.onboardingDraft, container.walletAccess) },
                        onBack = back,
                        onCreated = { resetTo(Home) },
                    )
                }
                entry<Lock> {
                    LockScreen(viewModel { LockViewModel(container.walletAccess) }, onUnlocked = { resetTo(Home) })
                }
                entry<Home> {
                    // Null only for a moment after process death: the route guard then shows Lock.
                    val wallet = container.session.wallet
                    if (wallet != null) {
                        HomeScreen(
                            viewModel {
                                HomeViewModel(
                                    wallet.spending,
                                    container.session::lock,
                                    vault = { container.session.wallet?.vault },
                                    labels = { container.labels()?.transactionLabels().orEmpty() },
                                    health = { healthReport(container.session) },
                                )
                            },
                            onReceive = { backStack.add(Receive()) },
                            onSend = { backStack.add(Send()) },
                            onVault = { backStack.add(VaultHome) },
                            onSetUpVault = { backStack.add(VaultSetup(importing = false)) },
                            onHealth = { backStack.add(Health) },
                            onSettings = { backStack.add(Settings) },
                            onTransaction = { txid -> backStack.add(TransactionDetails(txid)) },
                            onLocked = { resetTo(Lock) },
                        )
                    }
                }
                entry<Receive> { route ->
                    val wallet = container.session.wallet
                    val pocket = if (route.vault) wallet?.vault else wallet?.spending
                    if (pocket != null) ReceiveScreen(viewModel { ReceiveViewModel(pocket) }, onBack = back)
                }
                entry<VaultHome> {
                    val wallet = container.session.wallet
                    val vault = wallet?.vault
                    val config = wallet?.settings?.vault
                    if (vault != null && config != null) {
                        VaultScreen(
                            viewModel { VaultViewModel(vault, config) },
                            onBack = back,
                            onReceive = { backStack.add(Receive(vault = true)) },
                            onMoveIn = { backStack.add(Send(toVault = true)) },
                            onSend = { backStack.add(Send(fromVault = true)) },
                            onRefresh = { backStack.add(Send(refreshVault = true)) },
                            onCoins = { backStack.add(Coins(vault = true)) },
                            onTransaction = { txid -> backStack.add(TransactionDetails(txid, vault = true)) },
                        )
                    }
                }
                entry<Send> { route ->
                    val wallet = container.session.wallet
                    val fromVault = route.fromVault || route.refreshVault
                    val pocket = if (fromVault) wallet?.vault else wallet?.spending
                    if (wallet != null && pocket != null) {
                        SendScreen(
                            viewModel {
                                val prefill: (suspend () -> String?)? =
                                    if (route.toVault) {
                                        (
                                            {
                                                container.session.wallet
                                                    ?.vault
                                                    ?.nextReceiveAddress()
                                                    ?.address
                                            }
                                        )
                                    } else {
                                        null
                                    }
                                val recoveryKey = if (route.fromVault) wallet.settings.vault?.recoveryKey else null
                                SendViewModel(pocket, container.walletAccess, wallet.biometricSeed, wallet.settings, prefill, recoveryKey)
                            },
                            biometricOpener = container.biometricSeedOpener,
                            onBack = back,
                            onDone = back,
                        )
                    }
                }
                entry<Settings> {
                    SettingsScreen(
                        viewModel {
                            SettingsViewModel(
                                container.walletAccess,
                                container.session,
                                container.biometricAvailable(),
                                container::wipeAll,
                                container::labels,
                            )
                        },
                        seedCipher = container.seedCipher,
                        onBack = back,
                        onChangePin = { backStack.add(ChangePin) },
                        onGuardrails = { backStack.add(GuardrailsSettings) },
                        onDuress = { backStack.add(DuressSettings) },
                        onServer = { backStack.add(ServerSettings) },
                        onCoins = { backStack.add(Coins()) },
                        onSetUpVault = { backStack.add(VaultSetup(importing = false)) },
                        onImportVault = { backStack.add(VaultSetup(importing = true)) },
                        onClaimInheritance = { backStack.add(ClaimInheritance) },
                        vaultBackup = container::vaultBackup,
                        onLock = {
                            container.session.lock()
                            resetTo(Lock)
                        },
                        onWiped = { resetTo(Welcome) },
                    )
                }
                entry<GuardrailsSettings> {
                    val wallet = container.session.wallet
                    if (wallet != null) {
                        GuardrailsScreen(
                            viewModel { GuardrailsViewModel(container.walletAccess, container.session, wallet.spending) },
                            onBack = back,
                        )
                    }
                }
                entry<VaultSetup> { route ->
                    VaultSetupScreen(
                        viewModel { VaultSetupViewModel(container.walletAccess, container.mnemonicService, container::keepVaultBackup) },
                        importing = route.importing,
                        onClose = back,
                    )
                }
                entry<ClaimInheritance> {
                    HeirClaimScreen(viewModel { HeirClaimViewModel(container::openHeirClaim) }, onClose = back)
                }
                entry<DuressSettings> {
                    DuressScreen(
                        viewModel { DuressViewModel(container.walletAccess, container.session, container.mnemonicService) },
                        onClose = back,
                    )
                }
                entry<ServerSettings> {
                    ServerScreen(
                        viewModel { ServerViewModel(container.walletAccess, container.session, container::checkServer) },
                        onBack = back,
                    )
                }
                entry<Coins> { route ->
                    val wallet = container.session.wallet
                    val pocket = if (route.vault) wallet?.vault else wallet?.spending
                    if (pocket != null) CoinsScreen(viewModel { CoinsViewModel(pocket) }, onBack = back)
                }
                entry<Health> {
                    HealthScreen(viewModel { HealthViewModel(container.session) }, onBack = back) { check ->
                        backStack.add(
                            when (check) {
                                HealthCheck.SPENDING_LIMIT -> GuardrailsSettings
                                HealthCheck.VAULT -> VaultSetup(importing = false)
                                HealthCheck.VAULT_HEARTBEAT -> VaultHome
                                HealthCheck.OWN_SERVER -> ServerSettings
                                HealthCheck.DURESS_PIN -> DuressSettings
                                HealthCheck.BIOMETRIC_SIGNING, HealthCheck.QUICK_AUTO_LOCK -> Settings
                            },
                        )
                    }
                }
                entry<ChangePin> {
                    ChangePinScreen(viewModel { ChangePinViewModel(container.walletAccess) }, onBack = back, onChanged = back)
                }
                entry<TransactionDetails> { route ->
                    val wallet = container.session.wallet
                    val pocket = if (route.vault) wallet?.vault else wallet?.spending
                    if (pocket !=
                        null
                    ) {
                        TransactionDetailsScreen(
                            viewModel { TransactionDetailsViewModel(pocket, route.txid, container.labels()) },
                            onBack = back,
                        )
                    }
                }
            },
    )
}
