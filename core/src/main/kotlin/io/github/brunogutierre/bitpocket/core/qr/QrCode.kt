package io.github.brunogutierre.bitpocket.core.qr

import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder

/**
 * A square QR code as modules (`true` = dark), without the quiet zone: the UI draws its own
 * 4-module margin. Kept free of Android types so the UI only paints squares.
 */
class QrMatrix internal constructor(
    val size: Int,
    private val modules: BooleanArray,
) {
    init {
        require(modules.size == size * size) { "QR matrix must be square" }
    }

    operator fun get(
        x: Int,
        y: Int,
    ): Boolean = modules[y * size + x]
}

object QrCode {
    /** Modules of light margin a scanner needs around the code (ISO/IEC 18004). */
    const val QUIET_ZONE = 4

    /**
     * Encodes [text] with error correction level M (15% damage), enough for screens. Uppercase
     * `BITCOIN:TB1...` URIs fit the denser alphanumeric mode.
     */
    fun encode(text: String): QrMatrix {
        val matrix = Encoder.encode(text, ErrorCorrectionLevel.M, mapOf(EncodeHintType.CHARACTER_SET to "UTF-8")).matrix
        val size = matrix.width
        return QrMatrix(size, BooleanArray(size * size) { matrix.get(it % size, it / size).toInt() == 1 })
    }
}
