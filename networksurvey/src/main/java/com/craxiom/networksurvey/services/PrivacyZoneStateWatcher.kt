package com.craxiom.networksurvey.services

import com.craxiom.networksurvey.privacy.PrivacyZoneGate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * Tells the survey service when the phone enters or leaves a privacy zone (or moves from one zone to
 * another), so the Survey screen and the notification show Paused at once instead of at the next
 * refresh. A Java-friendly wrapper over [PrivacyZoneGate.state].
 */
class PrivacyZoneStateWatcher(private val gate: PrivacyZoneGate) {
    private var scope: CoroutineScope? = null

    /** Calls [onChange] after every change of the zone the phone is in, off the main thread. */
    @Synchronized
    fun start(onChange: Runnable) {
        if (scope != null) return
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        gate.state.map { it.zone }.distinctUntilChanged().drop(1).onEach { onChange.run() }.launchIn(newScope)
        scope = newScope
    }

    @Synchronized
    fun stop() {
        scope?.cancel()
        scope = null
    }
}
