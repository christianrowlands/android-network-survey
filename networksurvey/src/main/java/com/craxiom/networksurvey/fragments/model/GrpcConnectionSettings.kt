package com.craxiom.networksurvey.fragments.model

import android.content.SharedPreferences
import com.craxiom.networksurvey.constants.NetworkSurveyConstants

/**
 * Holds the gRPC server connection settings. What is streamed is not part of the settings: a gRPC
 * stream receives every record type the survey records (except CDR, which is files only).
 */
data class GrpcConnectionSettings(
    val host: String?,
    val port: Int?,
    val deviceName: String?,
) {

    class Builder {
        private var host: String? = null
        private var port: Int? = null
        private var deviceName: String? = null

        fun host(host: String) = apply { this.host = host }
        fun port(port: Int) = apply { this.port = port }
        fun deviceName(deviceName: String) = apply { this.deviceName = deviceName }

        fun build(): GrpcConnectionSettings = GrpcConnectionSettings(host, port, deviceName)
    }

    companion object {
        /**
         * @return The gRPC server settings the user saved, or null if no server host is saved (the
         * gRPC destination is not set up).
         */
        @JvmStatic
        fun fromPreferences(preferences: SharedPreferences): GrpcConnectionSettings? {
            val host = preferences.getString(NetworkSurveyConstants.PROPERTY_NETWORK_SURVEY_CONNECTION_HOST, "")
            if (host.isNullOrBlank()) return null
            val port = preferences.getInt(
                NetworkSurveyConstants.PROPERTY_NETWORK_SURVEY_CONNECTION_PORT,
                NetworkSurveyConstants.DEFAULT_GRPC_PORT,
            )
            val deviceName = preferences.getString(NetworkSurveyConstants.PROPERTY_NETWORK_SURVEY_DEVICE_NAME, "") ?: ""
            return GrpcConnectionSettings(host, port, deviceName)
        }
    }
}
