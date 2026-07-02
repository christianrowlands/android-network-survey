package com.craxiom.networksurvey.services.watchlist

import android.content.Context
import androidx.preference.PreferenceManager
import com.craxiom.messaging.WatchlistEntryUpdate
import com.craxiom.messaging.WatchlistEntryUpdateData
import com.craxiom.messaging.watchlist.WatchlistChangeType
import com.craxiom.networksurvey.BuildConfig
import com.craxiom.networksurvey.constants.WatchlistMessageConstants
import com.craxiom.networksurvey.listeners.IWatchlistListener
import com.craxiom.networksurvey.logging.db.SurveyDatabase
import com.craxiom.networksurvey.logging.db.model.WatchlistEntryEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.function.Supplier
import androidx.core.content.edit

/**
 * Publishes the user's Wi-Fi watchlist (for example, over MQTT) so a fleet backend can track each
 * device's current watchlist.
 * <p>
 * Every message is a SNAPSHOT carrying the device's full authoritative list of entries. One is published
 * when the MQTT connection is established (driven by the connection-established callback, so it also
 * fires on a silent auto-reconnect) and whenever the watchlist changes. A device that has never had a
 * watchlist entry publishes nothing at all; consumers treat a device with no messages as having an empty
 * watchlist. A message published while the connection is down may be queued or dropped at the MQTT layer;
 * either way the backend is healed by the snapshot from the next change or (re)connect, because a
 * consumer only keeps the entries list from the highest messageSequence it has seen for the device.
 * <p>
 * Instances are created and started when a broker connection with watchlist streaming is established and
 * stopped on disconnect. The database read and the sequence mint happen inside a single process-wide
 * critical section so a message's sequence order always matches the order its state was read in, even
 * across publisher instances (a publish in flight when an instance is stopped either completes or is
 * cancelled before it mints a sequence).
 */
class WatchlistChangePublisher(
    private val context: Context,
    private val deviceId: String,
    private val missionIdProvider: Supplier<String?>,
    private val listener: IWatchlistListener
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val watchlistDao by lazy { SurveyDatabase.getInstance(context).watchlistDao() }
    private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(context) }

    /** Begin observing the watchlist, publishing a fresh snapshot on every change. */
    fun start() {
        scope.launch {
            // The emitted value is only a change trigger. The state is re-read inside the publish
            // critical section so the sequence order always matches the state-read order.
            watchlistDao.observeAll().collect { publishCurrentState() }
        }
    }

    /** Stop observing and release resources. */
    fun stop() {
        scope.cancel()
    }

    /**
     * Publish the full current watchlist as an authoritative SNAPSHOT. Call this when the MQTT connection
     * is established (including on auto-reconnect). Watchlist changes publish through the same path.
     */
    fun publishSnapshot() {
        scope.launch { publishCurrentState() }
    }

    private suspend fun publishCurrentState() {
        publishMutex.withLock {
            try {
                val current = watchlistDao.getAll()
                // A never-used watchlist stays silent: an empty snapshot from a device that has never
                // published carries no information (consumers treat "no messages" as an empty watchlist),
                // and most devices never use the feature. Once a sequence has been minted, empty
                // snapshots always publish so a missed clear-all is healed on the next (re)connect.
                if (current.isEmpty() && prefs.getLong(PREF_MESSAGE_SEQUENCE, 0L) == 0L) {
                    return@withLock
                }
                listener.onWatchlistEntryUpdate(buildUpdate(current))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Swallow so a failed read or publish can never kill the observeAll collector (or the
                // process); the next change or (re)connect snapshot heals the backend.
                Timber.e(e, "Failed to publish the watchlist snapshot")
            }
        }
    }

    private fun buildUpdate(entries: List<WatchlistEntryEntity>): WatchlistEntryUpdate {
        val dataBuilder = WatchlistEntryUpdateData.newBuilder()
            .setDeviceSerialNumber(deviceId)
            .setDeviceTime(WatchlistProtoMapper.rfc3339(System.currentTimeMillis()))
            .setMissionId(missionIdProvider.get() ?: "")
            .setMessageSequence(nextSequence())
            .setChangeType(WatchlistChangeType.SNAPSHOT)
            .addAllEntries(entries.map { WatchlistProtoMapper.toProtoEntry(it) })

        return WatchlistEntryUpdate.newBuilder()
            .setVersion(BuildConfig.MESSAGING_API_VERSION)
            .setMessageType(WatchlistMessageConstants.WATCHLIST_ENTRY_UPDATE_MESSAGE_TYPE)
            .setData(dataBuilder)
            .build()
    }

    /**
     * Return the next device-global watchlist message sequence. Seeded from wall-clock epoch millis on
     * first use and persisted synchronously, so it does not regress across app restarts or reinstalls
     * (provided the device clock has not moved backwards across a reinstall). Always called from within
     * the process-wide publish critical section, so it is never concurrent.
     */
    private fun nextSequence(): Long {
        val last = prefs.getLong(PREF_MESSAGE_SEQUENCE, 0L)
        val next = if (last == 0L) System.currentTimeMillis() else last + 1
        prefs.edit(commit = true) { putLong(PREF_MESSAGE_SEQUENCE, next) }
        return next
    }

    private companion object {
        const val PREF_MESSAGE_SEQUENCE = "watchlist_mqtt_message_sequence"

        /**
         * Serializes the state read, sequence mint, and publish across ALL publisher instances so a
         * message's sequence order always matches its state-read order. Process-wide because an old
         * instance's in-flight publish can overlap a new instance created by a quick reconnect.
         */
        val publishMutex = Mutex()
    }
}
