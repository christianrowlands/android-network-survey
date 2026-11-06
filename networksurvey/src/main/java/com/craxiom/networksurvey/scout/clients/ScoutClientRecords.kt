package com.craxiom.networksurvey.scout.clients

import com.craxiom.messaging.WifiClientRecord
import com.craxiom.messaging.WifiClientRecordData
import com.craxiom.messaging.WifiProbeRequestRecord
import com.craxiom.messaging.WifiProbeRequestRecordData
import com.craxiom.messaging.wifi.NodeType
import com.craxiom.networksurvey.BuildConfig
import com.craxiom.networksurvey.board.v1.ClientObservation
import com.craxiom.networksurvey.board.v1.ProbeObservation
import com.craxiom.networksurvey.scout.wifi.ScoutHeard
import com.craxiom.networksurvey.scout.wifi.ScoutRecordStamp
import com.craxiom.networksurvey.scout.wifi.ScoutWifiFields
import com.google.protobuf.FloatValue
import com.google.protobuf.Int32Value

/**
 * Builds NS Messaging Wi-Fi client and probe request records from Scout observations. Field values
 * follow the iOS `WifiClientRecord(client:context:)` and `WifiProbeRequestRecord(probe:context:)`.
 */
object ScoutClientRecords {
    /** The NS Messaging message type of a Wi-Fi client record. */
    const val CLIENT_MESSAGE_TYPE = "WifiClientRecord"

    /** The NS Messaging message type of a probe request record. */
    const val PROBE_REQUEST_MESSAGE_TYPE = "WifiProbeRequestRecord"

    /**
     * @param deviceId This phone's device serial number.
     * @param missionId The Mission ID to stamp, empty when no mission runs.
     * @param recordNumber The Wi-Fi client record number.
     * @return The record, or null when the client's address is not a MAC address.
     */
    @JvmStatic
    fun client(heard: ScoutHeard<ClientObservation>, deviceId: String, missionId: String, recordNumber: Int): WifiClientRecord? {
        val client = heard.observation
        val mac = ScoutWifiFields.mac(client.clientMac)
        if (mac.isEmpty()) return null

        val data = WifiClientRecordData.newBuilder()
        val stamp = ScoutRecordStamp.of(heard.captureTimeMs, heard.fix)
        stamp.location?.let {
            data.setLatitude(it.latitude).setLongitude(it.longitude).setAltitude(it.altitude)
                .setAccuracy(it.accuracy).setLocationAge(it.locationAge)
            if (it.speed != 0f) data.setSpeed(it.speed)
        }
        data.setDeviceSerialNumber(deviceId)
            .setDeviceTime(stamp.deviceTime)
            .setMissionId(missionId)
            .setRecordNumber(recordNumber)
            .setSourceAddress(mac)
            .setBssid(ScoutWifiFields.mac(client.associatedBssid))
            .setSignalStrength(FloatValue.of(client.rssiMax.toFloat()))
            .addAllFrameTypes(ScoutClientFields.frameTypes(client.sourceFlags))
        if (client.channel != 0) data.setChannel(Int32Value.of(client.channel))
        if (client.frequencyMhz != 0) data.setFrequencyMhz(Int32Value.of(client.frequencyMhz))
        if (client.hasNoiseFloorDbm()) data.setSnr(FloatValue.of((client.rssiMax - client.noiseFloorDbm).toFloat()))

        return WifiClientRecord.newBuilder()
            .setMessageType(CLIENT_MESSAGE_TYPE)
            .setVersion(BuildConfig.MESSAGING_API_VERSION)
            .setData(data)
            .build()
    }

    /**
     * @param deviceId This phone's device serial number.
     * @param missionId The Mission ID to stamp, empty when no mission runs.
     * @param recordNumber The probe request record number.
     * @return The record, or null when the client's address is not a MAC address.
     */
    @JvmStatic
    fun probe(heard: ScoutHeard<ProbeObservation>, deviceId: String, missionId: String, recordNumber: Int): WifiProbeRequestRecord? {
        val probe = heard.observation
        val mac = ScoutWifiFields.mac(probe.clientMac)
        if (mac.isEmpty()) return null

        val data = WifiProbeRequestRecordData.newBuilder()
        val stamp = ScoutRecordStamp.of(heard.captureTimeMs, heard.fix)
        stamp.location?.let {
            data.setLatitude(it.latitude).setLongitude(it.longitude).setAltitude(it.altitude)
                .setAccuracy(it.accuracy).setLocationAge(it.locationAge)
            if (it.speed != 0f) data.setSpeed(it.speed)
        }
        data.setDeviceSerialNumber(deviceId)
            .setDeviceTime(stamp.deviceTime)
            .setMissionId(missionId)
            .setRecordNumber(recordNumber)
            .setSourceAddress(mac)
            .setDestinationAddress(ScoutWifiFields.mac(probe.destinationAddress))
            .setBssid(ScoutWifiFields.mac(probe.bssid))
            .setSsid(ScoutWifiFields.ssid(probe.ssid))
            .setSignalStrength(FloatValue.of(probe.rssiMax.toFloat()))
            .setNodeType(NodeType.NON_AP_STA)
            // No rates in a probe observation, so a legacy 2.4 GHz probe stays UNKNOWN
            .setStandard(ScoutWifiFields.standard(probe.phyFlags, probe.band))
        if (probe.channel != 0) data.setChannel(Int32Value.of(probe.channel))
        if (probe.frequencyMhz != 0) data.setFrequencyMhz(Int32Value.of(probe.frequencyMhz))
        if (probe.hasNoiseFloorDbm()) data.setSnr(FloatValue.of((probe.rssiMax - probe.noiseFloorDbm).toFloat()))

        return WifiProbeRequestRecord.newBuilder()
            .setMessageType(PROBE_REQUEST_MESSAGE_TYPE)
            .setVersion(BuildConfig.MESSAGING_API_VERSION)
            .setData(data)
            .build()
    }
}
