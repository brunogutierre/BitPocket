package io.github.brunogutierre.bitpocket.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.brunogutierre.bitpocket.R
import io.github.brunogutierre.bitpocket.ui.theme.BitPocketTheme
import io.github.brunogutierre.bitpocket.ui.theme.Spacing
import io.github.brunogutierre.bitpocket.ui.theme.extendedColors

enum class BannerSeverity { WARNING, ERROR }

/** Inline banner (radius 12) with an icon, a title and a body; read as one element by TalkBack. */
@Composable
fun WarningBanner(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    severity: BannerSeverity = BannerSeverity.WARNING,
) {
    val (container: Color, content: Color, icon: Int) =
        when (severity) {
            BannerSeverity.WARNING -> {
                Triple(MaterialTheme.extendedColors.warningContainer, MaterialTheme.colorScheme.onSurface, R.drawable.ic_warning)
            }

            BannerSeverity.ERROR -> {
                Triple(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, R.drawable.ic_error)
            }
        }
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier =
            modifier
                .fillMaxWidth()
                .background(container, MaterialTheme.shapes.medium)
                .padding(Spacing.lg)
                .semantics(mergeDescendants = true) {},
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = content)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = content)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WarningBannerPreview() {
    BitPocketTheme {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            WarningBanner("Study project", "Runs on Signet/Testnet4 only. Not audited.")
            WarningBanner("Wrong network", "This address is for another network.", severity = BannerSeverity.ERROR)
        }
    }
}
