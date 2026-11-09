package io.github.brunogutierre.bitpocket.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.brunogutierre.bitpocket.core.crypto.wipe
import io.github.brunogutierre.bitpocket.core.seed.GeneratedMnemonic
import io.github.brunogutierre.bitpocket.core.seed.MnemonicService
import io.github.brunogutierre.bitpocket.core.seed.WordChallenge
import io.github.brunogutierre.bitpocket.core.session.SettingsOutcome
import io.github.brunogutierre.bitpocket.core.session.VaultSetupOutcome
import io.github.brunogutierre.bitpocket.core.session.WalletAccess
import io.github.brunogutierre.bitpocket.core.vault.HeirTimelock
import io.github.brunogutierre.bitpocket.security.LockoutCountdown
import io.github.brunogutierre.bitpocket.security.PinBuffer
import io.github.brunogutierre.bitpocket.ui.components.PickResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

enum class VaultSetupStage { INTRO, TIMELOCK, WORDS, VERIFY, PIN, SAVING, BACKUP, FAILED }

/** Whose words are being shown or checked. */
enum class VaultKeyStep { RECOVERY, HEIR }

/** Why setup or import stopped on [VaultSetupStage.FAILED]. */
enum class VaultSetupError { ALREADY_SET_UP, NOT_THIS_WALLET, STORAGE }

data class VaultSetupUiState(
    val stage: VaultSetupStage = VaultSetupStage.INTRO,
    val importing: Boolean = false,
    val timelock: HeirTimelock = HeirTimelock.YEAR,
    val keyStep: VaultKeyStep = VaultKeyStep.RECOVERY,
    val words: List<String> = emptyList(),
    val revealed: Boolean = false,
    val challenge: WordChallenge? = null,
    val challengeStep: Int = 0,
    val challengeCount: Int = 0,
    val selected: String? = null,
    val pickResult: PickResult = PickResult.NONE,
    val pickErrorSignal: Int = 0,
    val pinEntered: Int = 0,
    val wrongPin: Boolean = false,
    val pinErrorSignal: Int = 0,
    val lockoutSeconds: Long = 0,
    val error: VaultSetupError? = null,
    val backupExported: Boolean = false,
) {
    val pinKeysEnabled: Boolean get() = stage == VaultSetupStage.PIN && lockoutSeconds == 0L
}

/**
 * Vault setup wizard (ADR 0007): risks, heir timelock, recovery words (shown once, then checked),
 * heir words (same), PIN, then the encrypted backup to export. Also imports a backup for a
 * restored wallet (straight to the PIN). Every seed and PIN copy is wiped when no longer needed.
 */
class VaultSetupViewModel(
    private val walletAccess: WalletAccess,
    private val mnemonics: MnemonicService,
    /** Keeps the backup next to the wallet's databases, for exporting it again later. */
    private val keepBackup: (ByteArray) -> Unit,
    private val random: Random = Random.Default,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val _state = MutableStateFlow(VaultSetupUiState())
    val state: StateFlow<VaultSetupUiState> = _state.asStateFlow()
    private val pin = PinBuffer()
    private val countdown = LockoutCountdown(viewModelScope) { seconds -> _state.update { it.copy(lockoutSeconds = seconds) } }
    private var recovery: GeneratedMnemonic? = null
    private var heir: GeneratedMnemonic? = null
    private var challenges: List<WordChallenge> = emptyList()
    private var importBytes: ByteArray? = null

    /** The encrypted backup, once created or imported, for the screen to write out. */
    var backup: ByteArray? = null
        private set

    fun startImport(bytes: ByteArray) {
        importBytes = bytes
        _state.update { it.copy(importing = true, stage = VaultSetupStage.PIN) }
    }

    fun next() =
        when (_state.value.stage) {
            VaultSetupStage.INTRO -> _state.update { it.copy(stage = VaultSetupStage.TIMELOCK) }
            VaultSetupStage.TIMELOCK -> showWords(VaultKeyStep.RECOVERY)
            VaultSetupStage.WORDS -> startVerify()
            else -> Unit
        }

    fun chooseTimelock(timelock: HeirTimelock) = _state.update { it.copy(timelock = timelock) }

    fun reveal() = _state.update { it.copy(revealed = true) }

    fun pick(word: String) {
        val current = _state.value
        val challenge = current.challenge ?: return
        if (word != challenge.answer) {
            _state.update { it.copy(selected = word, pickResult = PickResult.WRONG, pickErrorSignal = it.pickErrorSignal + 1) }
            return
        }
        val nextStep = current.challengeStep + 1
        when {
            nextStep < challenges.size -> {
                _state.update {
                    it.copy(
                        challenge = challenges[nextStep],
                        challengeStep = nextStep,
                        selected = null,
                        pickResult = PickResult.NONE,
                    )
                }
            }

            current.keyStep == VaultKeyStep.RECOVERY -> {
                showWords(VaultKeyStep.HEIR)
            }

            else -> {
                _state.update { it.copy(stage = VaultSetupStage.PIN, words = emptyList(), challenge = null) }
            }
        }
    }

    fun pinDigit(digit: Char) {
        if (!_state.value.pinKeysEnabled) return
        pin.append(digit)
        _state.update { it.copy(pinEntered = pin.size, wrongPin = false) }
        if (pin.isFull) save()
    }

    fun pinBackspace() {
        pin.removeLast()
        _state.update { it.copy(pinEntered = pin.size) }
    }

    fun onBackupExported() = _state.update { it.copy(backupExported = true) }

    private fun showWords(step: VaultKeyStep) {
        val generated = mnemonics.create()
        if (step == VaultKeyStep.RECOVERY) recovery = generated else heir = generated
        _state.update {
            it.copy(stage = VaultSetupStage.WORDS, keyStep = step, words = generated.words, revealed = false, challenge = null)
        }
    }

    private fun startVerify() {
        challenges = mnemonics.challenges(_state.value.words, random)
        _state.update {
            it.copy(
                stage = VaultSetupStage.VERIFY,
                challenge = challenges.first(),
                challengeStep = 0,
                challengeCount = challenges.size,
                selected = null,
                pickResult = PickResult.NONE,
            )
        }
    }

    private fun save() {
        val attempt = pin.toCharArray()
        pin.clear()
        _state.update { it.copy(stage = VaultSetupStage.SAVING) }
        viewModelScope.launch {
            val outcome =
                try {
                    withContext(workDispatcher) { run(attempt) }
                } finally {
                    attempt.wipe()
                }
            when (outcome) {
                is VaultSetupOutcome.Done -> {
                    backup = outcome.backup
                    withContext(workDispatcher) { keepBackup(outcome.backup) }
                    wipeSeeds()
                    _state.update { it.copy(stage = VaultSetupStage.BACKUP, pinEntered = 0) }
                }

                is VaultSetupOutcome.Denied -> {
                    denied(outcome.outcome)
                }

                VaultSetupOutcome.AlreadySetUp -> {
                    fail(VaultSetupError.ALREADY_SET_UP)
                }

                VaultSetupOutcome.NotThisWallet -> {
                    fail(VaultSetupError.NOT_THIS_WALLET)
                }
            }
        }
    }

    private fun run(attempt: CharArray): VaultSetupOutcome {
        importBytes?.let { return walletAccess.importVault(attempt, it) }
        val recoverySeed = checkNotNull(recovery).entropy
        val heirSeed = checkNotNull(heir).entropy
        return walletAccess.setUpVault(attempt, recoverySeed, heirSeed, _state.value.timelock)
    }

    private fun denied(outcome: SettingsOutcome) {
        _state.update { it.copy(stage = VaultSetupStage.PIN, pinEntered = 0) }
        when (outcome) {
            is SettingsOutcome.WrongPin -> {
                _state.update { it.copy(wrongPin = true, pinErrorSignal = it.pinErrorSignal + 1) }
                countdown.start(outcome.lockout)
            }

            is SettingsOutcome.LockedOut -> {
                countdown.start(outcome.remaining)
            }

            else -> {
                fail(VaultSetupError.STORAGE)
            }
        }
    }

    private fun fail(error: VaultSetupError) {
        wipeSeeds()
        _state.update { it.copy(stage = VaultSetupStage.FAILED, error = error) }
    }

    private fun wipeSeeds() {
        recovery?.entropy?.wipe()
        heir?.entropy?.wipe()
        recovery = null
        heir = null
    }

    override fun onCleared() {
        pin.clear()
        wipeSeeds()
    }
}
