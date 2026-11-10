package io.github.brunogutierre.bitpocket.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.brunogutierre.bitpocket.core.crypto.wipe
import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import io.github.brunogutierre.bitpocket.core.seed.MnemonicService
import io.github.brunogutierre.bitpocket.core.seed.RestoreResult
import io.github.brunogutierre.bitpocket.core.send.RecipientParser
import io.github.brunogutierre.bitpocket.core.send.RecipientResult
import io.github.brunogutierre.bitpocket.core.send.SpendException
import io.github.brunogutierre.bitpocket.core.send.SpendPlan
import io.github.brunogutierre.bitpocket.core.vault.HeirClaimOpen
import io.github.brunogutierre.bitpocket.core.vault.VaultConfig
import io.github.brunogutierre.bitpocket.core.wallet.ChainUnavailableException
import io.github.brunogutierre.bitpocket.core.wallet.HeirCoin
import io.github.brunogutierre.bitpocket.core.wallet.VaultPocket
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class HeirClaimStage { PICK_FILE, WORDS, SCANNING, COINS, REVIEW, SENDING, DONE }

enum class HeirClaimError { INVALID_BACKUP, NOT_THE_HEIR, BAD_WORDS, OFFLINE, BAD_ADDRESS, NOTHING_MATURED, FAILED }

data class HeirClaimUiState(
    val stage: HeirClaimStage = HeirClaimStage.PICK_FILE,
    val wordsText: String = "",
    val network: SupportedNetwork? = null,
    val coins: List<HeirCoin> = emptyList(),
    val addressText: String = "",
    val plan: SpendPlan? = null,
    val txid: String? = null,
    val error: HeirClaimError? = null,
) {
    val maturedSats: Long get() = coins.filter { it.matured }.sumOf { it.sats }
}

/**
 * The heir's side of the Vault (ADR 0008), with no wallet session: pick the backup file, type the
 * heir words, scan the Vault, then sweep every matured coin to an address of the heir's choice.
 * [open] is `HeirClaim.open` with the app's chain clients.
 */
class HeirClaimViewModel(
    private val open: (backup: ByteArray, heirSeed: ByteArray) -> HeirClaimOpen,
    private val mnemonics: MnemonicService = MnemonicService(),
    private val workDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _state = MutableStateFlow(HeirClaimUiState())
    val state: StateFlow<HeirClaimUiState> = _state.asStateFlow()
    private var backup: ByteArray? = null
    private var heirSeed: ByteArray? = null
    private var vault: VaultPocket? = null
    private var config: VaultConfig? = null

    fun onBackupPicked(bytes: ByteArray) {
        backup = bytes
        _state.update { it.copy(stage = HeirClaimStage.WORDS, error = null) }
    }

    fun onWordsChange(text: String) = _state.update { it.copy(wordsText = text, error = null) }

    /** Checks the words, opens the backup with them and scans the Vault. */
    fun submitWords() {
        val file = backup ?: return
        val words =
            _state.value.wordsText
                .trim()
                .lowercase()
                .split(Regex("\\s+"))
        val seed = (mnemonics.restore(words) as? RestoreResult.Valid)?.entropy ?: return fail(HeirClaimError.BAD_WORDS)
        _state.update { it.copy(stage = HeirClaimStage.SCANNING, wordsText = "", error = null) }
        viewModelScope.launch {
            when (val opened = withContext(workDispatcher) { open(file, seed) }) {
                is HeirClaimOpen.Opened -> {
                    heirSeed = seed
                    vault = opened.vault
                    config = opened.config
                    _state.update { it.copy(network = opened.vault.network) }
                    scan()
                }

                HeirClaimOpen.InvalidBackup -> {
                    seed.wipe()
                    fail(HeirClaimError.INVALID_BACKUP, HeirClaimStage.PICK_FILE)
                }

                HeirClaimOpen.NotTheHeir -> {
                    seed.wipe()
                    fail(HeirClaimError.NOT_THE_HEIR, HeirClaimStage.WORDS)
                }
            }
        }
    }

    /** Full scan of the Vault (first sync of an in-memory wallet), then the coin list. */
    fun scan() {
        val wallet = vault ?: return
        _state.update { it.copy(stage = HeirClaimStage.SCANNING, error = null) }
        viewModelScope.launch {
            try {
                wallet.sync()
                _state.update { it.copy(stage = HeirClaimStage.COINS, coins = wallet.heirCoins()) }
            } catch (e: ChainUnavailableException) {
                fail(HeirClaimError.OFFLINE, HeirClaimStage.COINS)
            }
        }
    }

    fun onAddressChange(text: String) = _state.update { it.copy(addressText = text, error = null) }

    fun review() {
        val wallet = vault ?: return
        val network = wallet.network
        val address =
            (RecipientParser.parse(_state.value.addressText, network) as? RecipientResult.Valid)?.recipient?.address
                ?: return fail(HeirClaimError.BAD_ADDRESS)
        viewModelScope.launch {
            try {
                val rate = wallet.feeRates().normal
                val plan = wallet.planHeirClaim(address, rate)
                _state.update { it.copy(stage = HeirClaimStage.REVIEW, plan = plan) }
            } catch (e: SpendException) {
                fail(HeirClaimError.NOTHING_MATURED)
            } catch (e: ChainUnavailableException) {
                fail(HeirClaimError.OFFLINE)
            }
        }
    }

    fun backToCoins() = _state.update { it.copy(stage = HeirClaimStage.COINS, plan = null) }

    fun claim() {
        val wallet = vault ?: return
        val plan = _state.value.plan ?: return
        val seed = heirSeed ?: return
        _state.update { it.copy(stage = HeirClaimStage.SENDING) }
        viewModelScope.launch {
            try {
                val txid = wallet.sendHeirClaim(plan, seed)
                wipeSeed()
                _state.update { it.copy(stage = HeirClaimStage.DONE, txid = txid) }
            } catch (e: SpendException) {
                fail(HeirClaimError.FAILED, HeirClaimStage.REVIEW)
            } catch (e: ChainUnavailableException) {
                fail(HeirClaimError.OFFLINE, HeirClaimStage.REVIEW)
            }
        }
    }

    private fun fail(
        error: HeirClaimError,
        stage: HeirClaimStage = _state.value.stage,
    ) = _state.update { it.copy(error = error, stage = stage) }

    private fun wipeSeed() {
        heirSeed?.wipe()
        heirSeed = null
    }

    override fun onCleared() {
        wipeSeed()
        vault?.close()
    }
}
