package com.craxiom.networksurvey.ui.wifi.model

import com.craxiom.networksurvey.survey.engine.SurveyRuntimeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The time between live Wi-Fi scans, which is how Android 9 and 10 reveal their scan throttle.
 * The gap is only measured between scans that ran back to back: it starts over after the list
 * starts or pauses, and whenever the survey pauses or resumes its scans (for low battery or a full
 * MQTT queue), so a pause is not mistaken for throttling. Used on the main thread only (the scan
 * results, the unregister hook and the watch all run there), so its clock needs no lock.
 */
class WifiScanGap(private val clock: () -> Long) {

    private var lastScanAt: Long? = null
    private var watch: Job? = null

    /** Marks a scan. @return The time since the previous one, or null when the gap starts over. */
    fun measure(): Long? {
        val now = clock()
        val gap = lastScanAt?.let { now - it }
        lastScanAt = now
        return gap
    }

    /** Starts the gap over: the next scan measures nothing. */
    fun reset() {
        lastScanAt = null
    }

    /**
     * Starts the gap over each time the survey pauses or resumes, watching [states] in [scope]; null
     * stops watching (no service bound).
     */
    fun watchSurveyPauses(scope: CoroutineScope, states: Flow<SurveyRuntimeState>?) {
        watch?.cancel()
        watch = states?.let { flow ->
            scope.launch {
                flow.map { it.pausedForBattery || it.pausedForBackpressure }
                    .distinctUntilChanged()
                    .drop(1)
                    .collect { reset() }
            }
        }
    }
}
