package com.craxiom.networksurvey.ui.wifi.clients

import com.craxiom.networksurvey.scout.clients.ScoutClient
import com.craxiom.networksurvey.ui.wifi.model.WifiListViewModel

/** The two views of the Clients mode: one row per client, or one row per network name looked for. */
enum class WifiClientsView { BY_CLIENT, BY_NETWORK }

/**
 * A network name the Scout heard clients look for, with the clients that did, strongest first.
 *
 * @property ssid The requested network name, never empty (wildcard probes name no network).
 */
data class ProbedNetwork(val ssid: String, val clients: List<ScoutClient>)

/**
 * What the Clients mode of Signals > Wi-Fi shows.
 *
 * @property available True while an NS Scout is paired and is the Wi-Fi source, the only time the
 *                     Clients mode is offered.
 * @property selected  True when the user picked Clients; it only shows while [available].
 * @property scoutName The Scout's name, "NS-ABCD", for the strip and the empty state.
 * @property clients   Every client heard recently, strongest first.
 * @property networks  The names those clients looked for, most clients first.
 * @property expandedNetworks The network rows opened to list their clients.
 * @property nowMs     The snapshot's time, for "last heard" ages.
 */
data class WifiClientsUiState(
    val available: Boolean = false,
    val selected: Boolean = false,
    val view: WifiClientsView = WifiClientsView.BY_CLIENT,
    val scoutName: String = "",
    val clients: List<ScoutClient> = emptyList(),
    val networks: List<ProbedNetwork> = emptyList(),
    val expandedNetworks: Set<String> = emptySet(),
    val nowMs: Long = 0L,
) {
    /** True when the Clients list replaces the access points. */
    val shown: Boolean get() = available && selected
}

/** Pure mapping for the Clients screens, covered by JVM tests. */
object WifiClientsMapper {

    /** @return The network names [clients] looked for, most clients first, then by name. */
    fun networks(clients: List<ScoutClient>): List<ProbedNetwork> =
        clients.flatMap { client -> client.probedSsids.distinct().map { it to client } }
            .groupBy({ it.first }, { it.second })
            .map { (ssid, lookers) -> ProbedNetwork(ssid, lookers.sortedByDescending { it.rssi }) }
            .sortedWith(compareByDescending<ProbedNetwork> { it.clients.size }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.ssid })

    /**
     * @return The MAC address as TalkBack should read it, octet by octet ("a2, 4f, 91, ...") so it
     * is not read as one long word.
     */
    fun spokenMac(mac: String): String = mac.split(':').joinToString(", ")

    /**
     * @return [clients] in [order] (their MACs, the order on screen), clients not in it after them
     * in their own order, and clients that left dropped. The values are always [clients]'.
     */
    fun inOrder(clients: List<ScoutClient>, order: List<String>): List<ScoutClient> {
        val byMac = clients.associateBy { it.mac }
        val kept = order.mapNotNull { byMac[it] }
        val known = order.toHashSet()
        return kept + clients.filter { it.mac !in known }
    }

    /** @return The name to show for the access point [bssid], from [ssidOf], falling back to the BSSID. */
    fun networkName(bssid: String, ssidOf: (String) -> String?): String = ssidOf(bssid)?.takeIf { it.isNotEmpty() } ?: bssid
}

/** @return The SSID the Wi-Fi list heard for [bssid], or null when it is hidden or not in the list. */
fun WifiListViewModel.ssidOf(bssid: String): String? =
    wrapperFor(bssid)?.wifiBeaconRecord?.data?.ssid?.takeIf { it.isNotEmpty() }
