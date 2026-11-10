package io.github.brunogutierre.bitpocket.core.qr

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class QrCodeTest {
    @Test
    fun `round-trips a BIP21 URI through a QR reader`() {
        val uri = "BITCOIN:TB1P8WPT9V4FRPF3TKN0SRD97PKSGSXC5HS52LAFXWRU9KGEEPHVS7RQLQT9ZJ"

        val matrix = QrCode.encode(uri)

        assertEquals(uri, decode(matrix))
    }

    @Test
    fun `uppercase URIs give a smaller code than lowercase ones`() {
        val address = "tb1p8wpt9v4frpf3tkn0srd97pksgsxc5hs52lafxwru9kgeephvs7rqlqt9zj"

        val upper = QrCode.encode("BITCOIN:${address.uppercase()}")
        val lower = QrCode.encode("bitcoin:$address")

        assertTrue(upper.size < lower.size, "${upper.size} vs ${lower.size}")
        // Version n has 17 + 4n modules per side.
        assertEquals(0, (upper.size - 17) % 4)
    }

    @Test
    fun `decodes a camera-like grayscale frame, with row padding`() {
        val uri = "bitcoin:tb1qw508d6qejxtdg4y5r3zarvary0c5xw7kxpjzsx?amount=0.001"
        val matrix = QrCode.encode(uri)
        val scale = 6
        val side = (matrix.size + 2 * QrCode.QUIET_ZONE) * scale
        val stride = side + 16 // camera buffers often pad each row
        val frame =
            ByteArray(stride * side) { i ->
                val x = i % stride / scale - QrCode.QUIET_ZONE
                val y = i / stride / scale - QrCode.QUIET_ZONE
                if (x in 0 until matrix.size && y in 0 until matrix.size && matrix[x, y]) 0 else -1
            }

        assertEquals(uri, QrCode.decode(frame, side, side, rowStride = stride))
    }

    @Test
    fun `a frame without a QR code decodes to null`() {
        assertNull(QrCode.decode(ByteArray(100 * 100) { (it * 31).toByte() }, 100, 100))
    }

    @Test
    fun `rejects a non-square matrix`() {
        assertThrows<IllegalArgumentException> { QrMatrix(3, BooleanArray(8)) }
    }

    /** Renders the matrix (with its quiet zone, 4 px per module) and reads it back with zxing. */
    private fun decode(matrix: QrMatrix): String {
        val scale = 4
        val side = (matrix.size + 2 * QrCode.QUIET_ZONE) * scale
        val pixels =
            IntArray(side * side) { i ->
                val x = i % side / scale - QrCode.QUIET_ZONE
                val y = i / side / scale - QrCode.QUIET_ZONE
                val dark = x in 0 until matrix.size && y in 0 until matrix.size && matrix[x, y]
                if (dark) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            }
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(side, side, pixels)))
        return QRCodeReader().decode(bitmap).text
    }
}
