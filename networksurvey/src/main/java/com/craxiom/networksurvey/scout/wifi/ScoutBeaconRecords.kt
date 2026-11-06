package com.craxiom.networksurvey.scout.wifi

import com.craxiom.messaging.WifiBeaconRecord
import com.craxiom.messaging.WifiBeaconRecordData
import com.craxiom.messaging.wifi.NodeType
import com.craxiom.networksurvey.BuildConfig
import com.craxiom.networksurvey.board.v1.ApObservation
import com.craxiom.networksurvey.constants.WifiBeaconMessageConstants
import com.craxiom.networksurvey.model.ScoutWifiDetails
import com.craxiom.networksurvey.model.WifiRecordWrapper
import com.google.protobuf.BoolValue
import com.google.protobuf.FloatValue
import com.google.protobuf.Int32Value

/** One access point observation from a Scout, ready to become a beacon record. */
typealias ScoutBeacon = ScoutHeard<ApObservation>

/**
 * Builds NS Messaging beacon records from Scout observations, the counterpart of the phone's
 * `generateWiFiBeaconSurveyRecord`. The Scout fills the fields the phone leaves blank (service set
 * type, rates, cipher and AKM suites, beacon interval, SNR). Field values follow the iOS
 * `WifiBeaconRecord(ap:context:)`.
 */
object ScoutBeaconRecords {
    /** Beacons are broadcast, as the iOS app records them. */
    const val BROADCAST_ADDRESS = "ff:ff:ff:ff:ff:ff"

    /**
     * @param deviceId This phone's device serial number.
     * @param missionId The Mission ID to stamp, empty when no mission runs.
     * @param recordNumber The record number, 0 for a live view snapshot that takes none.
     * @param excluded Whether an SSID is on the user's exclusion list.
     */
    @JvmStatic
    fun build(
        beacon: ScoutBeacon,
        deviceId: String,
        missionId: String,
        recordNumber: Int,
        excluded: (String) -> Boolean,
    ): WifiRecordWrapper? {
        val ap = beacon.observation
        val bssid = ScoutWifiFields.mac(ap.bssid)
        if (bssid.isEmpty()) return null

        val data = WifiBeaconRecordData.newBuilder()
        val stamp = ScoutRecordStamp.of(beacon.captureTimeMs, beacon.fix)
        stamp.location?.let { applyLocation(data, it) }
        data.setDeviceSerialNumber(deviceId)
            .setDeviceTime(stamp.deviceTime)
            .setMissionId(missionId)
            .setRecordNumber(recordNumber)
        applyAccessPoint(data, ap, bssid)

        val record = WifiBeaconRecord.newBuilder()
            .setMessageType(WifiBeaconMessageConstants.WIFI_BEACON_RECORD_MESSAGE_TYPE)
            .setVersion(BuildConfig.MESSAGING_API_VERSION)
            .setData(data)
            .build()
        return WifiRecordWrapper(record, "", excluded(data.ssid), details(beacon), beacon.late)
    }

    private fun applyLocation(data: WifiBeaconRecordData.Builder, location: ScoutRecordStamp.Location) {
        data.setLatitude(location.latitude)
            .setLongitude(location.longitude)
            .setAltitude(location.altitude)
            .setAccuracy(location.accuracy)
            .setLocationAge(location.locationAge)
        if (location.speed != 0f) data.setSpeed(location.speed)
    }

    private fun applyAccessPoint(data: WifiBeaconRecordData.Builder, ap: ApObservation, bssid: String) {
        data.setBssid(bssid)
            .setSourceAddress(bssid)
            .setDestinationAddress(BROADCAST_ADDRESS)
            .setSsid(if (ap.ssidHidden) "" else ScoutWifiFields.ssid(ap.ssid))
            .setServiceSetType(ScoutWifiFields.serviceSetType(ap.capabilityInfo))
            .setSupportedRates(ScoutWifiFields.rates(ap.supportedRates))
            .setExtendedSupportedRates(ScoutWifiFields.rates(ap.extendedSupportedRates))
            .addAllCipherSuites(ScoutWifiFields.cipherSuites(ap))
            .addAllAkmSuites(ScoutWifiFields.akmSuites(ap))
            .setEncryptionType(ScoutWifiFields.encryptionType(ap.security))
            .setWps(BoolValue.of(ap.wps))
            .setBandwidth(ScoutWifiFields.bandwidth(ap.channelWidth, ap.phyFlags))
            .setSignalStrength(FloatValue.of(ap.rssiMax.toFloat()))
            .setNodeType(NodeType.AP)
            .setStandard(ScoutWifiFields.standard(ap.phyFlags, ap.band, ap.supportedRates.concat(ap.extendedSupportedRates)))
        if (ap.hasBeaconIntervalTu()) data.setBeaconInterval(Int32Value.of(ap.beaconIntervalTu))
        if (ap.passpoint) data.setPasspoint(BoolValue.of(true))
        if (ap.channel != 0) data.setChannel(Int32Value.of(ap.channel))
        if (ap.frequencyMhz != 0) data.setFrequencyMhz(Int32Value.of(ap.frequencyMhz))
        if (ap.hasNoiseFloorDbm()) data.setSnr(FloatValue.of((ap.rssiMax - ap.noiseFloorDbm).toFloat()))
    }

    private fun details(beacon: ScoutBeacon): ScoutWifiDetails {
        val ap = beacon.observation
        return ScoutWifiDetails(
            scoutName = beacon.scoutName,
            stationCount = if (ap.hasStationCount()) ap.stationCount else null,
            channelUtilization = if (ap.hasChannelUtilization()) ap.channelUtilization else null,
            phyFlags = ap.phyFlags,
            noiseFloorDbm = if (ap.hasNoiseFloorDbm()) ap.noiseFloorDbm else null,
            // Apart, for the Security card; the record's cipherSuites merges the two
            pairwiseCiphers = ap.pairwiseCiphersList.filter { it != 0 }.mapNotNull(ScoutWifiFields::cipherSuite).map { it.number },
            groupCipher = ap.groupCipher.takeIf { it != 0 }?.let(ScoutWifiFields::cipherSuite)?.number,
        )
    }
}
