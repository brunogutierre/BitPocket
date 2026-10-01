package io.github.brunogutierre.bitpocket.ui.onboarding

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.brunogutierre.bitpocket.R
import io.github.brunogutierre.bitpocket.onboarding.CreateSeedViewModel
import io.github.brunogutierre.bitpocket.onboarding.VerifySeedViewModel
import io.github.brunogutierre.bitpocket.ui.components.PrimaryButton
import io.github.brunogutierre.bitpocket.ui.components.SeedWordGrid
import io.github.brunogutierre.bitpocket.ui.components.WarningBanner
import io.github.brunogutierre.bitpocket.ui.components.WordPicker

@Composable
fun CreateSeedScreen(
    viewModel: CreateSeedViewModel,
    onBack: () -> Unit,
    onWrittenDown: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    OnboardingScaffold(
        title = stringResource(R.string.create_title),
        onBack = onBack,
        bottomActions = {
            PrimaryButton(stringResource(R.string.create_done), onClick = onWrittenDown, enabled = state.canContinue)
        },
    ) {
        WarningBanner(stringResource(R.string.create_warning_title), stringResource(R.string.create_warning_body))
        SeedWordGrid(state.words, revealed = state.revealed, onReveal = viewModel::reveal)
    }
}

@Composable
fun VerifySeedScreen(
    viewModel: VerifySeedViewModel,
    onBack: () -> Unit,
    onVerified: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.verified.collect { onVerified() } }
    OnboardingScaffold(title = stringResource(R.string.verify_title), onBack = onBack) {
        Text(
            stringResource(R.string.verify_step, state.step, state.totalSteps),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(stringResource(R.string.verify_prompt, state.challenge.position), style = MaterialTheme.typography.headlineSmall)
        WordPicker(
            options = state.challenge.options,
            selected = state.selected,
            result = state.result,
            onPick = viewModel::pick,
            errorSignal = state.errorSignal,
        )
    }
}
