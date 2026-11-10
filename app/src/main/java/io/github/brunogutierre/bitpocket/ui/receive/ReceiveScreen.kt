package io.github.brunogutierre.bitpocket.ui.receive

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.brunogutierre.bitpocket.R
import io.github.brunogutierre.bitpocket.core.format.BitcoinFormat
import io.github.brunogutierre.bitpocket.core.qr.QrCode
import io.github.brunogutierre.bitpocket.core.qr.QrMatrix
import io.github.brunogutierre.bitpocket.receive.ReceiveViewModel
import io.github.brunogutierre.bitpocket.ui.components.AddressFingerprintView
import io.github.brunogutierre.bitpocket.ui.components.PageScaffold
import io.github.brunogutierre.bitpocket.ui.components.PrimaryButton
import io.github.brunogutierre.bitpocket.ui.components.SecondaryButton
import io.github.brunogutierre.bitpocket.ui.theme.monoTypography

@Composable
fun ReceiveScreen(
    viewModel: ReceiveViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val address = state.address?.address
    PageScaffold(
        title = stringResource(R.string.receive_title),
        onBack = onBack,
        network = viewModel.network,
        bottomActions = {
            PrimaryButton(
                stringResource(R.string.receive_copy),
                onClick = { address?.let { copy(context, it) } },
                enabled = address != null,
            )
            SecondaryButton(
                stringResource(R.string.receive_share),
                onClick = { viewModel.shareText()?.let { share(context, it) } },
                enabled = address != null,
            )
        },
    ) {
        state.qr?.let { QrImage(it, Modifier.align(Alignment.CenterHorizontally)) }
        if (address != null) {
            Text(
                BitcoinFormat.addressChunks(address).joinToString(" "),
                style = MaterialTheme.monoTypography.address,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            AddressFingerprintView(address, Modifier.align(Alignment.CenterHorizontally))
            Text(
                stringResource(R.string.receive_fingerprint_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(
            stringResource(R.string.receive_network_hint, viewModel.network.displayName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = viewModel::newAddress, enabled = !state.atUnusedLimit) {
                Text(stringResource(R.string.receive_new_address))
            }
            if (state.atUnusedLimit) {
                Text(
                    stringResource(R.string.receive_unused_limit),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Dark modules on white with the quiet zone, whatever the theme: scanners expect that. */
@Composable
private fun QrImage(
    matrix: QrMatrix,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.receive_qr_description)
    Box(
        modifier
            .widthIn(max = 280.dp)
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(Color.White, MaterialTheme.shapes.medium)
            .semantics { contentDescription = description },
    ) {
        Canvas(Modifier.matchParentSize()) {
            val modules = matrix.size + 2 * QrCode.QUIET_ZONE
            val cell = size.minDimension / modules
            for (y in 0 until matrix.size) {
                for (x in 0 until matrix.size) {
                    if (matrix[x, y]) {
                        drawRect(
                            Color.Black,
                            topLeft = Offset((x + QrCode.QUIET_ZONE) * cell, (y + QrCode.QUIET_ZONE) * cell),
                            // A hair larger than the cell, so anti-aliasing leaves no seams between modules.
                            size = Size(cell + 0.5f, cell + 0.5f),
                        )
                    }
                }
            }
        }
    }
}

private fun copy(
    context: Context,
    address: String,
) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    val label = context.getString(R.string.receive_clip_label)
    clipboard.setPrimaryClip(ClipData.newPlainText(label, address))
    // Leave nothing behind for clipboard-reading apps: clear it after a minute if it is still ours.
    Handler(Looper.getMainLooper()).postDelayed({
        if (clipboard.primaryClipDescription?.label == label) clipboard.clearPrimaryClip()
    }, CLIPBOARD_CLEAR_MS)
    // Android 13+ shows its own confirmation for clipboard writes.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, R.string.receive_copied, Toast.LENGTH_SHORT).show()
    }
}

private fun share(
    context: Context,
    text: String,
) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, null))
}

private const val CLIPBOARD_CLEAR_MS = 60_000L
