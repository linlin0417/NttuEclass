package tw.edu.irika.nttueclass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.edu.irika.nttueclass.pass.crypto.DonationData
import tw.edu.irika.nttueclass.pass.crypto.DonationIntegerHelper
import tw.edu.irika.nttueclass.pass.crypto.NttuCryptoManager

class NttuCryptoTest {

    @Test
    fun testSpecialTokenPlaceholderLength() {
        assertEquals("Placeholder must be exactly 128 chars", 128, NttuCryptoManager.SPECIAL_TOKEN_PLACEHOLDER.length)
        assertTrue(NttuCryptoManager.SPECIAL_TOKEN_PLACEHOLDER.all { it == '0' })
    }

    @Test
    fun testNormalizeTokenRules() {
        // Null or blank -> placeholder
        assertEquals(NttuCryptoManager.SPECIAL_TOKEN_PLACEHOLDER, NttuCryptoManager.normalizeToken(null))
        assertEquals(NttuCryptoManager.SPECIAL_TOKEN_PLACEHOLDER, NttuCryptoManager.normalizeToken(""))
        assertEquals(NttuCryptoManager.SPECIAL_TOKEN_PLACEHOLDER, NttuCryptoManager.normalizeToken("   "))

        // Short token -> padded with '0' to 128
        val shortToken = "abc123"
        val normalizedShort = NttuCryptoManager.normalizeToken(shortToken)
        assertEquals(128, normalizedShort.length)
        assertTrue(normalizedShort.startsWith("abc123"))
        assertEquals("abc123" + "0".repeat(122), normalizedShort)

        // Long token (> 128) -> truncated to 128
        val longToken = "x".repeat(200)
        val normalizedLong = NttuCryptoManager.normalizeToken(longToken)
        assertEquals(128, normalizedLong.length)
        assertEquals("x".repeat(128), normalizedLong)

        // Exact 128 chars
        val exact128 = "A".repeat(128)
        val normalizedExact = NttuCryptoManager.normalizeToken(exact128)
        assertEquals(128, normalizedExact.length)
        assertEquals(exact128, normalizedExact)
    }

    @Test
    fun testDonationIntegerEncodingDecoding() {
        val original = DonationData(
            tier = 3,
            coffeeCount = 42,
            dinnerCount = 15,
            isFounder = true,
            isLifetime = true
        )

        val encoded = DonationIntegerHelper.encode(original)
        val decoded = DonationIntegerHelper.decode(encoded)

        assertEquals(3, decoded.tier)
        assertEquals(42, decoded.coffeeCount)
        assertEquals(15, decoded.dinnerCount)
        assertTrue(decoded.isFounder)
        assertTrue(decoded.isLifetime)
        assertEquals("守護者月贊助", decoded.tierName)
        assertTrue(decoded.badgeList.contains("創始支持者"))
        assertTrue(decoded.badgeList.contains("榮譽永久贊助徽章"))
    }

    @Test
    fun testDonationIntegerMaxValues() {
        val maxData = DonationData(
            tier = 3,
            coffeeCount = 65535,
            dinnerCount = 65535,
            isFounder = true,
            isLifetime = false
        )
        val encoded = DonationIntegerHelper.encode(maxData)
        val decoded = DonationIntegerHelper.decode(encoded)

        assertEquals(3, decoded.tier)
        assertEquals(65535, decoded.coffeeCount)
        assertEquals(65535, decoded.dinnerCount)
        assertTrue(decoded.isFounder)
        assertFalse(decoded.isLifetime)
    }

    @Test
    fun testEcc256EncryptionAndDecryptionRoundTrip() {
        val studentId = "11411188"
        val customToken = "a".repeat(128)
        val donationInt = 123456789L
        val timestamp = 1757123456789L

        val cipherBase64 = NttuCryptoManager.encryptPayload(
            studentId = studentId,
            specialToken = customToken,
            donationInteger = donationInt,
            timestamp = timestamp
        )

        assertNotNull(cipherBase64)
        assertTrue(cipherBase64.isNotEmpty())

        val decryptResult = NttuCryptoManager.decryptPayload(cipherBase64)
        assertTrue("Decryption should succeed", decryptResult.isSuccess)

        val payload = decryptResult.getOrThrow()
        assertEquals(studentId, payload.studentId)
        assertEquals(customToken, payload.token)
        assertEquals(donationInt, payload.donationInteger)
        assertEquals(timestamp, payload.timestamp)
        assertFalse(payload.is1D)
    }

    @Test
    fun testEcc256DecryptionWithPlaceholderToken() {
        val studentId = "11411188"
        val cipherBase64 = NttuCryptoManager.encryptPayload(
            studentId = studentId,
            specialToken = null, // empty -> placeholder
            donationInteger = 0L
        )

        val decryptResult = NttuCryptoManager.decryptPayload(cipherBase64)
        assertTrue(decryptResult.isSuccess)

        val payload = decryptResult.getOrThrow()
        assertEquals(studentId, payload.studentId)
        assertTrue(NttuCryptoManager.isPlaceholder(payload.token))
        assertEquals("未綁定 (預設佔位符)", NttuCryptoManager.maskToken(payload.token))
    }

    @Test
    fun testParseAnyPass1DBarcode() {
        val result = NttuCryptoManager.parseAnyPass("1141118800")
        assertTrue(result.isSuccess)

        val payload = result.getOrThrow()
        assertEquals("11411188", payload.studentId)
        assertTrue(payload.is1D)
    }
}
