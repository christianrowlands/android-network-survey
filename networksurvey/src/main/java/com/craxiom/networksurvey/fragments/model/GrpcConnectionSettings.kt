package com.craxiom.networksurvey.fragments.model

import android.content.SharedPreferences
import androidx.core.content.edit
import com.craxiom.networksurvey.constants.NetworkSurveyConstants

/**
 * Holds the gRPC server connection settings. What is streamed is not part of the settings: a gRPC
 * stream receives every record type the survey records (except CDR, which is files only).
 *
 * @property deviceName The name set on every message sent to the server; empty sends no name.
 */
data class GrpcConnectionSettings(
    val host: String,
    val port: Int,
    val deviceName: String,
) {

    /** "host:port", with an IPv6 literal in brackets so the port stays readable. */
    val displayAddress: String get() = displayAddress(host, port)

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

        /**
         * Saves [settings] in one commit, so the survey service sees the host, port and device name
         * change together. Call off the main thread.
         */
        @JvmStatic
        fun save(preferences: SharedPreferences, settings: GrpcConnectionSettings) {
            preferences.edit(commit = true) {
                putString(NetworkSurveyConstants.PROPERTY_NETWORK_SURVEY_CONNECTION_HOST, settings.host)
                putInt(NetworkSurveyConstants.PROPERTY_NETWORK_SURVEY_CONNECTION_PORT, settings.port)
                putString(NetworkSurveyConstants.PROPERTY_NETWORK_SURVEY_DEVICE_NAME, settings.deviceName)
            }
        }

        /** "host:port", with an IPv6 literal in brackets. */
        @JvmStatic
        fun displayAddress(host: String, port: Int): String =
            if (host.contains(IPV6_SEPARATOR) && !host.startsWith(IPV6_OPEN)) "$IPV6_OPEN$host$IPV6_CLOSE:$port" else "$host:$port"

        private const val IPV6_SEPARATOR = ':'
        private const val IPV6_OPEN = '['
        private const val IPV6_CLOSE = ']'
    }
}
