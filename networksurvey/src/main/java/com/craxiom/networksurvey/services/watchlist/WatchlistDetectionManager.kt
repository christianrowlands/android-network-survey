package com.craxiom.networksurvey.services.watchlist

import android.content.Context
import androidx.preference.PreferenceManager
import com.craxiom.messaging.WatchlistMatch
import com.craxiom.messaging.WatchlistMatchData
import com.craxiom.networksurvey.BuildConfig
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.constants.WatchlistMessageConstants
import com.craxiom.networksurvey.listeners.IWatchlistListener
import com.craxiom.networksurvey.listeners.IWifiSurveyRecordListener
import com.craxiom.networksurvey.logging.db.SurveyDatabase
import com.craxiom.networksurvey.logging.db.model.WatchlistEntryEntity
import com.craxiom.networksurvey.logging.db.model.WatchlistHitEntity
import com.craxiom.networksurvey.model.WifiRecordWrapper
import com.google.protobuf.FloatValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier

/**
 * Detects when a watched network (a {@link WatchlistEntryEntity}) comes into range and alerts the
 * user. Registered as an {@link IWifiSurveyRecordListener} so it sees every Wi-Fi scan while a
 * survey is running, including SSIDs the user has excluded from logging.
 * <p>
 * Matching is done in memory against a cached snapshot of the enabled entries, so the work done on
 * the shared survey-processing thread is cheap and never blocks; database writes and notifications
 * are dispatched to a background coroutine scope. To avoid alert fatigue, the manager alerts only on
 * an absent-to-present transition and only re-arms an entry after it has been unmatched for an
 * absence window (a single dropped scan is not enough to mark a network absent, because real access
 * points flicker out of individual scan results). A per-entry cooldown is kept as a hard backstop.
 */
class WatchlistDetectionManager(
    private val context: Context,
    private val deviceId: String,
    // Nullable because the supplier is implemented in Java, which cannot enforce non-null returns.
    private val missionIdProvider: Supplier<String?>
) : IWifiSurveyRecordListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val watchlistDao by lazy { SurveyDatabase.getInstance(context).watchlistDao() }
    private val watchlistHitDao by lazy { SurveyDatabase.getInstance(context).watchlistHitDao() }

    /**
     * Publishes match records (for example, over MQTT), or null when watchlist streaming is not active.
     * Volatile because it is set from the service thread and read from the detection coroutine. The
     * detection manager and MQTT connection have independent lifecycles, so the service (re)attaches
     * this whenever a manager is created or the MQTT connection comes up or down.
     */
    @Volatile
    var matchListener: IWatchlistListener? = null

    private val matchRecordNumber = AtomicInteger(0)

    @Volatile
    private var enabledSnapshot: List<WatchlistEntryEntity> = emptyList()

    private val presenceById = HashMap<Long, Presence>()

    @Volatile
    private var absenceWindowMillis = DEFAULT_ABSENCE_WINDOW_SECONDS * 1000L

    /**
     * Per-entry presence tracking used by the absent-to-present transition logic.
     */
    private class Presence {
        var present = false
        var lastSeenMillis = 0L
        var lastAlertMillis = 0L
    }

    override fun wantsExcludedRecords(): Boolean = true

    /**
     * Begin keeping the enabled-entry snapshot current and prune stale history. Call when the
     * manager is registered as a Wi-Fi listener.
     */
    fun start() {
        refreshConfig()
        scope.launch {
            watchlistDao.observeEnabled().collectLatest { entries ->
                enabledSnapshot = entries
                val enabledIds = entries.map { it.id }.toSet()
                synchronized(presenceById) { presenceById.keys.retainAll(enabledIds) }
            }
        }
        scope.launch { pruneHistory() }
    }

    /**
     * Stop tracking and release resources. Call when the manager is unregistered.
     */
    fun stop() {
        scope.cancel()
        synchronized(presenceById) { presenceById.clear() }
    }

    /**
     * Re-read the absence window from preferences. Call when the relevant preference changes.
     */
    fun refreshConfig() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val seconds = prefs.getInt(
            NetworkSurveyConstants.PROPERTY_WATCHLIST_ABSENCE_WINDOW_SECONDS,
            DEFAULT_ABSENCE_WINDOW_SECONDS
        )
        absenceWindowMillis = seconds.coerceAtLeast(MIN_ABSENCE_WINDOW_SECONDS) * 1000L
    }

    override fun onWifiBeaconSurveyRecords(wifiBeaconRecords: MutableList<WifiRecordWrapper>?) {
        val records = wifiBeaconRecords ?: return
        val entries = enabledSnapshot
        if (entries.isEmpty()) return

        val now = System.currentTimeMillis()
        for (entry in entries) {
            val match = findMatch(entry, records)
            val presence =
                synchronized(presenceById) { presenceById.getOrPut(entry.id) { Presence() } }
            if (match != null) {
                val wasPresent = presence.present
                presence.lastSeenMillis = now
                presence.present = true
                if (!wasPresent) {
                    val cooldownMs = entry.cooldownSeconds.coerceAtLeast(0) * 1000L
                    if (now - presence.lastAlertMillis >= cooldownMs) {
                        presence.lastAlertMillis = now
                        recordHitAndNotify(entry, match, now)
                    }
                }
            } else if (presence.present && now - presence.lastSeenMillis >= absenceWindowMillis) {
                presence.present = false
            }
        }
    }

    /**
     * Find the first record in the scan batch that satisfies the entry's match rule, or null.
     */
    private fun findMatch(
        entry: WatchlistEntryEntity,
        records: List<WifiRecordWrapper>
    ): MatchInfo? {
        val ssid = entry.ssid?.trim()?.takeIf { it.isNotEmpty() }
        val bssid = entry.bssid?.trim()?.takeIf { it.isNotEmpty() }
        if (ssid == null && bssid == null) return null

        for (wrapper in records) {
            val data = wrapper.wifiBeaconRecord.data
            val recordSsid = data.ssid ?: ""
            val recordBssid = data.bssid ?: ""

            if (WatchlistMatcher.matches(entry, recordSsid, recordBssid)) {
                val matchedField = if (ssid != null) {
                    WatchlistHitEntity.MATCHED_FIELD_SSID
                } else {
                    WatchlistHitEntity.MATCHED_FIELD_BSSID
                }
                val signalStrength =
                    if (data.hasSignalStrength()) data.signalStrength.value else null
                val hasLocation = data.latitude != 0.0 || data.longitude != 0.0
                return MatchInfo(
                    ssid = recordSsid,
                    bssid = recordBssid,
                    signalStrength = signalStrength,
                    latitude = if (hasLocation) data.latitude else null,
                    longitude = if (hasLocation) data.longitude else null,
                    altitude = data.altitude,
                    accuracy = data.accuracy,
                    locationAge = data.locationAge,
                    speed = data.speed,
                    matchedField = matchedField
                )
            }
        }
        return null
    }

    private fun recordHitAndNotify(entry: WatchlistEntryEntity, match: MatchInfo, now: Long) {
        val label = entry.label ?: match.ssid
        scope.launch {
            try {
                val hit = WatchlistHitEntity().apply {
                    entryId = entry.id
                    this.label = label
                    matchedField = match.matchedField
                    ssid = match.ssid
                    bssid = match.bssid
                    rssi = match.rssi
                    latitude = match.latitude
                    longitude = match.longitude
                    timestamp = now
                }
                watchlistHitDao.insert(hit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to record watchlist hit")
            }

            publishMatch(entry, match, label, now)

            WatchlistNotificationHelper.showWatchlistNotification(
                context,
                entry.id,
                label,
                match.rssi
            )
        }
    }

    /**
     * Build and hand a match record to the [matchListener] (for example, the MQTT connection) when
     * watchlist streaming is active. A publish failure is logged and swallowed so it can never
     * interfere with recording the hit or showing the notification.
     */
    private fun publishMatch(
        entry: WatchlistEntryEntity,
        match: MatchInfo,
        label: String,
        now: Long
    ) {
        val listener = matchListener ?: return
        try {
            listener.onWatchlistMatch(buildMatch(entry, match, label, now))
        } catch (e: Exception) {
            Timber.e(e, "Failed to publish watchlist match")
        }
    }

    /**
     * Assemble a [WatchlistMatch] from the matched entry and the observed network. The device
     * name is intentionally left unset here; the MQTT connection injects the effective device name at
     * publish time, exactly as it does for every other record type.
     */
    private fun buildMatch(
        entry: WatchlistEntryEntity,
        match: MatchInfo,
        label: String,
        now: Long
    ): WatchlistMatch {
        val dataBuilder = WatchlistMatchData.newBuilder()
            .setDeviceSerialNumber(deviceId)
            .setDeviceTime(WatchlistProtoMapper.rfc3339(now))
            .setMissionId(missionIdProvider.get() ?: "")
            .setRecordNumber(matchRecordNumber.incrementAndGet())
            .setAltitude(match.altitude)
            .setAccuracy(match.accuracy)
            .setLocationAge(match.locationAge)
            .setSpeed(match.speed)
            .setEntryUuid(entry.uuid ?: "")
            .setLabel(label)
            .setMatchType(WatchlistProtoMapper.toMatchType(entry.matchType))
            .setMatchedField(WatchlistProtoMapper.toMatchField(match.matchedField))
            .setSsid(match.ssid)
            .setBssid(match.bssid)
        match.latitude?.let { dataBuilder.latitude = it }
        match.longitude?.let { dataBuilder.longitude = it }
        match.signalStrength?.let { dataBuilder.signalStrength = FloatValue.of(it) }

        return WatchlistMatch.newBuilder()
            .setVersion(BuildConfig.MESSAGING_API_VERSION)
            .setMessageType(WatchlistMessageConstants.WATCHLIST_MATCH_MESSAGE_TYPE)
            .setData(dataBuilder)
            .build()
    }

    private suspend fun pruneHistory() {
        try {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val days = prefs.getInt(
                NetworkSurveyConstants.PROPERTY_WATCHLIST_HISTORY_RETENTION_DAYS,
                DEFAULT_HISTORY_RETENTION_DAYS
            )
            if (days <= 0) return
            val cutoff = System.currentTimeMillis() - days.toLong() * MILLIS_PER_DAY
            val deleted = watchlistHitDao.pruneOlderThan(cutoff)
            if (deleted > 0) Timber.d("Pruned %d old watchlist hits", deleted)
        } catch (e: Exception) {
            Timber.e(e, "Failed to prune watchlist history")
        }
    }

    private class MatchInfo(
        val ssid: String,
        val bssid: String,
        val signalStrength: Float?,
        val latitude: Double?,
        val longitude: Double?,
        val altitude: Float,
        val accuracy: Int,
        val locationAge: Int,
        val speed: Float,
        val matchedField: String
    ) {
        /** The signal strength truncated to a whole dBm for the history row, or 0 when unavailable. */
        val rssi: Int get() = signalStrength?.toInt() ?: 0
    }

    companion object {
        const val DEFAULT_ABSENCE_WINDOW_SECONDS = 60
        const val DEFAULT_COOLDOWN_SECONDS = 900
        const val DEFAULT_HISTORY_RETENTION_DAYS = 90
        private const val MIN_ABSENCE_WINDOW_SECONDS = 5
        private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
    }
}
