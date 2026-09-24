package io.github.brunogutierre.bitpocket.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.brunogutierre.bitpocket.R
import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import io.github.brunogutierre.bitpocket.ui.theme.BitPocketTheme
import io.github.brunogutierre.bitpocket.ui.theme.Spacing

/** Teal "test money" pill: the network name, announced as having no value. */
@Composable
fun NetworkBadge(
    network: SupportedNetwork,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.network_badge_description, network.displayName)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape)
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                .clearAndSetSemantics { contentDescription = description },
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_science),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = network.displayName.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

/** Top app bar with the network badge and the 2 dp tertiary bottom border marking test money. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BitPocketTopBar(
    title: String,
    network: SupportedNetwork,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        CenterAlignedTopAppBar(
            title = { Text(title, style = MaterialTheme.typography.titleLarge) },
            actions = { NetworkBadge(network, Modifier.padding(end = Spacing.lg)) },
        )
        Box(Modifier.fillMaxWidth().height(2.dp).background(MaterialTheme.colorScheme.tertiary))
    }
}

@Preview(showBackground = true)
@Composable
private fun TopBarPreview() {
    BitPocketTheme {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            BitPocketTopBar("Spending", SupportedNetwork.SIGNET)
            NetworkBadge(SupportedNetwork.TESTNET4, Modifier.padding(Spacing.lg))
        }
    }
}
