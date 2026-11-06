package com.craxiom.networksurvey.scout.firmware

import com.google.crypto.tink.subtle.Ed25519Verify
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.util.Base64

/** An Ed25519 public key that may sign NS Scout firmware. */
class FirmwareSigningKey(private val raw: ByteArray) {
    init {
        require(raw.size == Ed25519Verify.PUBLIC_KEY_LEN) { "An Ed25519 public key is 32 bytes" }
    }

    /** @return True when [signature] is this key's Ed25519 signature over [data]. */
    fun verifies(signature: ByteArray, data: ByteArray): Boolean = try {
        Ed25519Verify(raw).verify(signature, data)
        true
    } catch (e: GeneralSecurityException) {
        false
    }

    companion object {
        /** @return The key in [base64] (the raw 32 bytes, as in the firmware repository's .pub files). */
        @JvmStatic
        fun fromBase64(base64: String): FirmwareSigningKey = FirmwareSigningKey(Base64.getDecoder().decode(base64))
    }
}

/** The keys the app accepts for firmware releases (network-survey-esp32 docs/release-manifest.md). */
class FirmwareTrust(private val keys: List<FirmwareSigningKey>) {

    /** @return True when one of the trusted keys made [signature] over [data]. */
    fun isTrusted(data: ByteArray, signature: ByteArray): Boolean {
        if (signature.size != Ed25519Verify.SIGNATURE_LEN) return false
        return keys.any { it.verifies(signature, data) }
    }

    companion object {
        /**
         * The release signing key (base64 of the raw 32 bytes). The firmware repository has the same
         * public key in scripts/keys/release-signing-key.pub and the iOS app in `FirmwareTrust.releaseKeys`;
         * its private half is the release workflow's signing secret.
         */
        const val RELEASE_KEY_BASE64 = "Ek1bybFsmuhEjknuauGaTa7N2zut111qFfKpziDEBwY="

        /** What the app trusts. */
        @JvmField
        val RELEASE = FirmwareTrust(listOf(FirmwareSigningKey.fromBase64(RELEASE_KEY_BASE64)))

        /**
         * @return A signature from a signature file or manifest field: base64 text (as
         * sign-release.sh writes it) or the raw 64 bytes, or null when it is neither.
         */
        @JvmStatic
        fun decodeSignature(data: ByteArray): ByteArray? {
            // Base64 of 64 bytes is 88 characters, so 64 bytes can only be the raw signature
            if (data.size == Ed25519Verify.SIGNATURE_LEN) return data
            val text = String(data, StandardCharsets.UTF_8).trim()
            val raw = try {
                Base64.getDecoder().decode(text)
            } catch (e: IllegalArgumentException) {
                return null
            }
            return raw.takeIf { it.size == Ed25519Verify.SIGNATURE_LEN }
        }

        /** @return [decodeSignature] of a manifest's base64 text field. */
        @JvmStatic
        fun decodeSignature(text: String): ByteArray? = decodeSignature(text.toByteArray(StandardCharsets.UTF_8))
    }
}
