package io.github.brunogutierre.bitpocket.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.brunogutierre.bitpocket.R
import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import io.github.brunogutierre.bitpocket.onboarding.WelcomeAction
import io.github.brunogutierre.bitpocket.onboarding.WelcomeEvent
import io.github.brunogutierre.bitpocket.onboarding.WelcomeViewModel
import io.github.brunogutierre.bitpocket.ui.components.NetworkBadge
import io.github.brunogutierre.bitpocket.ui.components.PrimaryButton
import io.github.brunogutierre.bitpocket.ui.components.SecondaryButton
import io.github.brunogutierre.bitpocket.ui.components.WarningBanner
import io.github.brunogutierre.bitpocket.ui.theme.Spacing

@Composable
fun WelcomeScreen(
    viewModel: WelcomeViewModel,
    onShowNewSeed: () -> Unit,
    onRestore: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { if (it == WelcomeEvent.SHOW_NEW_SEED) onShowNewSeed() else onRestore() }
    }
    OnboardingScaffold(
        title = stringResource(R.string.app_name),
        onBack = null,
        bottomActions = {
            PrimaryButton(stringResource(R.string.welcome_create), onClick = { viewModel.onAction(WelcomeAction.Create) })
            SecondaryButton(stringResource(R.string.welcome_restore), onClick = { viewModel.onAction(WelcomeAction.Restore) })
        },
    ) {
        Text(stringResource(R.string.tagline), style = MaterialTheme.typography.headlineSmall)
        WarningBanner(stringResource(R.string.warning_title), stringResource(R.string.warning_not_audited))
        TextButton(onClick = { viewModel.onAction(WelcomeAction.ToggleAdvanced) }) {
            Text(stringResource(R.string.welcome_advanced))
        }
        if (state.advancedExpanded) {
            Text(stringResource(R.string.welcome_network_title), style = MaterialTheme.typography.titleMedium)
            Column(Modifier.selectableGroup()) {
                SupportedNetwork.entries.forEach { network ->
                    NetworkOption(network, selected = network == state.network) {
                        viewModel.onAction(WelcomeAction.SelectNetwork(network))
                    }
                }
            }
        }
    }
}

@Composable
private fun NetworkOption(
    network: SupportedNetwork,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = Spacing.minTouchTarget)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
    ) {
        RadioButton(selected = selected, onClick = null)
        val label =
            if (network ==
                SupportedNetwork.DEFAULT
            ) {
                stringResource(R.string.welcome_network_default, network.displayName)
            } else {
                network.displayName
            }
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        NetworkBadge(network)
    }
}
