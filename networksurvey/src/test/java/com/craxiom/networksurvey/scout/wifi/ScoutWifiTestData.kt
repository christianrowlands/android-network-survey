package com.craxiom.networksurvey.scout.wifi

import com.craxiom.networksurvey.board.v1.ApObservation
import com.craxiom.networksurvey.board.v1.Band
import com.craxiom.networksurvey.board.v1.BoardModel
import com.craxiom.networksurvey.scout.model.PairedScout
import com.craxiom.networksurvey.scout.model.ScoutRecords
import com.google.protobuf.ByteString

/** Shared Scout Wi-Fi test data. */
object ScoutWifiTestData {
    val scout = PairedScout(
        id = "NS-02005E00ABCD", address = "02:00:5E:00:AB:CD", shortId = "ABCD",
        boardModel = BoardModel.BOARD_MODEL_ESP32_C5_DEVKITC_1_VALUE, modelName = "", firmwareVersion = "0.2.0",
        protocolMajor = 1, bands = listOf(Band.BAND_GHZ_2_4_VALUE, Band.BAND_GHZ_5_VALUE), pairedAtMs = 0,
    )

    fun ap(last: Int, rssi: Int, boardTimeMs: Long): ApObservation = ApObservation.newBuilder()
        .setBssid(ByteString.copyFrom(byteArrayOf(0x02, 0x00, 0x5E, 0x00, 0x53, last.toByte())))
        .setSsid(ByteString.copyFromUtf8("Net$last"))
        .setChannel(36).setFrequencyMhz(5180).setBand(Band.BAND_GHZ_5)
        .setRssiMax(rssi).setBoardTimeMs(boardTimeMs)
        .build()

    fun batch(receivedAtMs: Long, vararg aps: ApObservation, synced: Boolean = true) =
        ScoutRecords(scout.scoutId, receivedAtMs, synced, aps.toList(), emptyList(), emptyList(), 0)

    fun fix(timeMs: Long, latitude: Double = 38.9) = PhoneFix(timeMs, latitude, -77.0, 21.3, 4.6f, 1.25f)
}
