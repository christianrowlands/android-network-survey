package com.craxiom.networksurvey.scout.clients

import com.craxiom.messaging.wifi.ClientFrameType

/**
 * One Wi-Fi client an NS Scout heard recently, for the live Clients list, Client details
 * and an access point's "Connected clients". Built from client and probe observations together, so
 * a phone that only probes shows up too.
 *
 * @property mac The client's MAC address, lowercase `aa:bb:cc:dd:ee:ff`.
 * @property locallyAdministered True when the address has the locally administered bit set,
 *                               usually a randomized address.
 * @property rssi The strongest signal of the client's latest dwell, dBm.
 * @property lastHeardMs When it was last heard, Unix epoch milliseconds (phone clock).
 * @property channel The channel it was last heard on, null when unknown.
 * @property bssid The access point it talks to, null when unknown (a client that only probes).
 * @property frameTypes The kinds of frames it was heard sending, in protocol order.
 * @property probedSsids The network names it looked for, newest last; wildcard probes are not listed.
 */
data class ScoutClient(
    val mac: String,
    val locallyAdministered: Boolean,
    val rssi: Int,
    val lastHeardMs: Long,
    val channel: Int?,
    val bssid: String?,
    val frameTypes: List<ClientFrameType>,
    val probedSsids: List<String>,
)

/**
 * Every Wi-Fi client an NS Scout heard recently, for live screens only (never saved or sent).
 *
 * @property scoutName The Scout's name, "NS-ABCD".
 * @property clients   The clients, strongest first.
 * @property atMs      When the snapshot was taken, Unix epoch milliseconds.
 */
data class ScoutClientSnapshot(val scoutName: String, val clients: List<ScoutClient>, val atMs: Long) {
    /** @return The client with [mac], or null when it was not heard recently. */
    fun client(mac: String): ScoutClient? = clients.firstOrNull { it.mac.equals(mac, ignoreCase = true) }

    /** @return The clients heard talking to [bssid], strongest first. */
    fun connectedTo(bssid: String): List<ScoutClient> = clients.filter { it.bssid.equals(bssid, ignoreCase = true) }

    companion object {
        /** No Scout clients (the phone is the Wi-Fi source, or nothing was heard yet). */
        @JvmField
        val EMPTY = ScoutClientSnapshot("", emptyList(), 0L)
    }
}
