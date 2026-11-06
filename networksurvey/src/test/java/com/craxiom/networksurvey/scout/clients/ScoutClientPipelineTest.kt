package com.craxiom.networksurvey.scout.clients

import com.craxiom.messaging.wifi.ClientFrameType
import com.craxiom.networksurvey.board.v1.ClientObservation
import com.craxiom.networksurvey.board.v1.ClientSource
import com.craxiom.networksurvey.scout.clients.ScoutClientTestData.batch
import com.craxiom.networksurvey.scout.clients.ScoutClientTestData.client
import com.craxiom.networksurvey.scout.clients.ScoutClientTestData.probe
import com.craxiom.networksurvey.scout.model.ScoutRecords
import com.craxiom.networksurvey.scout.wifi.ScoutWifiPipeline
import com.craxiom.networksurvey.scout.wifi.ScoutWifiTestData
import com.craxiom.networksurvey.scout.wifi.ScoutWifiTestData.fix
import com.craxiom.networksurvey.scout.wifi.ScoutWifiTestData.scout
import com.google.protobuf.ByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Wi-Fi clients record path: clients keyed by MAC and probes by MAC and SSID, the strongest per
 * window, time and place of capture, late records, the pre-start drop, and the live snapshot.
 */
class ScoutClientPipelineTest {

    private val pipeline = ScoutClientPipeline().apply { start(scout, START) }

    private fun settings(surveyStartMs: Long? = START, liveViews: Boolean = false) = ScoutWifiPipeline.Settings(
        windowMs = WINDOW, sweepMs = SWEEP, maxFixGapMs = 30_000, surveyStartMs = surveyStartMs,
        liveViews = liveViews, snapshotIntervalMs = 5_000,
    )

    @Test
    fun `a window gives one client record per client MAC, the strongest, placed by the nearest fix`() {
        pipeline.onFix(fix(START + 1_000, latitude = 1.0))
        pipeline.onFix(fix(START + 9_000, latitude = 2.0))
        pipeline.onRecords(batch(START + 2_500, clients = listOf(client(1, -70, START + 2_000), client(2, -61, START + 2_000))), WINDOW)
        pipeline.onRecords(batch(START + 8_500, clients = listOf(client(1, -55, START + 8_000))), WINDOW)

        val clients = pipeline.tick(START + WINDOW + 3_000, settings()).clients.single()

        assertEquals(2, clients.size)
        val strongest = clients.first { it.observation.clientMac == ScoutClientTestData.mac(1) }
        assertEquals(-55, strongest.observation.rssiMax)
        assertEquals(START + 8_000, strongest.captureTimeMs)
        assertEquals(2.0, strongest.fix!!.latitude, 0.0)
        assertEquals(scout.name, strongest.scoutName)
    }

    @Test
    fun `a client record's frame types and BSSID cover the whole window while its signal is the strongest dwell's`() {
        val probing = client(1, -70, START + 2_000, ClientSource.CLIENT_SOURCE_PROBE_REQ_VALUE).toBuilder().setChannel(1).build()
        // The strongest dwell did not learn the access point; a weaker one did
        val strongest = client(1, -50, START + 5_000, ClientSource.CLIENT_SOURCE_DATA_VALUE).toBuilder().clearAssociatedBssid().build()
        val idle = client(1, -66, START + 8_000, ClientSource.CLIENT_SOURCE_NULL_DATA_VALUE).toBuilder().setChannel(11).build()
        pipeline.onRecords(batch(START + 8_500, clients = listOf(probing, strongest, idle)), WINDOW)

        val heard = pipeline.tick(START + WINDOW + 3_000, settings()).clients.single().single()
        val data = ScoutClientRecords.client(heard, "8C2E1F7A", "", 1)!!.data

        assertEquals(listOf(ClientFrameType.DATA, ClientFrameType.NULL_DATA, ClientFrameType.PROBE_REQUEST), data.frameTypesList)
        assertEquals("e0:63:da:41:7c:22", data.bssid)
        assertEquals(-50f, data.signalStrength.value, 0f)
        assertEquals(36, data.channel.value)
        assertEquals(START + 5_000, heard.captureTimeMs)
    }

    @Test
    fun `merging keeps the strongest dwell's BSSID and allocates nothing when the other adds nothing`() {
        val kept = client(1, -50, START, ClientSource.CLIENT_SOURCE_DATA_VALUE)
        val other = client(1, -60, START, ClientSource.CLIENT_SOURCE_DATA_VALUE).toBuilder().setAssociatedBssid(ScoutClientTestData.mac(9)).build()

        assertSame(kept, ScoutClientFields.withWindowOf(kept, other))
        val noBssid = kept.toBuilder().clearAssociatedBssid().build()
        assertSame(noBssid, ScoutClientFields.withWindowOf(noBssid, noBssid.toBuilder().setRssiMax(-70).build()))
    }

    @Test
    fun `probes are one per client and network name per window`() {
        pipeline.onRecords(
            batch(
                START + 2_500,
                probes = listOf(probe(1, "eduroam", -70, START + 2_000), probe(1, "HOME-5G", -66, START + 2_000), probe(2, "eduroam", -60, START + 2_000)),
            ),
            WINDOW,
        )
        pipeline.onRecords(batch(START + 5_500, probes = listOf(probe(1, "eduroam", -50, START + 5_000))), WINDOW)

        val probes = pipeline.tick(START + WINDOW + 3_000, settings()).probes.single()

        assertEquals(3, probes.size)
        assertEquals(-50, probes.first { it.observation.clientMac == ScoutClientTestData.mac(1) && it.observation.ssid.toStringUtf8() == "eduroam" }.observation.rssiMax)
    }

    @Test
    fun `nothing leaves before its window closes, and each window is emitted once`() {
        pipeline.onRecords(batch(START + 2_500, clients = listOf(client(1, -60, START + 2_000))), WINDOW)

        assertTrue(pipeline.tick(START + WINDOW - 1, settings()).clients.isEmpty())
        assertEquals(1, pipeline.tick(START + WINDOW + 3_000, settings()).clients.size)
        // A late observation of an emitted window is dropped rather than duplicated
        pipeline.onRecords(batch(START + WINDOW + 4_000, clients = listOf(client(1, -40, START + 3_000))), WINDOW)
        assertTrue(pipeline.tick(START + WINDOW + 6_000, settings()).clients.isEmpty())
    }

    @Test
    fun `records heard before the source or the survey started are dropped`() {
        pipeline.onRecords(batch(START + 1_000, clients = listOf(client(1, -60, START - 60_000))), WINDOW)
        pipeline.onRecords(batch(START + 6_000, clients = listOf(client(2, -60, START + 5_000))), WINDOW)
        pipeline.onRecords(batch(START + 12_000, probes = listOf(probe(3, "eduroam", -60, START + 11_000))), WINDOW)

        val output = pipeline.tick(START + 40_000, settings(surveyStartMs = START + 10_000))

        assertTrue(output.clients.isEmpty())
        assertEquals(ScoutClientTestData.mac(3), output.probes.single().single().observation.clientMac)
    }

    @Test
    fun `a backlog is saved with its own time and place, marked late, and kept out of the live list`() {
        for (second in 0..10) pipeline.onFix(fix(START + second * 10_000L, latitude = second.toDouble()))
        val heard = START + 41_000
        pipeline.onRecords(batch(START + 90_000, clients = listOf(client(1, -60, heard))), WINDOW)

        val output = pipeline.tick(START + 95_000, settings(liveViews = true))
        val heardClient = output.clients.single().single()

        assertEquals(heard, heardClient.captureTimeMs)
        assertEquals(4.0, heardClient.fix!!.latitude, 0.0)
        assertTrue(heardClient.late)
        assertTrue(output.snapshot!!.clients.isEmpty())
    }

    @Test
    fun `the live snapshot merges a client's frames and the networks it looked for`() {
        val flags = ClientSource.CLIENT_SOURCE_DATA_VALUE or ClientSource.CLIENT_SOURCE_NULL_DATA_VALUE
        pipeline.onRecords(
            batch(
                START + 2_500,
                clients = listOf(client(1, -51, START + 2_000, flags)),
                probes = listOf(probe(1, "eduroam", -58, START + 2_200), probe(1, "", -58, START + 2_300), probe(2, "HOME-5G", -72, START + 2_100)),
            ),
            WINDOW,
        )

        val snapshot = pipeline.tick(START + 3_000, settings(liveViews = true)).snapshot!!

        assertEquals(scout.name, snapshot.scoutName)
        assertEquals(listOf("3e:7b:20:d4:91:01", "3e:7b:20:d4:91:02"), snapshot.clients.map { it.mac })
        val connected = snapshot.client("3E:7B:20:D4:91:01")!!
        assertEquals("e0:63:da:41:7c:22", connected.bssid)
        assertEquals(listOf(ClientFrameType.DATA, ClientFrameType.NULL_DATA, ClientFrameType.PROBE_REQUEST), connected.frameTypes)
        // A wildcard probe names no network
        assertEquals(listOf("eduroam"), connected.probedSsids)
        assertTrue(connected.locallyAdministered)
        assertEquals(listOf(connected), snapshot.connectedTo("E0:63:DA:41:7C:22"))
        assertNull(snapshot.client("3e:7b:20:d4:91:02")!!.bssid)
    }

    @Test
    fun `the snapshot waits for its interval, needs a live view, and forgets clients not heard for a while`() {
        pipeline.onRecords(batch(START + 1_500, clients = listOf(client(1, -60, START + 1_000))), WINDOW)

        assertNull(pipeline.tick(START + 2_000, settings(liveViews = false)).snapshot)
        assertEquals(1, pipeline.tick(START + 2_000, settings(liveViews = true)).snapshot!!.clients.size)
        assertNull(pipeline.tick(START + 4_000, settings(liveViews = true)).snapshot)

        val later = START + 1_000 + ScoutClientPipeline.MIN_LIVE_RETENTION_MS + 1
        assertTrue(pipeline.tick(later, settings(liveViews = true)).snapshot!!.clients.isEmpty())
    }

    @Test
    fun `without a live view the live list still forgets clients, so a long survey does not grow it`() {
        pipeline.onRecords(batch(START + 1_500, clients = listOf(client(1, -60, START + 1_000)), probes = listOf(probe(2, "x", -60, START + 1_000))), WINDOW)
        pipeline.tick(START + 2_000, settings())
        assertEquals(2, pipeline.liveCount)

        pipeline.tick(START + 1_000 + ScoutClientPipeline.MIN_LIVE_RETENTION_MS + 1, settings())

        assertEquals(0, pipeline.liveCount)
    }

    @Test
    fun `a probe for an excluded SSID never leaves as a record but stays on the live list`() {
        val excluding = ScoutClientPipeline({ it == "HOME-5G" }).apply { start(scout, START) }
        excluding.onRecords(batch(START + 2_500, probes = listOf(probe(1, "HOME-5G", -60, START + 2_000), probe(1, "eduroam", -60, START + 2_000))), WINDOW)

        assertEquals(1, excluding.peekAll(settings()).probes.single().size)
        val output = excluding.tick(START + WINDOW + 3_000, settings(liveViews = true))

        assertEquals("eduroam", output.probes.single().single().observation.ssid.toStringUtf8())
        assertEquals(listOf("HOME-5G", "eduroam"), output.snapshot!!.clients.single().probedSsids)
    }

    @Test
    fun `a window with only excluded probes gives nothing`() {
        val excluding = ScoutClientPipeline({ it == "HOME-5G" }).apply { start(scout, START) }
        excluding.onRecords(batch(START + 2_500, probes = listOf(probe(1, "HOME-5G", -60, START + 2_000))), WINDOW)

        assertTrue(excluding.drainAll(settings()).probes.isEmpty())
    }

    @Test
    fun `a client of an access point beaconing an excluded SSID is left out until the SSID comes off the list`() {
        var excludedSsid = "Home"
        val excluding = ScoutClientPipeline({ it == excludedSsid }).apply { start(scout, START) }
        val home = ScoutWifiTestData.ap(1, -60, START + 1_000).toBuilder()
            .setBssid(ScoutClientTestData.apBssid).setSsid(ByteString.copyFromUtf8("Home")).build()
        val unrelated = client(2, -60, START + 2_000).toBuilder().setAssociatedBssid(ScoutClientTestData.mac(9)).build()

        fun batchWithHome(receivedAt: Long, clients: List<ClientObservation>) =
            ScoutRecords(scout.scoutId, receivedAt, true, listOf(home), clients, emptyList(), 0)

        excluding.onRecords(batchWithHome(START + 2_500, listOf(client(1, -60, START + 2_000), unrelated)), WINDOW)

        val kept = excluding.drainAll(settings()).clients.single().single()
        assertEquals(ScoutClientTestData.mac(2), kept.observation.clientMac)

        // Off the list: the next beacon clears it, and the client's next window is saved
        excludedSsid = ""
        excluding.onRecords(batchWithHome(START + WINDOW + 2_500, listOf(client(1, -60, START + WINDOW + 2_000))), WINDOW)
        assertEquals(ScoutClientTestData.mac(1), excluding.drainAll(settings()).clients.single().single().observation.clientMac)
    }

    @Test
    fun `a stop drains what is held and a clear forgets it`() {
        pipeline.onRecords(batch(START + 2_500, clients = listOf(client(1, -60, START + 2_000)), probes = listOf(probe(1, "x", -60, START + 2_000))), WINDOW)

        val peeked = pipeline.peekAll(settings())
        assertEquals(1, peeked.clients.size)
        val drained = pipeline.drainAll(settings())
        assertEquals(1, drained.clients.size)
        assertEquals(1, drained.probes.size)
        assertFalse(pipeline.drainAll(settings()).clients.isNotEmpty())
    }

    private companion object {
        const val START = 1_792_000_020_000L
        const val WINDOW = 15_000L
        const val SWEEP = 14_000L
    }
}
