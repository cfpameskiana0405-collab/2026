package com.ayzen.scan.logic

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

enum class GenFormat(val zxing: BarcodeFormat) {
    QR(BarcodeFormat.QR_CODE),
    CODE128(BarcodeFormat.CODE_128)
}

object QrGenerator {

    fun generate(
        content: String,
        format: GenFormat = GenFormat.QR,
        fg: Int = Color.BLACK,
        bg: Int = Color.WHITE,
        size: Int = 1024
    ): Bitmap? {
        return try {
            val hints = HashMap<EncodeHintType, Any>()
            hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
            if (format == GenFormat.QR) {
                hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.M
                hints[EncodeHintType.MARGIN] = 3
            } else {
                hints[EncodeHintType.MARGIN] = 24
            }
            val width = size
            val height = if (format == GenFormat.QR) size else size / 3
            val matrix = MultiFormatWriter().encode(content, format.zxing, width, height, hints)
            val w = matrix.width
            val h = matrix.height
            val pixels = IntArray(w * h)
            for (y in 0 until h) {
                val offset = y * w
                for (x in 0 until w) {
                    pixels[offset + x] = if (matrix.get(x, y)) fg else bg
                }
            }
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                it.setPixels(pixels, 0, w, 0, 0, w, h)
            }
        } catch (e: Exception) {
            null
        }
    }
}
