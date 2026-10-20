package com.craxiom.networksurvey.mqtt.profile

import com.craxiom.mqttlibrary.MqttConstants

/**
 * One saved MQTT broker: where to connect and how. The username and password are kept apart, in
 * encrypted storage ([MqttBrokerCredentials]), and the client ID and device status setting belong
 * to the phone, not to a broker.
 *
 * @property revision Goes up on every save, so that a change to only the credentials still
 *                    changes the stored profile list and reaches the service's preference listener.
 */
data class MqttBrokerProfile(
    val id: String,
    val name: String,
    val host: String,
    val port: Int = MqttConstants.DEFAULT_MQTT_PORT,
    val tlsEnabled: Boolean = MqttConstants.DEFAULT_MQTT_TLS_SETTING,
    val topicPrefix: String = MqttConstants.DEFAULT_MQTT_TOPIC_PREFIX,
    val qos: Int = MqttConstants.DEFAULT_MQTT_QOS.value,
    val origin: Origin = Origin.USER,
    val revision: Long = 0,
) {
    /** Where a profile came from. */
    enum class Origin {
        /** Added by the person using the phone (typed in, scanned, or migrated from the old screen). */
        USER,

        /**
         * A copy of the organization's broker, made when the user chose to use their own settings so
         * that the stream keeps going. The organization may set its password, so it is never shared.
         */
        ORGANIZATION_COPY,
    }

    companion object {
        /** The lowest valid TCP port. */
        const val MIN_PORT = 1

        /** The highest valid TCP port. */
        const val MAX_PORT = 65535

        /** @return True if [port] is a valid TCP port. */
        @JvmStatic
        fun isValidPort(port: Int): Boolean = port in MIN_PORT..MAX_PORT

        /** @return True if [prefix] has no MQTT wildcard, which the broker can't publish to. */
        @JvmStatic
        fun isValidTopicPrefix(prefix: String): Boolean = '#' !in prefix && '+' !in prefix
    }
}

/** A broker's username and password, read from encrypted storage. Either may be empty. */
data class MqttBrokerLogin(val username: String, val password: String)
