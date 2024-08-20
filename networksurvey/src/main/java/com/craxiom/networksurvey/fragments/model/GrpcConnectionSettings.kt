package com.craxiom.networksurvey.fragments.model

/**
 * Holds the gRPC connection settings for a network survey.
 */
data class GrpcConnectionSettings(
    val host: String?,
    val port: Int?,
    val deviceName: String?,
    val cellularStreamEnabled: Boolean?,
    val phoneStateStreamEnabled: Boolean?,
    val wifiStreamEnabled: Boolean?,
    val bluetoothStreamEnabled: Boolean?,
    val gnssStreamEnabled: Boolean?,
    val deviceStatusStreamEnabled: Boolean?
) {

    class Builder {
        private var host: String? = null
        private var port: Int? = null
        private var deviceName: String? = null
        private var cellularStreamEnabled: Boolean? = null
        private var phoneStateStreamEnabled: Boolean? = null
        private var wifiStreamEnabled: Boolean? = null
        private var bluetoothStreamEnabled: Boolean? = null
        private var gnssStreamEnabled: Boolean? = null
        private var deviceStatusStreamEnabled: Boolean? = null

        fun host(host: String) = apply { this.host = host }
        fun port(port: Int) = apply { this.port = port }
        fun deviceName(deviceName: String) = apply { this.deviceName = deviceName }
        fun cellularStreamEnabled(cellularStreamEnabled: Boolean) =
            apply { this.cellularStreamEnabled = cellularStreamEnabled }

        fun phoneStateStreamEnabled(phoneStateStreamEnabled: Boolean) =
            apply { this.phoneStateStreamEnabled = phoneStateStreamEnabled }

        fun wifiStreamEnabled(wifiStreamEnabled: Boolean) =
            apply { this.wifiStreamEnabled = wifiStreamEnabled }

        fun bluetoothStreamEnabled(bluetoothStreamEnabled: Boolean) =
            apply { this.bluetoothStreamEnabled = bluetoothStreamEnabled }

        fun gnssStreamEnabled(gnssStreamEnabled: Boolean) =
            apply { this.gnssStreamEnabled = gnssStreamEnabled }

        fun deviceStatusStreamEnabled(deviceStatusStreamEnabled: Boolean) =
            apply { this.deviceStatusStreamEnabled = deviceStatusStreamEnabled }

        fun build(): GrpcConnectionSettings {
            return GrpcConnectionSettings(
                host,
                port,
                deviceName,
                cellularStreamEnabled ?: false,
                phoneStateStreamEnabled ?: false,
                wifiStreamEnabled ?: false,
                bluetoothStreamEnabled ?: false,
                gnssStreamEnabled ?: false,
                deviceStatusStreamEnabled ?: false
            )
        }
    }
}
