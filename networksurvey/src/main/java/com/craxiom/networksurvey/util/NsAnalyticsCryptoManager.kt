package com.craxiom.networksurvey.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import timber.log.Timber
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Crypto manager for NS Analytics that handles AES encryption/decryption using Android Keystore.
 */
class NsAnalyticsCryptoManager {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_ALIAS = "ns_analytics_master_key"
        private const val GCM_TAG_LENGTH = 128
        private const val KEY_SIZE = 256

        // Separator for storing IV with encrypted data
        private const val SEPARATOR = ":"
    }

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply {
            load(null)
        }
    }

    /**
     * Get or create the encryption key
     */
    private fun getOrCreateKey(): SecretKey {
        val existingKey = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        return existingKey?.secretKey ?: createKey()
    }

    /**
     * Create a new AES key in the Android Keystore
     */
    private fun createKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE)
            .setRandomizedEncryptionRequired(true)

        keyGenerator.init(builder.build())
        return keyGenerator.generateKey()
    }

    /**
     * Encrypt a string value
     * Returns the encrypted data with IV in format: base64(iv):base64(ciphertext)
     */
    fun encrypt(plainText: String?): String? {
        if (plainText == null) return null

        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())

            val iv = cipher.iv
            val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            // Combine IV and ciphertext with separator
            val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            val cipherBase64 = Base64.encodeToString(cipherText, Base64.NO_WRAP)

            "$ivBase64$SEPARATOR$cipherBase64"
        } catch (e: Exception) {
            Timber.e(e, "Failed to encrypt data")
            null
        }
    }

    /**
     * Decrypt a string value
     * Expects format: base64(iv):base64(ciphertext)
     */
    fun decrypt(encryptedData: String?): String? {
        if (encryptedData == null) return null

        return try {
            val parts = encryptedData.split(SEPARATOR)
            if (parts.size != 2) {
                Timber.w("Invalid encrypted data format")
                return null
            }

            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val cipherText = Base64.decode(parts[1], Base64.NO_WRAP)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), spec)

            val plainText = cipher.doFinal(cipherText)
            String(plainText, Charsets.UTF_8)
        } catch (e: Exception) {
            Timber.e(e, "Failed to decrypt data")
            null
        }
    }
}