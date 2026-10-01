package io.github.brunogutierre.bitpocket.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.brunogutierre.bitpocket.R
import io.github.brunogutierre.bitpocket.home.HomeViewModel
import io.github.brunogutierre.bitpocket.ui.components.BitPocketTopBar
import io.github.brunogutierre.bitpocket.ui.components.SecondaryButton
import io.github.brunogutierre.bitpocket.ui.theme.Spacing
import io.github.brunogutierre.bitpocket.ui.theme.screenHorizontalPadding

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onLocked: () -> Unit,
) {
    Scaffold(topBar = { BitPocketTopBar(stringResource(R.string.home_title), viewModel.network) }) { innerPadding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = screenHorizontalPadding(), vertical = Spacing.xl),
        ) {
            Text(
                stringResource(R.string.home_placeholder),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SecondaryButton(stringResource(R.string.home_lock), onClick = {
                viewModel.lock()
                onLocked()
            })
        }
    }
}
