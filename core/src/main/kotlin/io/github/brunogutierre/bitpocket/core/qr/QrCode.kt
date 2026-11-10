package io.github.brunogutierre.bitpocket.core.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
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

    /**
     * Reads a QR code from a grayscale frame (e.g. the camera's Y plane, [rowStride] bytes per
     * row), or returns null when there is none or it cannot be read.
     */
    fun decode(
        luminance: ByteArray,
        width: Int,
        height: Int,
        rowStride: Int = width,
    ): String? {
        val source = PlanarYUVLuminanceSource(luminance, rowStride, height, 0, 0, width, height, false)
        val hints = mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE))
        // A new reader per frame: zxing readers keep state and are not thread-safe.
        return try {
            QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)), hints).text
        } catch (e: ReaderException) {
            null
        }
    }
}
