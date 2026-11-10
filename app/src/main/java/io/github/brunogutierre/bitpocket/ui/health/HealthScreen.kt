package io.github.brunogutierre.bitpocket.ui.health

import androidx.compose.foundation.clickable
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.brunogutierre.bitpocket.R
import io.github.brunogutierre.bitpocket.core.health.HealthCheck
import io.github.brunogutierre.bitpocket.health.HealthViewModel
import io.github.brunogutierre.bitpocket.ui.components.PageScaffold
import io.github.brunogutierre.bitpocket.ui.theme.extendedColors
import io.github.brunogutierre.bitpocket.ui.theme.monoTypography

@Composable
fun HealthScreen(
    viewModel: HealthViewModel,
    onBack: () -> Unit,
    onFix: (HealthCheck) -> Unit,
) {
    val report by viewModel.report.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.reload() }
    PageScaffold(title = stringResource(R.string.health_title), onBack = onBack) {
        val current = report ?: return@PageScaffold
        Text(stringResource(R.string.health_grade, current.grade.name, current.score), style = MaterialTheme.monoTypography.balance)
        Text(stringResource(R.string.health_body), style = MaterialTheme.typography.bodyMedium)
        HealthCheck.entries.forEach { check ->
            val ok = check in current.passed
            ListItem(
                leadingContent = {
                    Text(if (ok) "✓" else "✗", color = if (ok) MaterialTheme.extendedColors.success else MaterialTheme.colorScheme.error)
                },
                headlineContent = { Text(stringResource(check.title())) },
                supportingContent = { Text(stringResource(if (ok) R.string.health_ok else R.string.health_fix, check.weight)) },
                modifier = if (ok) Modifier else Modifier.clickable { onFix(check) },
                colors =
                    androidx.compose.material3.ListItemDefaults.colors(
                        headlineColor = if (ok) Color.Unspecified else MaterialTheme.colorScheme.onSurface,
                    ),
            )
        }
    }
}

fun HealthCheck.title(): Int =
    when (this) {
        HealthCheck.BIOMETRIC_SIGNING -> R.string.health_biometric
        HealthCheck.QUICK_AUTO_LOCK -> R.string.health_auto_lock
        HealthCheck.SPENDING_LIMIT -> R.string.health_limit
        HealthCheck.VAULT -> R.string.health_vault
        HealthCheck.VAULT_HEARTBEAT -> R.string.health_heartbeat
        HealthCheck.OWN_SERVER -> R.string.health_server
        HealthCheck.DURESS_PIN -> R.string.health_duress
    }
