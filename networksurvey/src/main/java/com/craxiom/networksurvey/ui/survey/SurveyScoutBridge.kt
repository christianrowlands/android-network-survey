package com.craxiom.networksurvey.ui.survey

import android.app.Application
import com.craxiom.networksurvey.scout.ScoutManager
import com.craxiom.networksurvey.scout.ScoutPolicy
import com.craxiom.networksurvey.scout.ScoutWifiStatuses
import com.craxiom.networksurvey.scout.model.ScoutState
import com.craxiom.networksurvey.scout.model.WifiSource
import com.craxiom.networksurvey.services.controller.WifiSourceSwitch
import com.craxiom.networksurvey.ui.survey.model.SurveyEvent
import com.craxiom.networksurvey.ui.survey.model.SurveyInputs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The Survey screen's link to the NS Scout: what its row and plates show, a status link while the
 * screen is in front, and the Wi-Fi source switches ("Use NS-ABCD", "Use phone Wi-Fi instead" and
 * Undo), which always go through the service so a survey that records is never switched.
 *
 * @param emit Shows a snackbar event.
 * @param gateway The bound service, null while unbound.
 */
internal class SurveyScoutBridge(
    private val application: Application,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val emit: (SurveyEvent) -> Unit,
    private val gateway: () -> SurveyServiceGateway?,
) {
    private val manager by lazy { ScoutManager.getInstance(application) }

    /** False when the organization doesn't allow NS Scout: every Scout surface is hidden then. */
    private val allowed = MutableStateFlow(true)

    /** Owns the status link while the screen is in front and the Scout is the source. */
    private val linkOwner = Any()
    private var linkJob: Job? = null

    /** The Scout's state and when it was last heard; no Scout when the organization doesn't allow one. */
    fun inputs(): Flow<Pair<ScoutState, Long?>> = combine(manager.state, manager.linkDetails, allowed) { state, details, ok ->
        if (ok) state to details.lastHeardAtMs else ScoutState.NotPaired to null
    }

    /** @return [inputs] with the Scout's state and what the running survey gets from it. */
    fun apply(inputs: SurveyInputs, scout: Pair<ScoutState, Long?>): SurveyInputs {
        val source = if (scout.first.scout == null) WifiSource.PHONE else manager.wifiSource.value
        return inputs.copy(
            scout = scout.first,
            scoutLastHeardAtMs = scout.second,
            scoutWifi = ScoutWifiStatuses.of(scout.first, source, inputs.runtime.sessionStartTimeMs, inputs.nowMs),
        )
    }

    /**
     * While resumed, asks for a status link whenever the Scout is the Wi-Fi source (so the row shows
     * Connected and the battery before Start) and lets it go when this phone is the source. Also
     * re-reads the organization's `allow_ns_scout`.
     */
    fun onResume() {
        if (linkJob?.isActive == true) return
        linkJob = scope.launch(ioDispatcher) {
            allowed.value = ScoutPolicy.isAllowed(application)
            if (!allowed.value) return@launch
            manager.wifiSource.collect { source ->
                if (source == WifiSource.SCOUT) manager.requestLink(linkOwner) else manager.release(linkOwner)
            }
        }
    }

    /** The screen left: it no longer needs the link. */
    fun onPause() {
        linkJob?.cancel()
        linkJob = null
        manager.release(linkOwner)
    }

    /** Asks the service to make [source] the Wi-Fi source and says what happened. */
    fun request(source: WifiSource) {
        val service = gateway() ?: return
        // Read now, on the caller's thread, so the switch itself is the only work left for later
        val switched = if (source == WifiSource.PHONE) SurveyEvent.WifiFromPhone else SurveyEvent.WifiFromScout(manager.pairedScout.value?.name.orEmpty())
        scope.launch(ioDispatcher) {
            when (service.requestWifiSource(source)) {
                WifiSourceSwitch.SWITCHED -> emit(switched)

                WifiSourceSwitch.REJECTED_RECORDING -> emit(SurveyEvent.WifiSourceLocked)
                WifiSourceSwitch.UNCHANGED, WifiSourceSwitch.REJECTED_NO_SCOUT -> Unit
            }
        }
    }
}
