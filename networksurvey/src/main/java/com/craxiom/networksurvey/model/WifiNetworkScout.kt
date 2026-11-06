package com.craxiom.networksurvey.model

import com.craxiom.messaging.WifiBeaconRecordData
import java.io.Serializable

/**
 * What the Wi-Fi details page shows for an access point an NS Scout heard, beyond the phone's
 * fields: the Scout's own details and the beacon fields the phone never fills (AKM and cipher
 * suites, WPS, beacon interval, SNR). Plain values so it travels in the [WifiNetwork] parcel.
 *
 * @property akmSuites `AkmSuite` numbers, in the order the AP advertised them.
 * @property cipherSuites `CipherSuite` numbers: pairwise then group, without repeats.
 * @property beaconIntervalTu The beacon interval in time units (1.024 ms), null when not heard.
 * @property snrDb Signal over the noise floor, null without a noise floor.
 */
data class WifiNetworkScout(
    val details: ScoutWifiDetails,
    val akmSuites: List<Int>,
    val cipherSuites: List<Int>,
    val wps: Boolean?,
    val beaconIntervalTu: Int?,
    val snrDb: Float?,
) : Serializable {
    companion object {
        /** @return The Scout's part of a record, or null for a record from the phone's own scan. */
        @JvmStatic
        fun from(data: WifiBeaconRecordData, details: ScoutWifiDetails?): WifiNetworkScout? = details?.let {
            WifiNetworkScout(
                details = it,
                // Copied into plain lists: the protobuf lists are not serializable
                akmSuites = data.akmSuitesValueList.toList(),
                cipherSuites = data.cipherSuitesValueList.toList(),
                wps = if (data.hasWps()) data.wps.value else null,
                beaconIntervalTu = if (data.hasBeaconInterval()) data.beaconInterval.value else null,
                snrDb = if (data.hasSnr()) data.snr.value else null,
            )
        }
    }
}
