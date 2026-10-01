package io.github.brunogutierre.bitpocket.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.brunogutierre.bitpocket.R
import io.github.brunogutierre.bitpocket.lock.LockViewModel
import io.github.brunogutierre.bitpocket.ui.components.PinPad
import io.github.brunogutierre.bitpocket.ui.theme.Spacing
import io.github.brunogutierre.bitpocket.ui.theme.screenHorizontalPadding

@Composable
fun LockScreen(
    viewModel: LockViewModel,
    onUnlocked: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.unlocked.collect { onUnlocked() } }
    Scaffold { innerPadding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xl, Alignment.CenterVertically),
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = screenHorizontalPadding())
                    .verticalScroll(rememberScrollState()),
        ) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(stringResource(R.string.lock_title), style = MaterialTheme.typography.titleMedium)
            val message =
                when {
                    state.lockoutSeconds > 0 -> stringResource(R.string.lock_locked_out, formatCountdown(state.lockoutSeconds))
                    state.failed -> stringResource(R.string.lock_failed)
                    state.wrongPin -> stringResource(R.string.lock_wrong)
                    else -> ""
                }
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            PinPad(
                enteredCount = state.entered,
                onDigit = viewModel::digit,
                onBackspace = viewModel::backspace,
                enabled = state.keysEnabled,
                errorSignal = state.errorSignal,
            )
        }
    }
}

private fun formatCountdown(seconds: Long) = "%d:%02d".format(seconds / 60, seconds % 60)
