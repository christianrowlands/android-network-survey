package com.craxiom.networksurvey.services.controller

import com.craxiom.messaging.WifiClientRecord
import com.craxiom.messaging.WifiProbeRequestRecord
import com.craxiom.networksurvey.board.v1.ClientObservation
import com.craxiom.networksurvey.board.v1.ProbeObservation
import com.craxiom.networksurvey.data.SsidExclusionManager
import com.craxiom.networksurvey.listeners.IWifiClientLiveListener
import com.craxiom.networksurvey.listeners.IWifiClientSurveyRecordListener
import com.craxiom.networksurvey.logging.WifiClientCsvLogger
import com.craxiom.networksurvey.logging.WifiProbeCsvLogger
import com.craxiom.networksurvey.scout.ScoutManager
import com.craxiom.networksurvey.scout.clients.ScoutClientHost
import com.craxiom.networksurvey.scout.clients.ScoutClientRecords
import com.craxiom.networksurvey.scout.clients.ScoutClientSnapshot
import com.craxiom.networksurvey.scout.clients.ScoutClientSource
import com.craxiom.networksurvey.scout.model.WifiSource
import com.craxiom.networksurvey.scout.wifi.ScoutHeard
import com.craxiom.networksurvey.scout.wifi.ScoutRecordNumbers
import com.craxiom.networksurvey.scout.wifi.ScoutSourceWatcher
import com.craxiom.networksurvey.services.NetworkSurveyService
import com.craxiom.networksurvey.survey.model.RecordType
import timber.log.Timber
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * The Wi-Fi clients record type: clients and probe requests, which only an NS Scout hears.
 *
 * It owns the record listeners (the survey's sinks: log files, MQTT, gRPC) and the live listeners
 * (the client screens), the two CSV loggers, and the [ScoutClientSource]. The source runs while
 * any listener is registered and the Scout is the Wi-Fi source, the same way a Wi-Fi listener
 * starts Wi-Fi scanning; with the phone as the source nothing runs, so a phone-only user never
 * records or sends Wi-Fi clients. Records are numbered and stamped with the Mission ID here, on
 * the source's thread, and only while a sink takes them: a client screen alone takes no numbers
 * and adds nothing to the survey's record counts. A source that starts during a battery or
 * backpressure pause stays paused.
 */
class WifiClientsController @JvmOverloads constructor(
    private val service: NetworkSurveyService,
    private val wifiController: WifiController,
    private val scoutManager: ScoutManager = ScoutManager.getInstance(service),
    private val clientLogger: WifiClientCsvLogger = WifiClientCsvLogger(service),
    private val probeLogger: WifiProbeCsvLogger = WifiProbeCsvLogger(service),
) : ScoutClientHost {
    private val lock = Any()
    private val sinks = CopyOnWriteArraySet<IWifiClientSurveyRecordListener>()
    private val liveListeners = CopyOnWriteArraySet<IWifiClientLiveListener>()
    private val loggingEnabled = AtomicBoolean(false)
    private val source = ScoutClientSource(scoutManager, this, service.primaryLocationListener)
    private val sourceWatcher = ScoutSourceWatcher(scoutManager)
    private val clientNumbers = ScoutRecordNumbers(AtomicInteger(1))
    private val probeNumbers = ScoutRecordNumbers(AtomicInteger(1))
    private val exclusionList by lazy { SsidExclusionManager(service) }

    @Volatile
    private var destroyed = false

    /** True during a battery or backpressure pause; guarded by [lock]. */
    private var paused = false

    /** Runs the delayed stop; guarded by [lock]. */
    private val stopTimer = ScheduledThreadPoolExecutor(1).apply { setRemoveOnCancelPolicy(true) }
    private var pendingStop: ScheduledFuture<*>? = null

    /** Starts following Wi-Fi source changes, so the source starts and stops with the Scout. */
    fun start() {
        sourceWatcher.start { updateSource() }
    }

    fun onDestroy() {
        synchronized(lock) {
            destroyed = true
            pendingStop?.cancel(false)
            stopTimer.shutdownNow()
            sourceWatcher.stop()
            if (source.isRunning) source.stop()
            source.destroy()
            clientLogger.onDestroy()
            probeLogger.onDestroy()
        }
    }

    /** Applies a changed log rollover size to the client and probe CSV files. */
    fun onRolloverPreferenceChanged() {
        if (destroyed) return
        clientLogger.onSharedPreferenceChanged()
        probeLogger.onSharedPreferenceChanged()
    }

    /** Applies the rollover size an organization changed to the client and probe CSV files. */
    fun onMdmPreferenceChanged() {
        if (destroyed) return
        clientLogger.onMdmPreferenceChanged()
        probeLogger.onMdmPreferenceChanged()
    }

    /** @return True while Wi-Fi clients are logged to CSV files. */
    fun isLoggingEnabled(): Boolean = loggingEnabled.get()

    /** @return True while the Scout captures Wi-Fi clients for someone. */
    fun isInUse(): Boolean = source.isRunning

    /**
     * Turns the client and probe CSV files on or off.
     *
     * @return The new logging state, or null when it could not be changed.
     */
    fun toggleLogging(enable: Boolean): Boolean? = synchronized(lock) {
        if (destroyed) return null
        if (enable == loggingEnabled.get()) return enable
        val result = if (enable) {
            if (clientLogger.enableLogging(true) && probeLogger.enableLogging(true)) {
                loggingEnabled.set(true)
                registerListener(clientLogger)
                registerListener(probeLogger)
                true
            } else {
                clientLogger.enableLogging(false)
                probeLogger.enableLogging(false)
                null
            }
        } else {
            // Unregister first: that hands the loggers the open window while they are still open
            unregisterListener(clientLogger)
            unregisterListener(probeLogger)
            clientLogger.enableLogging(false)
            probeLogger.enableLogging(false)
            loggingEnabled.set(false)
            false
        }
        service.updateServiceNotification()
        service.notifyLoggingChangedListeners()
        result
    }

    fun registerListener(listener: IWifiClientSurveyRecordListener) {
        sinks += listener
        updateSource()
    }

    /** Removes [listener], giving it what the open window holds so far first. */
    fun unregisterListener(listener: IWifiClientSurveyRecordListener) {
        if (listener in sinks) {
            source.peekPending(
                { clients -> buildClients(clients, peek = true).takeIf { it.isNotEmpty() }?.let { notify(listener) { l -> l.onWifiClientRecords(it) } } },
                { probes -> buildProbes(probes, peek = true).takeIf { it.isNotEmpty() }?.let { notify(listener) { l -> l.onWifiProbeRequestRecords(it) } } },
            )
        }
        sinks -= listener
        updateSource()
    }

    fun registerLiveListener(listener: IWifiClientLiveListener) {
        liveListeners += listener
        updateSource()
    }

    fun unregisterLiveListener(listener: IWifiClientLiveListener) {
        liveListeners -= listener
        updateSource()
    }

    /** @return True while any sink or live screen wants Wi-Fi clients. */
    fun hasListeners(): Boolean = sinks.isNotEmpty() || liveListeners.isNotEmpty()

    /** @return True while a survey sink takes Wi-Fi clients and the Scout is the Wi-Fi source. */
    fun isRecording(): Boolean = sinks.isNotEmpty() && isScoutTheWifiSource()

    /** Battery or backpressure pause: the board is told to stop, what is held goes to the sinks. */
    fun pauseScanning() {
        synchronized(lock) {
            paused = true
            if (source.isRunning) source.pause()
        }
    }

    fun resumeScanning() {
        synchronized(lock) {
            paused = false
            if (source.isRunning) source.resume()
        }
    }

    /** @return True when the Scout is paired and is the Wi-Fi source, so Wi-Fi clients can be recorded. */
    fun isScoutTheWifiSource(): Boolean =
        scoutManager.pairedScout.value != null && scoutManager.wifiSource.value == WifiSource.SCOUT

    private fun updateSource() {
        synchronized(lock) { updateSourceLocked() }
    }

    /**
     * Starts the source when it is wanted, and stops it [STOP_GRACE_MS] after it no longer is, so
     * moving from one client screen to the next (the old one leaves before the new one arrives)
     * neither restarts the board's capture nor forgets what the live screens know.
     */
    private fun updateSourceLocked() {
        if (destroyed) return
        val wanted = hasListeners() && isScoutTheWifiSource()
        if (wanted) {
            pendingStop?.cancel(false)
            pendingStop = null
            if (!source.isRunning) {
                source.start()
                if (paused) source.pause()
                Timber.i("Wi-Fi clients capture started")
                service.updateLocationListener()
            }
        } else if (source.isRunning && pendingStop == null) {
            pendingStop = stopTimer.schedule(::stopIfUnwanted, STOP_GRACE_MS, TimeUnit.MILLISECONDS)
        }
    }

    private fun stopIfUnwanted() {
        synchronized(lock) {
            pendingStop = null
            if (destroyed || !source.isRunning || (hasListeners() && isScoutTheWifiSource())) return
            source.stop()
            Timber.i("Wi-Fi clients capture stopped")
            service.updateLocationListener()
        }
    }

    // ScoutClientHost, called on the Scout Wi-Fi clients thread.

    override fun scanRateMs(): Int = wifiController.scanRateMs

    override fun hasLiveViews(): Boolean = liveListeners.isNotEmpty()

    /**
     * A client screen gets a snapshot at least every [LIVE_SNAPSHOT_INTERVAL_MS], or faster when an
     * open Wi-Fi screen asks for it. (Without a Wi-Fi live view the Wi-Fi interval is unbounded, and a
     * Client details page alone would otherwise get only one snapshot.)
     */
    override fun liveViewIntervalMs(): Int = minOf(wifiController.liveViewIntervalMs, LIVE_SNAPSHOT_INTERVAL_MS)

    override fun surveyStartMs(): Long? = service.surveySessionStartTimeMs

    override fun isExcludedSsid(ssid: String): Boolean = exclusionList.isExcluded(ssid)

    override fun onScoutClients(clients: List<ScoutHeard<ClientObservation>>) {
        if (sinks.isEmpty()) return
        val records = buildClients(clients)
        if (records.isEmpty()) return
        service.onRecordsProcessed(RecordType.WIFI_CLIENTS, records.size)
        sinks.forEach { sink -> notify(sink) { it.onWifiClientRecords(records) } }
    }

    override fun onScoutProbes(probes: List<ScoutHeard<ProbeObservation>>) {
        if (sinks.isEmpty()) return
        val records = buildProbes(probes)
        if (records.isEmpty()) return
        service.onRecordsProcessed(RecordType.WIFI_CLIENTS, records.size)
        sinks.forEach { sink -> notify(sink) { it.onWifiProbeRequestRecords(records) } }
    }

    override fun onScoutClientSnapshot(snapshot: ScoutClientSnapshot) {
        liveListeners.forEach { listener ->
            try {
                listener.onWifiClientSnapshot(snapshot)
            } catch (e: Exception) {
                Timber.e(e, "Unable to notify a live Wi-Fi client view")
            }
        }
    }

    /** @param peek True for a leaving sink's early copy of the open window (same numbers as at close). */
    private fun buildClients(clients: List<ScoutHeard<ClientObservation>>, peek: Boolean = false): List<WifiClientRecord> {
        val missionId = service.missionIdForRecords
        return clients.mapNotNull { ScoutClientRecords.client(it, service.surveyDeviceId, missionId, clientNumbers.next(it.slot, peek)) }
    }

    /** @param peek True for a leaving sink's early copy of the open window (same numbers as at close). */
    private fun buildProbes(probes: List<ScoutHeard<ProbeObservation>>, peek: Boolean = false): List<WifiProbeRequestRecord> {
        val missionId = service.missionIdForRecords
        return probes.mapNotNull { ScoutClientRecords.probe(it, service.surveyDeviceId, missionId, probeNumbers.next(it.slot, peek)) }
    }

    private fun notify(listener: IWifiClientSurveyRecordListener, call: (IWifiClientSurveyRecordListener) -> Unit) {
        try {
            call(listener)
        } catch (e: Exception) {
            Timber.e(e, "Unable to notify a Wi-Fi client record listener")
        }
    }

    private companion object {
        /** The slowest the client screens are refreshed, the same 5 s as a live Wi-Fi screen. */
        const val LIVE_SNAPSHOT_INTERVAL_MS = 5_000

        /** How long the capture keeps running after the last listener left. */
        const val STOP_GRACE_MS = 3_000L
    }
}
