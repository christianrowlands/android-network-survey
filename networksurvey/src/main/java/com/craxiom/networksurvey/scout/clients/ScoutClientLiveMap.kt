package com.craxiom.networksurvey.scout.clients

import com.craxiom.messaging.wifi.ClientFrameType
import com.craxiom.networksurvey.board.v1.ClientObservation
import com.craxiom.networksurvey.board.v1.ProbeObservation
import com.craxiom.networksurvey.scout.wifi.ScoutWifiFields

/**
 * What the live screens know about each Wi-Fi client, merged from its client and probe
 * observations: the latest signal and channel, the access point it talks to, every kind of frame
 * it sent and the networks it looked for. The owner calls [prune] every tick, so a long survey in a
 * dense place holds only the clients heard recently, whether or not a live screen is open.
 *
 * Only live (not late) observations are added. Not thread safe; the pipeline synchronizes.
 */
class ScoutClientLiveMap(private val maxProbedSsids: Int = MAX_PROBED_SSIDS) {

    private class Entry(val mac: String) {
        var locallyAdministered = false
        var rssi = 0
        var lastHeardMs = Long.MIN_VALUE
        var channel: Int? = null
        var bssid: String? = null
        val frameTypes = sortedSetOf<ClientFrameType>()
        val probedSsids = LinkedHashSet<String>()

        /** Takes the newest signal and channel. */
        fun heard(timeMs: Long, rssiMax: Int, channelNumber: Int, administered: Boolean) {
            if (timeMs < lastHeardMs) return
            lastHeardMs = timeMs
            rssi = rssiMax
            channel = channelNumber.takeIf { it != 0 }
            locallyAdministered = administered
        }
    }

    private val entries = LinkedHashMap<String, Entry>()

    /** The number of clients known. */
    val size: Int get() = entries.size

    fun addClient(observation: ClientObservation, timeMs: Long) {
        val entry = entryFor(ScoutWifiFields.mac(observation.clientMac)) ?: return
        entry.heard(timeMs, observation.rssiMax, observation.channel, observation.locallyAdministered)
        ScoutWifiFields.mac(observation.associatedBssid).takeIf { it.isNotEmpty() }?.let { entry.bssid = it }
        entry.frameTypes += ScoutClientFields.frameTypes(observation.sourceFlags)
    }

    fun addProbe(observation: ProbeObservation, timeMs: Long) {
        val entry = entryFor(ScoutWifiFields.mac(observation.clientMac)) ?: return
        entry.heard(timeMs, observation.rssiMax, observation.channel, observation.locallyAdministered)
        entry.frameTypes += ClientFrameType.PROBE_REQUEST
        val ssid = ScoutWifiFields.ssid(observation.ssid)
        if (ssid.isNotEmpty()) {
            // Newest last: a repeat moves to the end, and the oldest falls off
            entry.probedSsids.remove(ssid)
            entry.probedSsids += ssid
            if (entry.probedSsids.size > maxProbedSsids) entry.probedSsids.remove(entry.probedSsids.first())
        }
    }

    /** Forgets the clients not heard within [retentionMs] of [nowMs]. */
    fun prune(nowMs: Long, retentionMs: Long) {
        entries.values.removeAll { it.lastHeardMs < nowMs - retentionMs }
    }

    /** @return Every client known, strongest first. */
    fun snapshot(): List<ScoutClient> {
        return entries.values.map { entry ->
            ScoutClient(
                mac = entry.mac,
                locallyAdministered = entry.locallyAdministered,
                rssi = entry.rssi,
                lastHeardMs = entry.lastHeardMs,
                channel = entry.channel,
                bssid = entry.bssid,
                frameTypes = entry.frameTypes.toList(),
                probedSsids = entry.probedSsids.toList(),
            )
        }.sortedByDescending { it.rssi }
    }

    fun clear() = entries.clear()

    private fun entryFor(mac: String): Entry? = if (mac.isEmpty()) null else entries.getOrPut(mac) { Entry(mac) }

    companion object {
        /** The most network names kept per client. */
        const val MAX_PROBED_SSIDS = 16
    }
}
