package com.craxiom.networksurvey.ui.activesurvey

import com.craxiom.networksurvey.model.TowerKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The towers found new during the running Community databases upload, so the Map's serving-cell
 * card can tag its cell NEW.
 *
 * A tower is only checked while new-tower alerts are on and a Community upload runs ([shouldCheck]),
 * so the set only fills in that situation. The service starts a session when the upload starts and
 * ends it when the upload stops; both clear the set, and a check that finishes after the end is
 * ignored.
 */
object NewTowerRegistry {

    private val mutableNewTowers = MutableStateFlow<Set<TowerKey>>(emptySet())

    private var active = false

    /** The towers found new in this upload session. */
    @JvmStatic
    val newTowers: StateFlow<Set<TowerKey>> = mutableNewTowers.asStateFlow()

    /**
     * @return True when the serving cell should be checked against the tower service: new-tower
     * alerts are on and a Community databases upload is running.
     */
    @JvmStatic
    fun shouldCheck(alertsEnabled: Boolean, uploadRunning: Boolean): Boolean = alertsEnabled && uploadRunning

    /** Called when a Community databases upload starts. */
    @JvmStatic
    @Synchronized
    fun startSession() {
        active = true
        mutableNewTowers.value = emptySet()
    }

    /** Called when a Community databases upload stops. */
    @JvmStatic
    @Synchronized
    fun endSession() {
        active = false
        mutableNewTowers.value = emptySet()
    }

    /**
     * Records [key] as new, unless no upload session is running. Synchronized with the session
     * changes, so a check that finishes just as the upload stops cannot add a tower after it.
     */
    @JvmStatic
    @Synchronized
    fun add(key: TowerKey) {
        if (!active) return
        mutableNewTowers.update { it + key }
    }
}
