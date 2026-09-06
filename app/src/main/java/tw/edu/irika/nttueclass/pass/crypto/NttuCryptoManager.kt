package tw.edu.irika.nttueclass.pass.crypto

import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 通行證載荷資料結構
 */
data class NttuPassPayload(
    val studentId: String,
    val token: String,
    val donationInteger: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val is1D: Boolean = false
)

/**
 * 簡易 RFC 5869 HKDF-SHA256 密鑰衍生實作
 */
object HkdfSha256 {
    fun deriveKey(ikm: ByteArray, salt: ByteArray?, info: ByteArray?, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        val effectiveSalt = if (salt == null || salt.isEmpty()) ByteArray(32) else salt
        mac.init(SecretKeySpec(effectiveSalt, "HmacSHA256"))
        val prk = mac.doFinal(ikm)

        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        val okm = ByteArray(length)
        var t = ByteArray(0)
        var offset = 0
        var iteration = 1
        while (offset < length) {
            mac.update(t)
            if (info != null) mac.update(info)
            mac.update(iteration.toByte())
            t = mac.doFinal()
            val toCopy = minOf(t.size, length - offset)
            System.arraycopy(t, 0, okm, offset, toCopy)
            offset += toCopy
            iteration++
        }
        return okm
    }
}

/**
 * NTTU ECC-256 (NIST P-256) ECIES 加解密管理器與 Special Token 規範防護器
 */
object NttuCryptoManager {

    /**
     * 規範定義：嚴格固定 128 字元長度之預設佔位符號 (剛好 128 個 '0')
     */
    const val SPECIAL_TOKEN_PLACEHOLDER: String =
        "00000000000000000000000000000000" +
        "00000000000000000000000000000000" +
        "00000000000000000000000000000000" +
        "00000000000000000000000000000000"

    /**
     * 預設內建 NIST P-256 主公鑰 (Base64 X.509)
     */
    const val DEFAULT_MASTER_PUBLIC_KEY_B64: String =
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE4LXlBIS2XqbXUoXgaAL37/n1Zb2W4KEvURnTMDR5E/gcUvSbgl03aI6Ut6+tGzPhP+znhCU2lfClxLzKF1Yi5w=="

    /**
     * 預設內建 NIST P-256 主私鑰 (Base64 PKCS#8)
     */
    const val DEFAULT_MASTER_PRIVATE_KEY_B64: String =
        "MEECAQAwEwYHKoZIzj0CAQYIKoZIzj0DAQcEJzAlAgEBBCDuIe9JeKjf84F/KBPFZdRLohXNE7zJ4OBLX5/3OJXeUQ=="

    private val masterPublicKey: PublicKey by lazy {
        val bytes = Base64.getDecoder().decode(DEFAULT_MASTER_PUBLIC_KEY_B64)
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(bytes))
    }

    private val masterPrivateKey: PrivateKey by lazy {
        val bytes = Base64.getDecoder().decode(DEFAULT_MASTER_PRIVATE_KEY_B64)
        KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(bytes))
    }

    /**
     * 特殊憑證標準化：未填寫、空字串強制替換為 128 個 '0'，不足 128 補齊，超出截斷
     */
    fun normalizeToken(token: String?): String {
        if (token.isNullOrBlank()) {
            return SPECIAL_TOKEN_PLACEHOLDER
        }
        val trimmed = token.trim()
        return when {
            trimmed.length > 128 -> trimmed.substring(0, 128)
            trimmed.length < 128 -> trimmed.padEnd(128, '0')
            else -> trimmed
        }
    }

    /**
     * 檢查是否為預設佔位符號
     */
    fun isPlaceholder(token: String): Boolean {
        return token == SPECIAL_TOKEN_PLACEHOLDER
    }

    /**
     * 遮罩顯示 Token (前 8 碼與後 8 碼)
     */
    fun maskToken(token: String): String {
        if (isPlaceholder(token)) {
            return "未綁定 (預設佔位符)"
        }
        return if (token.length >= 16) {
            "${token.take(8)}...${token.takeLast(8)}"
        } else {
            token
        }
    }

    /**
     * NttuPass 1D 一維條碼內容生成：{學號}00
     */
    fun generate1DBarcodeContent(studentId: String): String {
        val cleanId = studentId.trim()
        return "${cleanId}00"
    }

    /**
     * NttuDataPass 2D ECC-256 加密二維碼字串生成
     *
     * 流程：
     * 1. 組裝明文：學號:128位Token:贊助整數:時戳
     * 2. 生成臨時金鑰對 (Ephemeral EC KeyPair)
     * 3. 與主公鑰執行 ECDH 生成共用密鑰
     * 4. 經由 HKDF-SHA256 衍生 AES-256 金鑰
     * 5. 以 AES-256-GCM 進行認證加密 (輸出 12-byte IV + 密文 + 16-byte Tag)
     * 6. 打包並編碼為 URL-Safe Base64 字串
     */
    fun encryptPayload(
        studentId: String,
        specialToken: String?,
        donationInteger: Long,
        timestamp: Long = System.currentTimeMillis(),
        pubKey: PublicKey = masterPublicKey
    ): String {
        val safeToken = normalizeToken(specialToken)
        val plaintext = "$studentId:$safeToken:$donationInteger:$timestamp"

        // 1. 生成臨時 EC 金鑰對
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        val ephemeralPair = kpg.generateKeyPair()
        val ephemPubBytes = ephemeralPair.public.encoded

        // 2. ECDH
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(ephemeralPair.private)
        ka.doPhase(pubKey, true)
        val sharedSecret = ka.generateSecret()

        // 3. HKDF 衍生 AES-256 金鑰
        val salt = "NttuEPassSalt".toByteArray(Charsets.UTF_8)
        val info = "NttuEPassAES256GCM".toByteArray(Charsets.UTF_8)
        val aesKeyBytes = HkdfSha256.deriveKey(sharedSecret, salt, info, 32)
        val secretKey = SecretKeySpec(aesKeyBytes, "AES")

        // 4. AES-256-GCM 加密
        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        val cipherWithTag = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        // 5. 封包格式：[ephemPubLen (2 bytes)] + [ephemPubBytes] + [iv (12 bytes)] + [cipherWithTag]
        val buffer = ByteBuffer.allocate(2 + ephemPubBytes.size + iv.size + cipherWithTag.size)
        buffer.putShort(ephemPubBytes.size.toShort())
        buffer.put(ephemPubBytes)
        buffer.put(iv)
        buffer.put(cipherWithTag)

        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.array())
    }

    /**
     * NttuDataPass 2D ECC-256 解密
     */
    fun decryptPayload(
        cipherBase64: String,
        privKey: PrivateKey = masterPrivateKey
    ): Result<NttuPassPayload> = runCatching {
        val rawBytes = try {
            Base64.getUrlDecoder().decode(cipherBase64.trim())
        } catch (e: Exception) {
            Base64.getDecoder().decode(cipherBase64.trim())
        }

        val buffer = ByteBuffer.wrap(rawBytes)
        val pubLen = buffer.short.toInt() and 0xFFFF
        val ephemPubBytes = ByteArray(pubLen)
        buffer.get(ephemPubBytes)

        val ephemPubKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(ephemPubBytes))

        val iv = ByteArray(12)
        buffer.get(iv)

        val cipherWithTag = ByteArray(buffer.remaining())
        buffer.get(cipherWithTag)

        // ECDH 恢復共享密鑰
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(privKey)
        ka.doPhase(ephemPubKey, true)
        val sharedSecret = ka.generateSecret()

        // HKDF
        val salt = "NttuEPassSalt".toByteArray(Charsets.UTF_8)
        val info = "NttuEPassAES256GCM".toByteArray(Charsets.UTF_8)
        val aesKeyBytes = HkdfSha256.deriveKey(sharedSecret, salt, info, 32)
        val secretKey = SecretKeySpec(aesKeyBytes, "AES")

        // AES-256-GCM 解密
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        val decryptedBytes = cipher.doFinal(cipherWithTag)
        val plaintext = String(decryptedBytes, Charsets.UTF_8)

        val parts = plaintext.split(":")
        require(parts.size >= 3) { "無效的通行證載荷結構: $plaintext" }

        val studentId = parts[0]
        val token = parts[1]
        val donationInt = parts[2].toLongOrNull() ?: 0L
        val timestamp = if (parts.size >= 4) parts[3].toLongOrNull() ?: 0L else 0L

        NttuPassPayload(
            studentId = studentId,
            token = token,
            donationInteger = donationInt,
            timestamp = timestamp,
            is1D = false
        )
    }

    /**
     * 智慧解析通行證輸入（相容 1D 校園條碼與 2D 加密條碼）
     */
    fun parseAnyPass(rawInput: String): Result<NttuPassPayload> {
        val trimmed = rawInput.trim()
        // 檢查是否為 1D 條碼 (結尾為 00 且為數字)
        if (trimmed.endsWith("00") && trimmed.length in 8..15 && trimmed.all { it.isDigit() }) {
            val studentId = trimmed.removeSuffix("00")
            return Result.success(
                NttuPassPayload(
                    studentId = studentId,
                    token = SPECIAL_TOKEN_PLACEHOLDER,
                    donationInteger = 0L,
                    timestamp = System.currentTimeMillis(),
                    is1D = true
                )
            )
        }

        // 嘗試 ECC-256 解密
        val decryptResult = decryptPayload(trimmed)
        if (decryptResult.isSuccess) {
            return decryptResult
        }

        // 容錯：如果包含冒號分隔的明文 (測試模式)
        if (trimmed.contains(":")) {
            val parts = trimmed.split(":")
            if (parts.size >= 2) {
                return Result.success(
                    NttuPassPayload(
                        studentId = parts[0],
                        token = normalizeToken(parts.getOrNull(1)),
                        donationInteger = parts.getOrNull(2)?.toLongOrNull() ?: 0L,
                        timestamp = System.currentTimeMillis(),
                        is1D = false
                    )
                )
            }
        }

        return Result.failure(IllegalArgumentException("無法識別的條碼格式或解密驗證失敗"))
    }
}
