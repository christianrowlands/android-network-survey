package com.craxiom.networksurvey.scout.link

import com.craxiom.networksurvey.board.v1.Band
import com.craxiom.networksurvey.board.v1.BleAddressType
import com.craxiom.networksurvey.board.v1.BoardInfo
import com.craxiom.networksurvey.board.v1.BoardModel
import com.craxiom.networksurvey.board.v1.BoardState
import com.craxiom.networksurvey.board.v1.BoardStatus
import com.craxiom.networksurvey.board.v1.ChannelWidth
import com.craxiom.networksurvey.board.v1.Chip
import com.craxiom.networksurvey.board.v1.ControlRequest
import com.craxiom.networksurvey.board.v1.ControlResponse
import com.craxiom.networksurvey.board.v1.FramePhy
import com.craxiom.networksurvey.board.v1.OtaAck
import com.craxiom.networksurvey.board.v1.RawFrame
import com.craxiom.networksurvey.board.v1.Record
import com.craxiom.networksurvey.board.v1.RecordBatch
import com.craxiom.networksurvey.board.v1.ResultCode
import com.craxiom.networksurvey.board.v1.Role
import com.craxiom.networksurvey.board.v1.SecurityType
import com.craxiom.networksurvey.scout.link.ScoutFixtureFiles.T0
import com.craxiom.networksurvey.scout.model.ScoutId
import com.google.protobuf.ByteString
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every message in network-survey-esp32 test/fixtures/proto, encoded by the firmware's nanopb,
 * decoded with the app's generated protobuf-java classes. Field values are those in the firmware's
 * test/host/proto_roundtrip/main.c.
 */
class ScoutProtoFixtureTest {
    private fun bytes(name: String) = ScoutFixtureFiles.bytes("proto/$name.bin")

    private fun ap(name: String) = RecordBatch.parseFrom(bytes(name)).recordsList.single().ap

    private val apBssid = byteArrayOf(0x02, 0x00, 0x5E, 0x10, 0x00, 0x01)
    private val broadcast = ByteArray(6) { 0xFF.toByte() }

    @Test
    fun boardInfo() {
        val info = BoardInfo.parseFrom(bytes("board_info"))
        assertEquals(1, info.protocolMajor)
        assertEquals(0, info.protocolMinor)
        assertEquals("0.1.0", info.firmwareVersion)
        assertEquals(BoardModel.BOARD_MODEL_ESP32_C5_DEVKITC_1, info.boardModel)
        assertEquals(Chip.CHIP_ESP32_C5, info.chip)
        assertEquals(listOf(Band.BAND_GHZ_2_4, Band.BAND_GHZ_5), info.bandsList)
        assertFalse(info.gpsPresent)
        assertEquals("ABCD", info.shortId)
        assertEquals(517, info.preferredMtu)
        assertEquals("ESP32-C5-DevKitC-1", info.modelName)
        assertEquals("NS-02005E00ABCD", ScoutId.fromBtAddress(info.btAddress.toByteArray())?.value)
    }

    @Test
    fun boardStatus() {
        val status = BoardStatus.parseFrom(bytes("board_status"))
        assertEquals(T0 + 10_000, status.boardTimeMs)
        assertTrue(status.timeSynced)
        assertEquals(BoardState.BOARD_STATE_SURVEYING, status.state)
        assertEquals(Role.ROLE_BAND_5, status.role)
        assertEquals(149, status.currentChannel)
        assertEquals(42, status.recordsPerSec)
        assertEquals(12, status.ringFillPercent)
        assertEquals(3, status.droppedRecords)
        assertEquals(0, status.droppedRawFrames)
        assertEquals(17, status.droppedRxFrames)
        assertEquals(850, status.rxFramesPerSec)
        assertEquals(3_600_000L, status.uptimeMs)
        assertTrue(status.hasBatteryMv())
        assertEquals(3900, status.batteryMv)
        assertFalse(status.pairingMode)
        assertEquals(1, status.bondCount)
        assertEquals(247, status.mtu)
    }

    @Test
    fun controlRequestConfigure_matchesTheAppsOwnEncoding() {
        val request = ControlRequest.parseFrom(bytes("control_request_configure"))
        assertEquals(7, request.requestId)
        assertTrue(request.hasConfigure())
        assertEquals(listOf(1, 6, 11, 36, 40, 44, 48, 149), request.configure.channelsList)

        // The app leaves channels, dwell and raw limits to the board; the fixture sets them all
        val ours = ScoutCapture(wifiAccessPoints = true, wifiClients = true, wifiProbes = true, rawFrames = true)
            .toConfigureRequest().setRequestId(7)
        ours.configureBuilder
            .addAllChannels(listOf(1, 6, 11, 36, 40, 44, 48, 149))
            .setDwell24Ms(250).setDwell5Ms(150)
            .setRawSnaplen(128).setRawMaxFramesPerSec(50).setRawTypeMask(1)
        assertArrayEquals(bytes("control_request_configure"), ours.build().toByteArray())
    }

    @Test
    fun controlRequestHello_matchesTheAppsOwnEncoding() {
        val request = ControlRequest.parseFrom(bytes("control_request_hello"))
        assertEquals(1, request.requestId)
        assertEquals("1.0.0", request.hello.appVersion)

        val ours = ScoutProtocol.hello("1.0.0").setRequestId(1).build()
        assertArrayEquals(bytes("control_request_hello"), ours.toByteArray())
    }

    @Test
    fun controlResponseHello() {
        val response = ControlResponse.parseFrom(bytes("control_response_hello"))
        assertEquals(1, response.requestId)
        assertEquals(ResultCode.RESULT_CODE_OK, response.result)
        assertTrue(response.hasHello())
        assertEquals(1, response.hello.protocolMajor)
        assertTrue(response.hello.compatible)
        assertEquals("0.1.0", response.hello.firmwareVersion)
        assertTrue(response.hello.otaImageConfirmed)
    }

    @Test
    fun otaAck() {
        val ack = OtaAck.parseFrom(bytes("ota_ack"))
        assertEquals(65536, ack.nextOffset)
    }

    @Test
    fun rawFrame() {
        val raw = RawFrame.parseFrom(bytes("raw_frame"))
        assertEquals(T0 * 1000 + 456, raw.boardTimeUs)
        assertTrue(raw.timeSynced)
        assertEquals(6, raw.channel)
        assertEquals(2437, raw.frequencyMhz)
        assertEquals(-44, raw.rssi)
        assertEquals(-96, raw.noiseFloorDbm)
        assertEquals(FramePhy.FRAME_PHY_LEGACY, raw.phy)
        assertEquals(2, raw.rate500Kbps)
        assertFalse(raw.hasMcs())
        assertEquals(180, raw.origLen)
        assertEquals(40, raw.data.size())
        assertEquals(0x80.toByte(), raw.data.byteAt(0))
    }

    @Test
    fun recordAp() {
        val ap = ap("record_ap")
        assertEquals(T0, ap.boardTimeMs)
        assertEquals(6, ap.channel)
        assertEquals(2437, ap.frequencyMhz)
        assertEquals(Band.BAND_GHZ_2_4, ap.band)
        assertArrayEquals(apBssid, ap.bssid.toByteArray())
        assertEquals(ByteString.copyFromUtf8("ExampleNet"), ap.ssid)
        assertEquals(-42, ap.rssiMax)
        assertEquals(-47, ap.rssiLast)
        assertEquals(-95, ap.noiseFloorDbm)
        assertEquals(9, ap.beaconCount)
        assertEquals(2, ap.probeResponseCount)
        assertEquals(100, ap.beaconIntervalTu)
        assertEquals(0x0431, ap.capabilityInfo)
        assertEquals(SecurityType.SECURITY_TYPE_WPA2_WPA3, ap.security)
        assertEquals(0x000FAC04, ap.groupCipher)
        assertEquals(listOf(0x000FAC04), ap.pairwiseCiphersList)
        assertEquals(listOf(0x000FAC02, 0x000FAC08), ap.akmSuitesList)
        assertEquals(7, ap.phyFlags)
        assertEquals(ChannelWidth.CHANNEL_WIDTH_MHZ_40, ap.channelWidth)
        assertTrue(ap.wps)
        assertFalse(ap.passpoint)
        assertEquals(8, ap.supportedRates.size())
        assertEquals(4, ap.extendedSupportedRates.size())
        assertEquals(5, ap.stationCount)
        assertEquals(37, ap.channelUtilization)
        assertFalse(ap.detailTrimmed)
    }

    @Test
    fun recordClient() {
        val client = RecordBatch.parseFrom(bytes("record_client")).recordsList.single().client
        assertEquals(T0 + 500, client.boardTimeMs)
        assertEquals(36, client.channel)
        assertEquals(Band.BAND_GHZ_5, client.band)
        assertArrayEquals(byteArrayOf(0x0A, 0x11, 0x22, 0x33, 0x44, 0x55), client.clientMac.toByteArray())
        assertArrayEquals(apBssid, client.associatedBssid.toByteArray())
        assertEquals(-60, client.rssiMax)
        assertEquals(14, client.frameCount)
        assertEquals(67, client.sourceFlags)
        assertFalse(client.hasNoiseFloorDbm())
        assertTrue(client.locallyAdministered)
    }

    @Test
    fun recordProbe() {
        val probe = RecordBatch.parseFrom(bytes("record_probe")).recordsList.single().probe
        assertEquals(T0 + 750, probe.boardTimeMs)
        assertEquals(1, probe.channel)
        assertEquals(ByteString.copyFromUtf8("Cafe-Guest"), probe.ssid)
        assertEquals(-75, probe.rssiLast)
        assertEquals(3, probe.count)
        assertTrue(probe.locallyAdministered)
        assertArrayEquals(broadcast, probe.destinationAddress.toByteArray())
        assertArrayEquals(broadcast, probe.bssid.toByteArray())
        assertEquals(1, probe.phyFlags)
    }

    @Test
    fun recordBle_decodesThoughAndroidIgnoresIt() {
        val ble = RecordBatch.parseFrom(bytes("record_ble")).recordsList.single().ble
        assertEquals(BleAddressType.BLE_ADDRESS_TYPE_RANDOM, ble.addressType)
        assertEquals(24, ble.advData.size())
        assertEquals(ByteString.copyFromUtf8("ExampleTag"), ble.localName)
        assertEquals(0xFFFF, ble.companyId)
        assertEquals(-12, ble.txPower)
    }

    @Test
    fun recordGps_decodesThoughAndroidIgnoresIt() {
        val gps = RecordBatch.parseFrom(bytes("record_gps")).recordsList.single().gps
        assertEquals(39.7392, gps.latitude, 0.0)
        assertEquals(-104.9903, gps.longitude, 0.0)
        assertEquals(1609.5f, gps.altitudeM, 0f)
        assertEquals(9, gps.satellites)
        assertEquals(270.0f, gps.courseDeg, 0f)
    }

    @Test
    fun recordBatchMixed_isNotSyncedAndKeepsBootTimes() {
        val batch = RecordBatch.parseFrom(bytes("record_batch_mixed"))
        assertFalse(batch.timeSynced)
        assertEquals(listOf(Record.KindCase.AP, Record.KindCase.BLE, Record.KindCase.GPS), batch.recordsList.map { it.kindCase })
        assertEquals(15_000L, batch.getRecords(0).ap.boardTimeMs)
        assertEquals(15_500L, batch.getRecords(2).gps.boardTimeMs)
    }
}
