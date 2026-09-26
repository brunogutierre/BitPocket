package io.github.brunogutierre.bitpocket.ui.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.brunogutierre.bitpocket.R
import io.github.brunogutierre.bitpocket.di.AppContainer
import io.github.brunogutierre.bitpocket.onboarding.CreateSeedViewModel
import io.github.brunogutierre.bitpocket.onboarding.RestoreSeedViewModel
import io.github.brunogutierre.bitpocket.onboarding.VerifySeedViewModel
import io.github.brunogutierre.bitpocket.onboarding.WelcomeViewModel
import io.github.brunogutierre.bitpocket.ui.onboarding.CreateSeedScreen
import io.github.brunogutierre.bitpocket.ui.onboarding.RestoreSeedScreen
import io.github.brunogutierre.bitpocket.ui.onboarding.VerifySeedScreen
import io.github.brunogutierre.bitpocket.ui.onboarding.WelcomeScreen
import io.github.brunogutierre.bitpocket.ui.placeholder.PlaceholderScreen

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
                    )
                }
                entry<CreateSeed> {
                    CreateSeedScreen(
                        viewModel { CreateSeedViewModel(container.onboardingDraft) },
                        onBack = back,
                        onWrittenDown = { backStack.add(VerifySeed) },
                    )
                }
                entry<VerifySeed> {
                    VerifySeedScreen(
                        viewModel { VerifySeedViewModel(container.onboardingDraft, container.mnemonicService) },
                        onBack = back,
                        onVerified = { backStack.add(SetPin) },
                    )
                }
                entry<RestoreSeed> {
                    RestoreSeedScreen(
                        viewModel { RestoreSeedViewModel(container.onboardingDraft, container.mnemonicService) },
                        onBack = back,
                        onRestored = { backStack.add(SetPin) },
                    )
                }
                entry<SetPin> { PlaceholderScreen(stringResource(R.string.route_set_pin)) }
                entry<Lock> { PlaceholderScreen(stringResource(R.string.route_lock)) }
                entry<Home> { PlaceholderScreen(stringResource(R.string.route_home)) }
            },
    )
}
