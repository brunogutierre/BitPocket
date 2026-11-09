package io.github.brunogutierre.bitpocket.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import io.github.brunogutierre.bitpocket.R
import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork

/** What a PIN prompt says below its title: the first failure that applies, else [body]. */
data class PinPromptStatus(
    val lockoutSeconds: Long = 0,
    val failed: Boolean = false,
    val wrongPin: Boolean = false,
)

/** A page asking for the PIN again (sign, settings), with the Lock screen's messages and lockout. */
@Composable
fun PinPrompt(
    title: String,
    body: String,
    status: PinPromptStatus,
    entered: Int,
    enabled: Boolean,
    errorSignal: Int,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onBack: () -> Unit,
    network: SupportedNetwork? = null,
) {
    BackHandler(onBack = onBack)
    PageScaffold(title = title, onBack = onBack, network = network) {
        val message =
            when {
                status.lockoutSeconds > 0 -> stringResource(R.string.lock_locked_out, formatCountdown(status.lockoutSeconds))
                status.failed -> stringResource(R.string.lock_failed)
                status.wrongPin -> stringResource(R.string.lock_wrong)
                else -> body
            }
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        )
        PinPad(
            enteredCount = entered,
            onDigit = onDigit,
            onBackspace = onBackspace,
            enabled = enabled,
            errorSignal = errorSignal,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

/** `75` -> `"1:15"`. */
fun formatCountdown(seconds: Long) = "%d:%02d".format(seconds / 60, seconds % 60)
