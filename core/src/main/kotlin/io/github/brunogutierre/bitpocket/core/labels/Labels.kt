package io.github.brunogutierre.bitpocket.core.labels

import io.github.brunogutierre.bitpocket.core.crypto.writeAtomically
import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import org.bitcoindevkit.Address
import java.io.File

/** One BIP329 record: [type] is `tx`, `addr`, `output`, …; [ref] a txid, address or `txid:vout`. */
data class Label(
    val type: String,
    val ref: String,
    val label: String?,
    /** Only for `output` records: false means "do not spend" (BitPocket freezes the coin). */
    val spendable: Boolean? = null,
)

/** What an import did: [added] records, [skipped] invalid lines, [frozen] outputs marked unspendable. */
data class LabelImport(
    val added: Int,
    val skipped: Int,
    val frozen: List<String>,
)

/**
 * BIP329 wallet labels, kept as one JSON Lines file per wallet (`wallets/<id>/labels.jsonl`):
 * the storage format is the export format (ADR 0011). Records of types BitPocket does not use are
 * kept as they are, so a round trip through another wallet loses nothing.
 */
class LabelStore(
    private val file: File,
) {
    private val records: LinkedHashMap<Pair<String, String>, Map<String, Any?>> = load()

    fun label(
        type: String,
        ref: String,
    ): String? = records[type to ref]?.get("label") as? String

    /** Labels of every transaction, by txid. */
    fun transactionLabels(): Map<String, String> =
        records.entries
            .mapNotNull { (key, value) ->
                (value["label"] as? String)?.let { label -> key.second.takeIf { key.first == TX }?.to(label) }
            }.toMap()

    /** Sets (or removes, when blank) the label of a record, then saves the file. */
    fun setLabel(
        type: String,
        ref: String,
        label: String?,
    ) {
        val key = type to ref
        val text = label?.trim().orEmpty()
        val current = records[key].orEmpty()
        if (text.isEmpty()) {
            val rest = current - "label"
            if (rest.keys.all { it == "type" || it == "ref" }) records.remove(key) else records[key] = rest
        } else {
            records[key] = linkedMapOf<String, Any?>("type" to type, "ref" to ref) + current + ("label" to text)
        }
        save()
    }

    fun export(): String = records.values.joinToString("\n", postfix = if (records.isEmpty()) "" else "\n") { FlatJson.write(it) }

    /**
     * Merges a BIP329 export. Lines that are not valid records, and addresses of another network,
     * are skipped; imported labels replace existing ones. Returns the outputs to freeze.
     */
    fun import(
        jsonl: String,
        network: SupportedNetwork,
    ): LabelImport {
        var added = 0
        var skipped = 0
        val frozen = mutableListOf<String>()
        jsonl.lineSequence().filter { it.isNotBlank() }.forEach { line ->
            val record = runCatching { FlatJson.parse(line) }.getOrNull()
            val type = record?.get("type") as? String
            val ref = record?.get("ref") as? String
            if (record == null || type == null || ref == null || (type == ADDR && !isAddressOf(ref, network))) {
                skipped++
                return@forEach
            }
            records[type to ref] = record
            added++
            if (type == OUTPUT && record["spendable"] == false) frozen += ref
        }
        save()
        return LabelImport(added, skipped, frozen)
    }

    private fun isAddressOf(
        address: String,
        network: SupportedNetwork,
    ): Boolean = runCatching { Address(address, network.toBdkNetwork()).close() }.isSuccess

    private fun load(): LinkedHashMap<Pair<String, String>, Map<String, Any?>> {
        val loaded = linkedMapOf<Pair<String, String>, Map<String, Any?>>()
        if (!file.isFile) return loaded
        file.readLines().filter { it.isNotBlank() }.forEach { line ->
            val record = runCatching { FlatJson.parse(line) }.getOrNull() ?: return@forEach
            val type = record["type"] as? String ?: return@forEach
            val ref = record["ref"] as? String ?: return@forEach
            loaded[type to ref] = record
        }
        return loaded
    }

    private fun save() {
        file.parentFile?.mkdirs()
        writeAtomically(file, export().toByteArray(Charsets.UTF_8))
    }

    companion object {
        const val TX = "tx"
        const val ADDR = "addr"
        const val OUTPUT = "output"
    }
}
