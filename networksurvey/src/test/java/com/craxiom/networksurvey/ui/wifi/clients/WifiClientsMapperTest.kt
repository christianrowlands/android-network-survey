package com.craxiom.networksurvey.ui.wifi.clients

import com.craxiom.messaging.wifi.ClientFrameType
import com.craxiom.networksurvey.scout.clients.ScoutClient
import org.junit.Assert.assertEquals
import org.junit.Test

/** The pure mapping behind the Clients list: networks looked for, spoken addresses, network names. */
class WifiClientsMapperTest {

    @Test
    fun networks_groupTheClientsByNameLookedFor_mostClientsFirst_strongestClientFirst() {
        val a = client("aa:00:00:00:00:01", -70, listOf("eduroam", "HOME-5G"))
        val b = client("aa:00:00:00:00:02", -50, listOf("eduroam"))
        val c = client("aa:00:00:00:00:03", -60, listOf("Cafe", "eduroam", "eduroam"))
        val d = client("aa:00:00:00:00:04", -40, emptyList())

        val networks = WifiClientsMapper.networks(listOf(a, b, c, d))

        assertEquals(listOf("eduroam", "Cafe", "HOME-5G"), networks.map { it.ssid })
        assertEquals(listOf(b, c, a), networks.first().clients)
        assertEquals(listOf(c), networks[1].clients)
    }

    @Test
    fun networks_withoutProbes_isEmpty() {
        assertEquals(emptyList<ProbedNetwork>(), WifiClientsMapper.networks(listOf(client("aa:00:00:00:00:01", -50, emptyList()))))
    }

    @Test
    fun spokenMac_readsTheAddressOctetByOctet() {
        assertEquals("a2, 4f, 91, 3c, 0e, 17", WifiClientsMapper.spokenMac("a2:4f:91:3c:0e:17"))
    }

    @Test
    fun networkName_isTheSsid_orTheBssidWhenUnknownOrHidden() {
        val ssids = mapOf("e0:63:da:41:7c:22" to "Toast", "e0:63:da:41:7c:23" to "")
        assertEquals("Toast", WifiClientsMapper.networkName("e0:63:da:41:7c:22", ssids::get))
        assertEquals("e0:63:da:41:7c:23", WifiClientsMapper.networkName("e0:63:da:41:7c:23", ssids::get))
        assertEquals("e0:63:da:41:7c:24", WifiClientsMapper.networkName("e0:63:da:41:7c:24", ssids::get))
    }

    @Test
    fun inOrder_keepsTheRowsOnScreen_inPlace_withFreshValues_newOnesAfter() {
        val a = client("a", -40, emptyList())
        val b = client("b", -50, emptyList())
        val c = client("c", -60, emptyList())
        // b got stronger, a left, d is new
        val latest = listOf(client("b", -30, emptyList()), c, client("d", -35, emptyList()))

        val shown = WifiClientsMapper.inOrder(latest, listOf(a, c, b).map { it.mac })

        assertEquals(listOf("c", "b", "d"), shown.map { it.mac })
        assertEquals(-30, shown[1].rssi)
    }

    private fun client(mac: String, rssi: Int, probed: List<String>) = ScoutClient(
        mac = mac,
        locallyAdministered = true,
        rssi = rssi,
        lastHeardMs = 1_000L,
        channel = 6,
        bssid = null,
        frameTypes = listOf(ClientFrameType.PROBE_REQUEST),
        probedSsids = probed,
    )
}
