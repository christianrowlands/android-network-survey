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
 * General-purpose crypto manager for securing app credentials using Android Keystore.
 */
class CryptoManager {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_ALIAS = "ns_app_master_key"
        private const val GCM_TAG_LENGTH = 128
        private const val KEY_SIZE = 256

        // Separator for storing IV with encrypted data
        private const val SEPARATOR = ":"
    }

private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
    .apply {
        load(null, null) // Load the keystore with default parameters
        val existingKey = getEntry(KEY_ALIAS, "") as? KeyStore.SecretKeyEntry
        if (existingKey != null) {
            return@apply existingKey.secretKey
        }
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE)
            .setRandomizedEncryptionRequired(true)
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(builder.build())
        return@apply keyGenerator.generateKey()
    }
