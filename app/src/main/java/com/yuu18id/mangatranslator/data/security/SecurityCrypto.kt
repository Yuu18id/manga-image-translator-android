package com.yuu18id.mangatranslator.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Hardware-backed AES-256 GCM encryption helper using Android KeyStore.
 * Encrypts sensitive secrets (such as API keys) at rest in DataStore.
 * Fully backward-compatible with unencrypted legacy keys.
 */
object SecurityCrypto {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "manga_translator_keystore_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128
    private const val ENCRYPTED_PREFIX = "enc:v1:"

    /**
     * Result container for encryption operation to distinguish successful hardware encryption
     * from unencrypted fallback states.
     */
    data class EncryptionResult(
        val result: String,
        val isEncrypted: Boolean,
        val error: Throwable? = null
    )

    @Synchronized
    private fun getOrCreateSecretKey(forceRecreate: Boolean = false): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!forceRecreate) {
            try {
                val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
                if (existing != null) return existing
            } catch (e: Throwable) {
                android.util.Log.w("SecurityCrypto", "Existing key invalid or corrupted, recreating: ${e.message}")
                try { keyStore.deleteEntry(KEY_ALIAS) } catch (_: Throwable) {}
            }
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    /**
     * Checks if the value is an encrypted payload produced by SecurityCrypto.
     */
    fun isEncrypted(value: String): Boolean {
        return value.startsWith(ENCRYPTED_PREFIX)
    }

    /**
     * Encrypts plaintext string with detailed execution result.
     * If KeyStore encryption succeeds, returns EncryptionResult with isEncrypted = true.
     * If KeyStore fails, returns the plaintext with isEncrypted = false and the root cause error.
     */
    fun encryptDetailed(plainText: String): EncryptionResult {
        if (plainText.isBlank()) return EncryptionResult("", isEncrypted = true)
        return try {
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + cipherText.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)
            val encrypted = ENCRYPTED_PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
            EncryptionResult(result = encrypted, isEncrypted = true)
        } catch (e: Throwable) {
            android.util.Log.e("SecurityCrypto", "Failed to encrypt secret via AndroidKeyStore: ${e.message}", e)
            EncryptionResult(result = plainText, isEncrypted = false, error = e)
        }
    }

    /**
     * Encrypts plaintext string using AES/GCM/NoPadding with a randomized IV.
     * Returns "enc:v1:<base64(iv + ciphertext)>".
     * If KeyStore is unavailable (e.g. running in mock JVM test environment), falls back to plainText.
     */
    fun encrypt(plainText: String): String {
        return encryptDetailed(plainText).result
    }

    /**
     * Decrypts an encrypted string.
     * If the string does not have the "enc:v1:" prefix, returns it as-is for backward compatibility.
     * If decryption of an encrypted payload fails, returns an empty string to prevent sending
     * encrypted ciphertext strings ("enc:v1:...") as bearer tokens to remote API endpoints.
     */
    fun decrypt(encryptedText: String): String {
        if (encryptedText.isBlank()) return ""
        if (!encryptedText.startsWith(ENCRYPTED_PREFIX)) {
            return encryptedText
        }
        return try {
            val base64Payload = encryptedText.removePrefix(ENCRYPTED_PREFIX)
            val combined = Base64.decode(base64Payload, Base64.NO_WRAP)
            if (combined.size <= GCM_IV_LENGTH) {
                android.util.Log.w("SecurityCrypto", "Encrypted payload is malformed (too short)")
                return ""
            }

            val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
            val cipherText = combined.copyOfRange(GCM_IV_LENGTH, combined.size)

            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            val plainBytes = cipher.doFinal(cipherText)
            String(plainBytes, Charsets.UTF_8)
        } catch (e: Throwable) {
            android.util.Log.e("SecurityCrypto", "Failed to decrypt secret payload: ${e.message}", e)
            // Return empty string instead of leaking encrypted ciphertext as raw token
            ""
        }
    }
}
