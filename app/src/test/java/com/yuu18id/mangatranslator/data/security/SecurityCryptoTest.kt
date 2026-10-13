package com.yuu18id.mangatranslator.data.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityCryptoTest {

    @Test
    fun testLegacyPlaintextReturnsUnmodified() {
        val legacyKey = "sk-proj-1234567890abcdef"
        val decrypted = SecurityCrypto.decrypt(legacyKey)
        assertEquals("Legacy plaintext without prefix must be returned unchanged", legacyKey, decrypted)
    }

    @Test
    fun testEmptyAndBlankStringsReturnEmpty() {
        assertEquals("", SecurityCrypto.encrypt(""))
        assertEquals("", SecurityCrypto.encrypt("   "))
        assertEquals("", SecurityCrypto.decrypt(""))
        assertEquals("", SecurityCrypto.decrypt("   "))

        val detailedEmpty = SecurityCrypto.encryptDetailed("")
        assertEquals("", detailedEmpty.result)
        assertTrue("Blank input should be marked valid without error", detailedEmpty.isEncrypted)
    }

    @Test
    fun testMalformedEncryptedPayloadReturnsEmpty() {
        val malformedPayload = "enc:v1:short"
        val result = SecurityCrypto.decrypt(malformedPayload)
        assertEquals("Malformed payload should return empty string to prevent sending invalid ciphertext", "", result)
    }

    @Test
    fun testIsEncryptedHelper() {
        assertTrue(SecurityCrypto.isEncrypted("enc:v1:testpayload"))
        assertFalse(SecurityCrypto.isEncrypted("plain-key-12345"))
        assertFalse(SecurityCrypto.isEncrypted(""))
        assertFalse(SecurityCrypto.isEncrypted("enc:v2:testpayload"))
    }

    @Test
    fun testEncryptDetailedFallbackInJvmEnvironment() {
        // In local JVM environment without AndroidKeyStore, encryptDetailed falls back to plainText with error logged
        val rawSecret = "my-secret-api-key"
        val result = SecurityCrypto.encryptDetailed(rawSecret)
        assertEquals(rawSecret, result.result)
        assertFalse("KeyStore is absent in local JVM tests, so isEncrypted must be false", result.isEncrypted)
        assertNotNull("Underlying error should be captured in result", result.error)
    }
}
