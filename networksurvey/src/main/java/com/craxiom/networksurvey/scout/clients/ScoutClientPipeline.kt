package com.craxiom.networksurvey.scout.clients

import androidx.annotation.VisibleForTesting
import com.craxiom.networksurvey.board.v1.ApObservation
import com.craxiom.networksurvey.board.v1.BoardStatus
import com.craxiom.networksurvey.board.v1.ClientObservation
import com.craxiom.networksurvey.board.v1.ProbeObservation
import com.craxiom.networksurvey.scout.ScoutCatchUpTracker
import com.craxiom.networksurvey.scout.model.PairedScout
import com.craxiom.networksurvey.scout.model.ScoutId
import com.craxiom.networksurvey.scout.model.ScoutRecords
import com.craxiom.networksurvey.scout.wifi.PhoneFix
import com.craxiom.networksurvey.scout.wifi.ScoutCaptureTiming
import com.craxiom.networksurvey.scout.wifi.ScoutHeard
import com.craxiom.networksurvey.scout.wifi.ScoutRecordAggregator
import com.craxiom.networksurvey.scout.wifi.ScoutWifiFields
import com.craxiom.networksurvey.scout.wifi.ScoutWifiPipeline
import com.google.protobuf.ByteString

/**
 * The Scout's Wi-Fi clients record path, the counterpart of [ScoutWifiPipeline] for clients and
 * probe requests, without threads so it can be tested. It shares [ScoutCaptureTiming] (time and
 * place of capture, the pre-start drop, the late flag) and [ScoutRecordAggregator] (at most one
 * record per item per window, the strongest observation): clients are keyed by MAC, probes by MAC
 * and requested SSID. A client record's frame types are those of all its observations in the
 * window, and its BSSID comes from any of them when the strongest has none; a probe record's count
 * is the total of its window. Live screens get a [ScoutClientSnapshot] of every client heard recently.
 *
 * The SSID exclusion list works as it does for the phone's scans: nothing about an excluded network
 * leaves the pipeline as a record, while the live screens still show it. A probe for an excluded
 * SSID is left out, and so is a client or probe that names the BSSID of an access point heard
 * beaconing an excluded SSID. A BSSID can only be recognized once its access point has been heard,
 * which takes up to one sweep from the start of a capture.
 *
 * Methods are synchronized: the source mostly calls it from its own thread, and a leaving sink
 * peeks from another.
 *
 * @param isExcludedSsid Whether an SSID is on the user's exclusion list.
 */
class ScoutClientPipeline(
    private val isExcludedSsid: (String) -> Boolean = { false },
    lateThresholdMs: Long = ScoutCatchUpTracker.DEFAULT_LAG_THRESHOLD_MS,
) {

    /** What one tick produced. */
    data class Output(
        val clients: List<List<ScoutHeard<ClientObservation>>>,
        val probes: List<List<ScoutHeard<ProbeObservation>>>,
        val snapshot: ScoutClientSnapshot?,
    )

    /** One observation with the time it was heard. */
    private class Sighting<O>(val observation: O, val captureTimeMs: Long, val late: Boolean)

    /** A probe request is one item per client and requested network name. */
    private data class ProbeKey(val mac: ByteString, val ssid: ByteString)

    private val timing = ScoutCaptureTiming(lateThresholdMs)
    private val clients = ScoutRecordAggregator<ByteString, Sighting<ClientObservation>>(
        keyOf = { it.observation.clientMac },
        strengthOf = { it.observation.rssiMax },
        timeOf = { it.captureTimeMs },
        // Frame types and the BSSID cover every dwell of the window; signal and channel stay the strongest's
        absorb = { kept, other ->
            val merged = ScoutClientFields.withWindowOf(kept.observation, other.observation)
            if (merged === kept.observation) kept else Sighting(merged, kept.captureTimeMs, kept.late)
        },
    )
    private val probes = ScoutRecordAggregator<ProbeKey, Sighting<ProbeObservation>>(
        keyOf = { ProbeKey(it.observation.clientMac, it.observation.ssid) },
        strengthOf = { it.observation.rssiMax },
        timeOf = { it.captureTimeMs },
    )
    private val live = ScoutClientLiveMap()

    /** The access points heard beaconing an excluded SSID. */
    private val excludedBssids = HashSet<ByteString>()
    private var scout: PairedScout? = null
    private var windowMs = 0L
    private var lastSnapshotMs: Long? = null

    /** The clients the live snapshot could list right now. */
    @get:VisibleForTesting
    internal val liveCount: Int
        @Synchronized get() = live.size

    /** Starts a capture for [paired] at [nowMs]; anything heard before is dropped. */
    @Synchronized
    fun start(paired: PairedScout?, nowMs: Long) {
        scout = paired
        timing.start(nowMs)
        lastSnapshotMs = null
        clear()
    }

    /** Keeps the Scout's name and model current. */
    @Synchronized
    fun setScout(paired: PairedScout?) {
        scout = paired
    }

    @Synchronized
    fun onStatus(scoutId: ScoutId, status: BoardStatus, receivedAtMs: Long) = timing.onStatus(scoutId, status, receivedAtMs)

    @Synchronized
    fun onFix(fix: PhoneFix) = timing.onFix(fix)

    /** Adds a batch's client and probe observations, and notes which access points are excluded. */
    @Synchronized
    fun onRecords(records: ScoutRecords, windowMs: Long) {
        if (scout?.scoutId != records.scoutId) return
        records.accessPoints.forEach(::noteAccessPoint)
        for (client in records.clients) {
            // No record can be built without a MAC, so it never takes a record number
            if (!ScoutWifiFields.isMac(client.clientMac)) continue
            val time = timing.captureTimeMs(client.boardTimeMs, records) ?: continue
            val late = timing.isLate(time, records)
            clients.add(Sighting(client, time, late), windowMs)
            if (!late) live.addClient(client, time)
        }
        for (probe in records.probes) {
            if (!ScoutWifiFields.isMac(probe.clientMac)) continue
            val time = timing.captureTimeMs(probe.boardTimeMs, records) ?: continue
            val late = timing.isLate(time, records)
            probes.add(Sighting(probe, time, late), windowMs)
            if (!late) live.addProbe(probe, time)
        }
    }

    /** @return The windows that closed and, when a live screen wants one, a snapshot. */
    @Synchronized
    fun tick(nowMs: Long, settings: ScoutWifiPipeline.Settings): Output {
        timing.noteSurveyStart(settings)
        val closedClients = ArrayList<List<ScoutHeard<ClientObservation>>>()
        val closedProbes = ArrayList<List<ScoutHeard<ProbeObservation>>>()
        if (settings.windowMs != windowMs) {
            // A new scan rate cuts windows differently; emit what the old cut holds.
            if (windowMs != 0L) {
                closedClients += heardClients(clients.drainAll(), settings)
                closedProbes += heardProbes(probes.drainAll(), settings)
            }
            windowMs = settings.windowMs
        }
        closedClients += heardClients(clients.drainClosed(nowMs), settings)
        closedProbes += heardProbes(probes.drainClosed(nowMs), settings)
        // Every tick, not only for a snapshot: without a live screen it would grow for the whole survey
        live.prune(nowMs, liveRetentionMs(settings))
        return Output(closedClients, closedProbes, snapshot(nowMs, settings))
    }

    /** @return Everything still waiting, for a pause or a stop (records from before the survey started stay out). */
    @Synchronized
    fun drainAll(settings: ScoutWifiPipeline.Settings): Output {
        timing.noteSurveyStart(settings)
        return Output(heardClients(clients.drainAll(), settings), heardProbes(probes.drainAll(), settings), snapshot = null)
    }

    /** @return Everything still waiting, left in place, for a sink that is about to leave while others stay. */
    @Synchronized
    fun peekAll(settings: ScoutWifiPipeline.Settings): Output {
        timing.noteSurveyStart(settings)
        return Output(heardClients(clients.peekAll(), settings), heardProbes(probes.peekAll(), settings), snapshot = null)
    }

    /** Forgets what was heard, for a stop. The clock and the fixes stay. */
    @Synchronized
    fun clear() {
        clients.clear()
        probes.clear()
        live.clear()
        excludedBssids.clear()
    }

    /** Keeps [excludedBssids] current, including for an SSID that came off the list during the capture. */
    private fun noteAccessPoint(ap: ApObservation) {
        if (isExcludedSsid(ScoutWifiFields.ssid(ap.ssid))) excludedBssids += ap.bssid else excludedBssids -= ap.bssid
    }

    private fun keepsClient(client: ClientObservation): Boolean = client.associatedBssid !in excludedBssids

    private fun keepsProbe(probe: ProbeObservation): Boolean =
        probe.bssid !in excludedBssids && !isExcludedSsid(ScoutWifiFields.ssid(probe.ssid))

    private fun heardClients(
        windows: List<ScoutRecordAggregator.Window<Sighting<ClientObservation>>>,
        settings: ScoutWifiPipeline.Settings,
    ): List<List<ScoutHeard<ClientObservation>>> = windows.mapNotNull { clients.heard(it, settings, ::keepsClient) }

    private fun heardProbes(
        windows: List<ScoutRecordAggregator.Window<Sighting<ProbeObservation>>>,
        settings: ScoutWifiPipeline.Settings,
    ): List<List<ScoutHeard<ProbeObservation>>> = windows.mapNotNull { probes.heard(it, settings, ::keepsProbe) }

    private fun <K : Any, O> ScoutRecordAggregator<K, Sighting<O>>.heard(
        window: ScoutRecordAggregator.Window<Sighting<O>>,
        settings: ScoutWifiPipeline.Settings,
        keeps: (O) -> Boolean,
    ): List<ScoutHeard<O>>? {
        val paired = scout ?: return null
        val floor = timing.floorMs(settings)
        return window.items.filter { it.captureTimeMs >= floor && keeps(it.observation) }.map { sighting ->
            ScoutHeard(
                observation = sighting.observation,
                scoutName = paired.name,
                captureTimeMs = sighting.captureTimeMs,
                fix = timing.fixFor(sighting.captureTimeMs, settings),
                late = sighting.late,
                slot = slotOf(window, sighting),
            )
        }.takeIf { it.isNotEmpty() }
    }

    private fun snapshot(nowMs: Long, settings: ScoutWifiPipeline.Settings): ScoutClientSnapshot? {
        val last = lastSnapshotMs
        if (!settings.liveViews || (last != null && nowMs - last < settings.snapshotIntervalMs)) return null
        val paired = scout ?: return null
        lastSnapshotMs = nowMs
        return ScoutClientSnapshot(paired.name, live.snapshot(), nowMs)
    }

    private fun liveRetentionMs(settings: ScoutWifiPipeline.Settings): Long =
        maxOf(MIN_LIVE_RETENTION_MS, settings.sweepMs * LIVE_RETENTION_SWEEPS)

    companion object {
        /**
         * Clients send far less often than access points beacon, so the live list keeps a client
         * for at least this long (or two sweeps) after it was last heard.
         */
        const val MIN_LIVE_RETENTION_MS = 60_000L

        private const val LIVE_RETENTION_SWEEPS = 2L
    }
}
