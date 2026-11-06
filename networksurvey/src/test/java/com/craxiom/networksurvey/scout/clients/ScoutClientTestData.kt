package com.craxiom.networksurvey.scout.clients

import com.craxiom.networksurvey.board.v1.Band
import com.craxiom.networksurvey.board.v1.ClientObservation
import com.craxiom.networksurvey.board.v1.ClientSource
import com.craxiom.networksurvey.board.v1.PhyFlag
import com.craxiom.networksurvey.board.v1.ProbeObservation
import com.craxiom.networksurvey.scout.model.ScoutRecords
import com.craxiom.networksurvey.scout.wifi.ScoutWifiTestData
import com.google.protobuf.ByteString

/** Shared Wi-Fi client and probe test data. */
object ScoutClientTestData {
    /** `3e:7b:20:d4:91:<last>`, a locally administered (randomized) address. */
    fun mac(last: Int): ByteString = ByteString.copyFrom(byteArrayOf(0x3E, 0x7B, 0x20, 0xD4.toByte(), 0x91.toByte(), last.toByte()))

    val apBssid: ByteString = ByteString.copyFrom(byteArrayOf(0xE0.toByte(), 0x63, 0xDA.toByte(), 0x41, 0x7C, 0x22))

    fun client(last: Int, rssi: Int, boardTimeMs: Long, flags: Int = ClientSource.CLIENT_SOURCE_DATA_VALUE): ClientObservation =
        ClientObservation.newBuilder()
            .setClientMac(mac(last)).setAssociatedBssid(apBssid)
            .setChannel(36).setFrequencyMhz(5180).setBand(Band.BAND_GHZ_5)
            .setRssiMax(rssi).setRssiLast(rssi - 2).setNoiseFloorDbm(-92)
            .setFrameCount(12).setSourceFlags(flags).setLocallyAdministered(true)
            .setBoardTimeMs(boardTimeMs)
            .build()

    fun probe(last: Int, ssid: String, rssi: Int, boardTimeMs: Long): ProbeObservation = ProbeObservation.newBuilder()
        .setClientMac(mac(last)).setSsid(ByteString.copyFromUtf8(ssid))
        .setDestinationAddress(ByteString.copyFrom(ByteArray(6) { 0xFF.toByte() }))
        .setBssid(ByteString.copyFrom(ByteArray(6) { 0xFF.toByte() }))
        .setChannel(6).setFrequencyMhz(2437).setBand(Band.BAND_GHZ_2_4)
        .setRssiMax(rssi).setCount(3).setLocallyAdministered(true).setPhyFlags(PhyFlag.PHY_FLAG_HT_VALUE)
        .setBoardTimeMs(boardTimeMs)
        .build()

    fun batch(
        receivedAtMs: Long,
        clients: List<ClientObservation> = emptyList(),
        probes: List<ProbeObservation> = emptyList(),
    ) = ScoutRecords(ScoutWifiTestData.scout.scoutId, receivedAtMs, true, emptyList(), clients, probes, 0)
}
