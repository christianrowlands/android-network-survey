package com.craxiom.networksurvey.ui.activesurvey

import com.craxiom.networksurvey.model.TowerKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The running Community databases upload session, and the towers tagged NEW during it, so the
 * Map's serving-cell card can tag its cell NEW.
 *
 * A tower is only checked while new-tower alerts are on and a Community upload runs ([shouldCheck]),
 * so the set only fills in that situation. The service starts a session when the upload starts and
 * ends it when the upload stops; both clear the set, and a check that finishes after its session
 * ended is ignored ([add] returns false).
 *
 * Each session is identified by when it started ([sessionStartedAt]), which the saved finds carry
 * so the New towers page and the notification can tell this session's finds from earlier ones.
 */
object NewTowerRegistry {

    private val mutableNewTowers = MutableStateFlow<Set<TowerKey>>(emptySet())

    @Volatile
    private var startedAt: Long? = null

    /** The towers tagged NEW in this upload session. */
    @JvmStatic
    val newTowers: StateFlow<Set<TowerKey>> = mutableNewTowers.asStateFlow()

    /** Epoch milliseconds the running upload session started, or null when none runs. */
    @JvmStatic
    val sessionStartedAt: Long? get() = startedAt

    /**
     * @return True when the serving cell should be checked against the tower service: new-tower
     * alerts are on and a Community databases upload is running.
     */
    @JvmStatic
    fun shouldCheck(alertsEnabled: Boolean, uploadRunning: Boolean): Boolean = alertsEnabled && uploadRunning

    /**
     * Called when a Community databases upload starts.
     *
     * @return The new session's [sessionStartedAt].
     */
    @JvmStatic
    @JvmOverloads
    @Synchronized
    fun startSession(now: Long = System.currentTimeMillis()): Long {
        startedAt = now
        mutableNewTowers.value = emptySet()
        return now
    }

    /**
     * Called when a Community databases upload stops.
     *
     * @return The ended session's [sessionStartedAt], or null when no session was running.
     */
    @JvmStatic
    @Synchronized
    fun endSession(): Long? {
        val ended = startedAt
        startedAt = null
        mutableNewTowers.value = emptySet()
        return ended
    }

    /**
     * Records [key] as NEW in the session that started at [sessionStartedAt]. Synchronized with the
     * session changes, so a check that finishes after its upload stopped (or after a new upload
     * started) cannot add a tower to a session it was not made in.
     *
     * @return True when [key] was added: that session is still the running one and [key] was not
     * already in the set.
     */
    @JvmStatic
    @Synchronized
    fun add(key: TowerKey, sessionStartedAt: Long): Boolean {
        if (startedAt != sessionStartedAt || key in mutableNewTowers.value) return false
        mutableNewTowers.update { it + key }
        return true
    }

    /**
     * Takes [key] back out of the session that started at [sessionStartedAt], for a find that
     * could not be saved, so the tower is checked in full again the next time the phone connects
     * to it. Does nothing when that session is no longer the running one.
     */
    @JvmStatic
    @Synchronized
    fun remove(key: TowerKey, sessionStartedAt: Long) {
        if (startedAt == sessionStartedAt) mutableNewTowers.update { it - key }
    }
}
