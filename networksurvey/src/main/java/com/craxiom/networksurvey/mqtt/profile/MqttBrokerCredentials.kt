package com.craxiom.networksurvey.mqtt.profile

import android.content.Context
import com.craxiom.networksurvey.util.CredentialSecureStorage

/**
 * Stores each broker profile's username and password. Production code encrypts them with the
 * Android Keystore ([SecureMqttBrokerCredentials]); tests use an in-memory map because Robolectric
 * has no Keystore.
 */
interface MqttBrokerCredentials {
    /** @return The stored login for profile [id]; empty strings when none is stored. */
    fun get(id: String): MqttBrokerLogin

    /** Stores the login for profile [id], replacing any earlier one. Writes synchronously. */
    fun store(id: String, login: MqttBrokerLogin)

    /** Deletes the login for profile [id]. */
    fun remove(id: String)
}

/** [MqttBrokerCredentials] backed by [CredentialSecureStorage]. */
class SecureMqttBrokerCredentials(context: Context) : MqttBrokerCredentials {
    private val appContext = context.applicationContext

    override fun get(id: String): MqttBrokerLogin = MqttBrokerLogin(
        CredentialSecureStorage.getBrokerUsername(appContext, id).orEmpty(),
        CredentialSecureStorage.getBrokerPassword(appContext, id).orEmpty(),
    )

    override fun store(id: String, login: MqttBrokerLogin) =
        CredentialSecureStorage.storeBrokerCredentials(appContext, id, login.username, login.password)

    override fun remove(id: String) = CredentialSecureStorage.removeBrokerCredentials(appContext, id)
}
