package com.craxiom.networksurvey.scout.wifi

import com.craxiom.messaging.wifi.AkmSuite
import com.craxiom.messaging.wifi.CipherSuite
import com.craxiom.messaging.wifi.EncryptionType
import com.craxiom.messaging.wifi.NodeType
import com.craxiom.messaging.wifi.ServiceSetType
import com.craxiom.messaging.wifi.Standard
import com.craxiom.messaging.wifi.WifiBandwidth
import com.craxiom.networksurvey.board.v1.ApObservation
import com.craxiom.networksurvey.board.v1.Band
import com.craxiom.networksurvey.board.v1.ChannelWidth
import com.craxiom.networksurvey.board.v1.PhyFlag
import com.craxiom.networksurvey.board.v1.SecurityType
import com.google.protobuf.ByteString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

/**
 * Scout observation to NS Messaging beacon record, checked against the iOS converter's golden
 * record (`convert-beacon.golden.json`) for the same observation, so both apps write the same
 * values for the same frames.
 */
class ScoutBeaconRecordsTest {
    private lateinit var zone: TimeZone

    @Before
    fun utc() {
        zone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restore() {
        TimeZone.setDefault(zone)
    }

    /** The iOS `Samples.ap`. */
    private val ap: ApObservation = ApObservation.newBuilder()
        .setBoardTimeMs(TIME).setChannel(36).setFrequencyMhz(5180).setBand(Band.BAND_GHZ_5)
        .setBssid(bytes(0x02, 0x00, 0x5E, 0x00, 0x53, 0x01))
        .setSsid(ByteString.copyFromUtf8("ExampleNet"))
        .setRssiMax(-48).setRssiLast(-51).setNoiseFloorDbm(-95).setBeaconCount(2).setBeaconIntervalTu(100)
        .setCapabilityInfo(0x0011).setSecurity(SecurityType.SECURITY_TYPE_WPA2_WPA3)
        .setGroupCipher(0x000FAC04).addPairwiseCiphers(0x000FAC04).addAkmSuites(0x000FAC02).addAkmSuites(0x000FAC08)
        .setPhyFlags(PhyFlag.PHY_FLAG_HT_VALUE or PhyFlag.PHY_FLAG_VHT_VALUE or PhyFlag.PHY_FLAG_HE_VALUE)
        .setChannelWidth(ChannelWidth.CHANNEL_WIDTH_MHZ_80).setWps(true)
        .setSupportedRates(bytes(0x8C, 0x12, 0x98, 0x24, 0xB0, 0x48, 0x60, 0x6C))
        .setStationCount(7).setChannelUtilization(40)
        .build()

    private fun beacon(observation: ApObservation = ap, fix: PhoneFix? = PhoneFix(TIME - 800, 38.889484, -77.035278, 21.3, 4.6f, 1.25f), late: Boolean = false) =
        ScoutBeacon(observation, "NS-ABCD", TIME, fix, late)

    private fun build(beacon: ScoutBeacon, excluded: Set<String> = emptySet()) =
        ScoutBeaconRecords.build(beacon, "8C2E1F7A", "NS 8C2E1F7A 20261014-180000", 1) { it in excluded }!!

    @Test
    fun `matches the iOS golden beacon record`() {
        val data = build(beacon()).wifiBeaconRecord.data

        assertEquals("8C2E1F7A", data.deviceSerialNumber)
        assertEquals("2026-10-14T18:03:07.25Z", data.deviceTime)
        assertEquals(38.889484, data.latitude, 0.0)
        assertEquals(-77.035278, data.longitude, 0.0)
        assertEquals(21.3f, data.altitude, 0.001f)
        assertEquals("NS 8C2E1F7A 20261014-180000", data.missionId)
        assertEquals(1, data.recordNumber)
        assertEquals(5, data.accuracy)
        assertEquals("02:00:5e:00:53:01", data.sourceAddress)
        assertEquals("ff:ff:ff:ff:ff:ff", data.destinationAddress)
        assertEquals("02:00:5e:00:53:01", data.bssid)
        assertEquals(100, data.beaconInterval.value)
        assertEquals(ServiceSetType.ESS, data.serviceSetType)
        assertEquals("ExampleNet", data.ssid)
        assertEquals("6; 9; 12; 18; 24; 36; 48; 54", data.supportedRates)
        assertEquals("", data.extendedSupportedRates)
        assertEquals(listOf(CipherSuite.CCMP), data.cipherSuitesList)
        assertEquals(listOf(AkmSuite.PSK, AkmSuite.SAE), data.akmSuitesList)
        assertEquals(EncryptionType.WPA2_WPA3, data.encryptionType)
        assertTrue(data.wps.value)
        assertFalse(data.hasPasspoint())
        assertEquals(36, data.channel.value)
        assertEquals(5180, data.frequencyMhz.value)
        assertEquals(-48f, data.signalStrength.value, 0f)
        assertEquals(47f, data.snr.value, 0f)
        assertEquals(NodeType.AP, data.nodeType)
        assertEquals(Standard.IEEE80211AX, data.standard)
        assertEquals(WifiBandwidth.MHZ_80, data.bandwidth)
        assertEquals(800, data.locationAge)
        assertEquals(1.25f, data.speed, 0f)
    }

    @Test
    fun `keeps the Scout's name and BSS Load for the screens`() {
        val record = build(beacon(late = true))

        assertEquals(7, record.scoutDetails.stationCount)
        assertEquals(40, record.scoutDetails.channelUtilization)
        assertEquals("NS-ABCD", record.scoutDetails.scoutName)
        assertTrue(record.isLate)
        assertEquals("", record.capabilitiesString)
    }

    @Test
    fun `without a fix the location fields are left out`() {
        val data = build(beacon(fix = null)).wifiBeaconRecord.data

        assertEquals(0.0, data.latitude, 0.0)
        assertEquals(0, data.accuracy)
        assertEquals(0, data.locationAge)
        assertEquals(0f, data.speed, 0f)
        assertEquals("2026-10-14T18:03:07.25Z", data.deviceTime)
    }

    @Test
    fun `a hidden SSID is empty, and the exclusion list applies`() {
        assertEquals("", build(beacon(ap.toBuilder().setSsidHidden(true).build())).wifiBeaconRecord.data.ssid)
        assertTrue(build(beacon(), excluded = setOf("ExampleNet")).isExcluded)
    }

    @Test
    fun `an observation without a BSSID gives no record`() {
        assertNull(ScoutBeaconRecords.build(beacon(ap.toBuilder().setBssid(ByteString.EMPTY).build()), "id", "", 1) { false })
    }

    @Test
    fun `field derivations match the iOS WifiFields`() {
        val ht = PhyFlag.PHY_FLAG_HT_VALUE
        val vht = PhyFlag.PHY_FLAG_VHT_VALUE
        assertEquals(Standard.IEEE80211BE, ScoutWifiFields.standard(8 or 4 or ht, Band.BAND_GHZ_5))
        assertEquals(Standard.IEEE80211AC, ScoutWifiFields.standard(vht or ht, Band.BAND_GHZ_5))
        assertEquals(Standard.IEEE80211N, ScoutWifiFields.standard(vht or ht, Band.BAND_GHZ_2_4))
        assertEquals(Standard.IEEE80211A, ScoutWifiFields.standard(0, Band.BAND_GHZ_5))
        assertEquals(Standard.IEEE80211B, ScoutWifiFields.standard(0, Band.BAND_GHZ_2_4, bytes(0x82, 0x84, 0x8B, 0x96)))
        assertEquals(Standard.IEEE80211BG, ScoutWifiFields.standard(0, Band.BAND_GHZ_2_4, bytes(0x82, 0x84, 0x0C)))
        assertEquals("1; 2; 5.5; 11; 18; 24; 36; 54", ScoutWifiFields.rates(bytes(0x82, 0x84, 0x8B, 0x96, 0x24, 0x30, 0x48, 0x6C)))
        assertEquals("6", ScoutWifiFields.rates(bytes(0x0C, 0xFF, 0xFB)))
        assertEquals(WifiBandwidth.MHZ_20, ScoutWifiFields.bandwidth(ChannelWidth.CHANNEL_WIDTH_UNKNOWN, 0))
        assertEquals(WifiBandwidth.UNKNOWN, ScoutWifiFields.bandwidth(ChannelWidth.CHANNEL_WIDTH_UNKNOWN, ht))
        assertEquals(listOf(CipherSuite.TKIP, CipherSuite.CCMP), ScoutWifiFields.cipherSuites(
            ap.toBuilder().setSecurity(SecurityType.SECURITY_TYPE_WPA_WPA2).clearPairwiseCiphers()
                .addPairwiseCiphers(0x0050F202).addPairwiseCiphers(0x000FAC04).setGroupCipher(0x0050F202).build(),
        ))
        assertEquals(listOf(CipherSuite.OPEN), ScoutWifiFields.cipherSuites(ap.toBuilder().setSecurity(SecurityType.SECURITY_TYPE_OPEN).build()))
        assertEquals(listOf(AkmSuite.IEEE_8021X, AkmSuite.FT_IEEE_8021X, AkmSuite.IEEE_8021X_SHA256), ScoutWifiFields.akmSuites(
            ap.toBuilder().clearAkmSuites().addAkmSuites(0x000FAC01).addAkmSuites(0x000FAC03).addAkmSuites(0x000FAC05).build(),
        ))
        assertEquals(EncryptionType.UNKNOWN, ScoutWifiFields.encryptionType(SecurityType.SECURITY_TYPE_OWE))
    }

    private fun bytes(vararg values: Int): ByteString = ByteString.copyFrom(ByteArray(values.size) { values[it].toByte() })

    private companion object {
        /** 2026-10-14T18:03:07.25Z, the iOS sample time. */
        const val TIME = 1_792_000_987_250L
    }
}
