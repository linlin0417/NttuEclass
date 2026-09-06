package tw.edu.irika.nttueclass.pass.barcode

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * 條碼與 QR Code 點陣圖生成器 (基於 ZXing)
 */
object BarcodeGenerator {

    /**
     * 生成 Code 128 一維條碼 Bitmap
     *
     * @param content 條碼內容，例如：{學號}00
     * @param width 圖片寬度 (像素)
     * @param height 圖片高度 (像素)
     */
    fun generateCode128(
        content: String,
        width: Int = 800,
        height: Int = 240
    ): Result<Bitmap> = runCatching {
        val hints = mapOf(
            EncodeHintType.MARGIN to 10
        )
        val writer = MultiFormatWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.CODE_128, width, height, hints)
        bitMatrixToBitmap(bitMatrix)
    }

    /**
     * 生成 QR Code 二維條碼 Bitmap
     *
     * @param content 二維碼載荷內容 (例如 ECC-256 Base64 密文字串)
     * @param size 正方形邊長 (像素)
     */
    fun generateQrCode(
        content: String,
        size: Int = 512
    ): Result<Bitmap> = runCatching {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 2
        )
        val writer = MultiFormatWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        bitMatrixToBitmap(bitMatrix)
    }

    /**
     * 將 ZXing BitMatrix 轉換為 Android Bitmap
     */
    private fun bitMatrixToBitmap(
        matrix: BitMatrix,
        foregroundColor: Int = Color.BLACK,
        backgroundColor: Int = Color.WHITE
    ): Bitmap {
        val width = matrix.width
        val height = matrix.height
        val pixels = IntArray(width * height)

        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (matrix.get(x, y)) foregroundColor else backgroundColor
            }
        }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }
}
