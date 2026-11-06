package com.craxiom.networksurvey.scout.clients

import com.craxiom.messaging.wifi.ClientFrameType
import com.craxiom.messaging.wifi.NodeType
import com.craxiom.messaging.wifi.Standard
import com.craxiom.networksurvey.BuildConfig
import com.craxiom.networksurvey.board.v1.ClientSource
import com.craxiom.networksurvey.scout.clients.ScoutClientTestData.client
import com.craxiom.networksurvey.scout.clients.ScoutClientTestData.probe
import com.craxiom.networksurvey.scout.wifi.ScoutHeard
import com.craxiom.networksurvey.scout.wifi.ScoutWifiTestData.fix
import com.craxiom.networksurvey.scout.wifi.ScoutWifiTestData.scout
import com.google.protobuf.ByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wi-Fi client and probe request records built from Scout observations carry the iOS app's field
 * values and the capture time and place.
 */
class ScoutClientRecordsTest {

    private fun <O> heard(observation: O, withFix: Boolean = true) = ScoutHeard(
        observation = observation, scoutName = scout.name,
        captureTimeMs = HEARD, fix = if (withFix) fix(HEARD - 1_500) else null, late = false,
    )

    @Test
    fun `a client record names the client, its AP and the frames it sent`() {
        val flags = ClientSource.CLIENT_SOURCE_DATA_VALUE or ClientSource.CLIENT_SOURCE_AUTH_VALUE
        val record = ScoutClientRecords.client(heard(client(1, -51, 0, flags)), "device-1", "NS mission", 7)!!
        val data = record.data

        assertEquals(ScoutClientRecords.CLIENT_MESSAGE_TYPE, record.messageType)
        assertEquals(BuildConfig.MESSAGING_API_VERSION, record.version)
        assertEquals("3e:7b:20:d4:91:01", data.sourceAddress)
        assertEquals("e0:63:da:41:7c:22", data.bssid)
        assertEquals(36, data.channel.value)
        assertEquals(5180, data.frequencyMhz.value)
        assertEquals(-51f, data.signalStrength.value, 0f)
        assertEquals(41f, data.snr.value, 0f)
        assertEquals(listOf(ClientFrameType.DATA, ClientFrameType.AUTHENTICATION), data.frameTypesList)
        assertEquals("device-1", data.deviceSerialNumber)
        assertEquals("NS mission", data.missionId)
        assertEquals(7, data.recordNumber)
        assertEquals(38.9, data.latitude, 0.0)
        assertEquals(1_500, data.locationAge)
    }

    @Test
    fun `a probe record carries the requested network and its standard`() {
        val record = ScoutClientRecords.probe(heard(probe(2, "eduroam", -63, 0)), "device-1", "", 3)!!
        val data = record.data

        assertEquals(ScoutClientRecords.PROBE_REQUEST_MESSAGE_TYPE, record.messageType)
        assertEquals("3e:7b:20:d4:91:02", data.sourceAddress)
        assertEquals("ff:ff:ff:ff:ff:ff", data.destinationAddress)
        assertEquals("ff:ff:ff:ff:ff:ff", data.bssid)
        assertEquals("eduroam", data.ssid)
        assertEquals(6, data.channel.value)
        assertEquals(NodeType.NON_AP_STA, data.nodeType)
        assertEquals(Standard.IEEE80211N, data.standard)
        assertFalse(data.hasSnr())
    }

    @Test
    fun `a record without a nearby fix has no location`() {
        val data = ScoutClientRecords.client(heard(client(1, -51, 0), withFix = false), "d", "", 1)!!.data
        assertEquals(0.0, data.latitude, 0.0)
        assertEquals(0, data.locationAge)
    }

    @Test
    fun `an observation without a MAC address makes no record`() {
        val noMac = client(1, -51, 0).toBuilder().setClientMac(ByteString.EMPTY).build()
        assertNull(ScoutClientRecords.client(heard(noMac), "d", "", 1))
    }

    @Test
    fun `frame type bits follow the protocol order`() {
        val all = ClientSource.entries.filter { it != ClientSource.UNRECOGNIZED }.sumOf { it.number }
        assertEquals(
            listOf(
                ClientFrameType.DATA, ClientFrameType.NULL_DATA, ClientFrameType.PROBE_REQUEST,
                ClientFrameType.ASSOCIATION_REQUEST, ClientFrameType.REASSOCIATION_REQUEST,
                ClientFrameType.AUTHENTICATION, ClientFrameType.ACTION,
            ),
            ScoutClientFields.frameTypes(all),
        )
        assertTrue(ScoutClientFields.frameTypes(0).isEmpty())
    }

    private companion object {
        const val HEARD = 1_792_000_030_000L
    }
}
