package io.github.brunogutierre.bitpocket.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.brunogutierre.bitpocket.core.health.HealthInputs
import io.github.brunogutierre.bitpocket.core.health.HealthReport
import io.github.brunogutierre.bitpocket.core.health.HealthScore
import io.github.brunogutierre.bitpocket.core.session.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The open wallet's security health, from its session and Vault heartbeat; null when locked. */
suspend fun healthReport(session: Session): HealthReport? {
    val wallet = session.wallet ?: return null
    return HealthScore.evaluate(HealthInputs(wallet.settings, wallet.biometricSeed != null, wallet.vault?.heartbeat()?.status))
}

/** Security health score screen (M7): re-read every time it is shown, since fixes happen elsewhere. */
class HealthViewModel(
    private val session: Session,
) : ViewModel() {
    private val _report = MutableStateFlow<HealthReport?>(null)
    val report: StateFlow<HealthReport?> = _report.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch { _report.value = healthReport(session) }
    }
}
