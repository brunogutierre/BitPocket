package io.github.brunogutierre.bitpocket.core.crypto

import java.io.File

/**
 * [AttemptsStore] in one fixed-size file, encrypted with its own device key (no PIN needed).
 *
 * A missing or unreadable file reads as [AttemptsState.NONE]: someone able to corrupt the file
 * could also delete it, so penalizing corruption would add no protection (see ADR 0005,
 * rollback). Transient key failures ([KeyUnavailableException]) propagate.
 */
class FileAttemptsStore(
    private val file: File,
    private val keyWrapper: KeyWrapper,
) : AttemptsStore {
    override fun read(): AttemptsState {
        val wrapped = file.takeIf { it.isFile }?.readBytes() ?: return AttemptsState.NONE
        return try {
            AttemptsState.decode(keyWrapper.unwrap(wrapped))
        } catch (e: AuthenticationFailedException) {
            AttemptsState.NONE
        } catch (e: IllegalArgumentException) {
            AttemptsState.NONE
        }
    }

    override fun write(state: AttemptsState) = writeAtomically(file, keyWrapper.wrap(state.encode()))
}
