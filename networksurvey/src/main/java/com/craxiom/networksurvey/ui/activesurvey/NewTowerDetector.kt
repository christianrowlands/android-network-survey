package com.craxiom.networksurvey.ui.activesurvey

import android.content.Context
import com.craxiom.networksurvey.logging.db.NewTowerFindStore
import com.craxiom.networksurvey.logging.db.model.NewTowerFindEntity
import com.craxiom.networksurvey.model.CellularRecordWrapper
import com.craxiom.networksurvey.model.TowerKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Checks the serving cell of a survey that sends to the Community databases against the tower
 * service, saves the towers it does not have and alerts on them. Java code in the survey service
 * calls [check] and [endSession]; the work runs off the calling thread.
 *
 * Each check belongs to the upload session that was running when [check] was called. When that
 * session is no longer the running one by the time the answer comes back (the upload stopped, or
 * stopped and started again), the answer is dropped: nothing is saved, alerted on or cached.
 *
 * The order of a check:
 * 1. A tower that is already saved alerts never again. Its last sighting moves forward and, when it
 *    was not in the tower database at its last check and that check is older than
 *    [NewTowerFindStore.RECHECK_AGE_MS], it is checked again silently. While it is still not in
 *    the tower database it is tagged NEW on the Map for this session.
 * 2. Otherwise the tower cache, then the tower service ([TowerDetectionManager.checkIfTowerIsNew]).
 * 3. A tower the service does not have is tagged ([NewTowerRegistry.add]), saved, cached
 *    ([TowerDetectionManager.rememberChecked]) and alerted on. When the find cannot be saved it is
 *    untagged and not cached, so it is checked in full again.
 *
 * A check that fails (no answer from the service) is not retried here: the survey only checks a
 * serving cell when it changes, so the tower is asked about again the next time the phone connects
 * to it after being on another cell, or in the next survey.
 *
 * All work runs on one serial dispatcher. The step from [NewTowerRegistry.add] to the alert does
 * not suspend, so the end-of-session summary ([endSession]) can never run in the middle of it and
 * always counts a find that was alerted on.
 */
class NewTowerDetector internal constructor(
    private val manager: TowerDetectionManager,
    private val store: NewTowerFindStore,
    private val alert: (newest: List<NewTowerFindEntity>, sessionCount: Int) -> Unit,
    private val scope: CoroutineScope = serialScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    constructor(context: Context) : this(
        TowerDetectionManager(context),
        NewTowerFindStore.from(context),
        { newest, count -> NewTowerNotificationHelper.showNewTowerNotification(context.applicationContext, newest, count) },
    )

    /**
     * Checks the serving cell [key] in the background, for the upload session running now. Does
     * nothing when no session is running.
     *
     * @param servingCell The serving-cell record, for the details saved with a new find.
     */
    fun check(key: TowerKey, servingCell: CellularRecordWrapper) {
        val sessionStartedAt = NewTowerRegistry.sessionStartedAt ?: return
        scope.launch { process(key, servingCell, sessionStartedAt) }
    }

    /**
     * Runs one check for the upload session that started at [sessionStartedAt]; see the class
     * documentation for the order.
     */
    internal suspend fun process(key: TowerKey, servingCell: CellularRecordWrapper, sessionStartedAt: Long) {
        try {
            if (isStale(sessionStartedAt)) return

            val saved = store.find(key)
            if (saved != null) {
                resight(key, saved, sessionStartedAt)
                return
            }

            if (!manager.checkIfTowerIsNew(key)) return

            if (isStale(sessionStartedAt)) return
            // No suspension from the tag to the alert; see the class documentation. A tower already
            // tagged this survey but cleared from the list lands here with no saved row and no
            // cache entry, so it is asked about again on each reconnection until the survey
            // stops. That is rare and bounded by serving cell changes, and caching it here would
            // keep it from alerting again in a later survey, as the Clear list copy promises.
            if (!NewTowerRegistry.add(key, sessionStartedAt)) {
                Timber.d("Ignoring a new tower found outside its upload session, or already tagged: %s", key)
                return
            }
            if (!store.recordFind(NewTowerFindFactory.create(key, servingCell, sessionStartedAt, clock()))) {
                NewTowerRegistry.remove(key, sessionStartedAt)
                return
            }

            Timber.i("New tower detected: %s", key)
            alert(store.newestForSession(sessionStartedAt, NewTowerNotificationHelper.MAX_LINES),
                store.countForSession(sessionStartedAt))
            manager.rememberChecked(key)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Error checking whether %s is a new tower", key)
        }
    }

    /** @return True, after logging, when the session the check was made in is no longer running. */
    private fun isStale(sessionStartedAt: Long): Boolean {
        val stale = NewTowerRegistry.sessionStartedAt != sessionStartedAt
        if (stale) Timber.d("Dropping a tower check from an upload session that has ended")
        return stale
    }

    /** A saved tower is the serving cell again: never alerts, may re-check silently. */
    private suspend fun resight(key: TowerKey, saved: NewTowerFindEntity, sessionStartedAt: Long) {
        val now = clock()
        store.touch(saved.id, now)

        var inDatabase = saved.inDatabase
        if (!inDatabase && saved.lastCheckedAt < now - NewTowerFindStore.RECHECK_AGE_MS) {
            inDatabase = store.recordLookup(saved.id, manager.lookUp(key), clock())
        }
        // Tag it NEW on the Map while it is still not in the tower database
        if (!inDatabase) NewTowerRegistry.add(key, sessionStartedAt)
    }

    companion object {
        /** One task at a time, so a find and the end-of-session summary never interleave. */
        private val serialScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

        /**
         * Ends the upload session: the Map stops tagging towers NEW, and the alert is replaced by a
         * silent summary of the session's finds, or removed when it found none. Does nothing when
         * no session was running, so a summary already shown at Stop survives the service's
         * destruction.
         */
        @JvmStatic
        fun endSession(context: Context) {
            val ended = NewTowerRegistry.endSession() ?: return
            val appContext = context.applicationContext
            serialScope.launch {
                try {
                    val count = NewTowerFindStore.from(appContext).countForSession(ended)
                    NewTowerNotificationHelper.showSessionSummary(appContext, count)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // The survey service must never crash over the summary (the CDR build has no
                    // crash reporting), so a failure only costs the summary
                    Timber.e(e, "Failed to show the new tower session summary")
                }
            }
        }
    }
}
