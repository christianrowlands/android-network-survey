package com.craxiom.networksurvey.mqtt

import com.craxiom.mqttlibrary.MqttConstants
import com.craxiom.mqttlibrary.MqttQos
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.survey.mdm.RestrictionsSnapshot
import timber.log.Timber

/**
 * Reads the MQTT broker an organization provides through the managed configuration. This is the
 * only place the MDM broker keys are interpreted, so the service, the survey setup policy and the
 * screens always agree on whether there is an organization broker.
 */
object OrganizationBroker {

    /** The longest device name accepted from the managed configuration. */
    private const val MAX_DEVICE_NAME_LENGTH = 100

    /**
     * @return True if the managed configuration carries a usable broker: a host and a client ID.
     */
    @JvmStatic
    fun isPresent(snapshot: RestrictionsSnapshot): Boolean =
        !snapshot.getString(MqttConstants.PROPERTY_MQTT_CONNECTION_HOST).isNullOrBlank() &&
            !snapshot.getString(MqttConstants.PROPERTY_MQTT_CLIENT_ID).isNullOrBlank()

    /**
     * @return True if the organization sets the broker password, in which case no copy of its broker
     * may be shared as a QR code.
     */
    @JvmStatic
    fun setsPassword(snapshot: RestrictionsSnapshot): Boolean =
        snapshot.contains(MqttConstants.PROPERTY_MQTT_PASSWORD)

    /**
     * @return The organization's broker, or null if the managed configuration does not provide a
     * usable one. The record types it streams come from the survey, not from here.
     */
    @JvmStatic
    fun from(snapshot: RestrictionsSnapshot): MqttConnectionInfo? {
        if (!isPresent(snapshot)) return null

        val deviceName = snapshot.getString(NetworkSurveyConstants.PROPERTY_MDM_MQTT_DEVICE_NAME)?.let {
            if (it.length > MAX_DEVICE_NAME_LENGTH) {
                Timber.w("MDM device name was truncated to %d characters", MAX_DEVICE_NAME_LENGTH)
                it.substring(0, MAX_DEVICE_NAME_LENGTH)
            } else {
                it
            }
        }

        return MqttConnectionInfo(
            snapshot.getString(MqttConstants.PROPERTY_MQTT_CONNECTION_HOST),
            snapshot.getInt(MqttConstants.PROPERTY_MQTT_CONNECTION_PORT) ?: MqttConstants.DEFAULT_MQTT_PORT,
            snapshot.getBoolean(MqttConstants.PROPERTY_MQTT_CONNECTION_TLS_ENABLED) ?: MqttConstants.DEFAULT_MQTT_TLS_SETTING,
            snapshot.getString(MqttConstants.PROPERTY_MQTT_CLIENT_ID),
            snapshot.getString(MqttConstants.PROPERTY_MQTT_USERNAME),
            snapshot.getString(MqttConstants.PROPERTY_MQTT_PASSWORD),
            false, false, false, false,
            snapshot.getBoolean(NetworkSurveyConstants.PROPERTY_MQTT_DEVICE_STATUS_STREAM_ENABLED)
                ?: NetworkSurveyConstants.DEFAULT_MQTT_DEVICE_STATUS_STREAM_SETTING,
            false,
            snapshot.getString(MqttConstants.PROPERTY_MQTT_TOPIC_PREFIX) ?: MqttConstants.DEFAULT_MQTT_TOPIC_PREFIX,
            deviceName,
            qosFrom(snapshot.getInt(MqttConstants.PROPERTY_MQTT_QOS)),
        )
    }

    /** @return The QoS for [value], or the default when it is absent or not a valid level. */
    @JvmStatic
    fun qosFrom(value: Int?): MqttQos {
        if (value == null) return MqttConstants.DEFAULT_MQTT_QOS
        return try {
            MqttQos.fromValue(value)
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "Invalid MQTT QoS value %d, using the default", value)
            MqttConstants.DEFAULT_MQTT_QOS
        }
    }
}
