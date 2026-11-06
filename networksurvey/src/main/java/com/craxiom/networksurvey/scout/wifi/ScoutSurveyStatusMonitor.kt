package com.craxiom.networksurvey.scout.wifi

import android.os.Handler
import com.craxiom.networksurvey.scout.ScoutManager
import com.craxiom.networksurvey.scout.ScoutWifiStatuses
import com.craxiom.networksurvey.scout.model.WifiSource
import com.craxiom.networksurvey.survey.engine.SurveyRuntimeState
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.survey.status.ScoutWifiStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import java.util.function.Supplier

/**
 * Keeps the survey service honest about the NS Scout: the notification is refreshed when Wi-Fi
 * from the Scout stops or starts being recorded (nothing else in the service changes at that
 * moment) and when a survey's 30 second start grace ends, and the stall watchdog learns that a
 * quiet record counter during a Scout outage is expected.
 *
 * @param wifiSource Where Wi-Fi comes from right now.
 * @param surveyStartMs When the survey session started, null when none runs.
 * @param refresh Rebuilds the notification; posted to [handler].
 */
class ScoutSurveyStatusMonitor @JvmOverloads constructor(
    private val manager: ScoutManager,
    private val handler: Handler,
    private val wifiSource: Supplier<WifiSource>,
    private val surveyStartMs: Supplier<Long?>,
    private val refresh: Runnable,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private var scope: CoroutineScope? = null
    private val graceRefresh = Runnable { refresh.run() }

    @Synchronized
    fun start() {
        if (scope != null) return
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        manager.state
            .map { ScoutWifiStatuses.outage(it) }
            .distinctUntilChanged()
            .drop(1)
            .onEach { handler.post(refresh) }
            .launchIn(newScope)
        scope = newScope
    }

    @Synchronized
    fun stop() {
        scope?.cancel()
        scope = null
        handler.removeCallbacks(refresh)
        handler.removeCallbacks(graceRefresh)
    }

    /**
     * @return The Scout as the survey's Wi-Fi source right now, or null when the phone is. Also
     * schedules a refresh for when the start grace ends, since the outage only shows after it.
     */
    fun status(): ScoutWifiStatus? {
        val now = clock()
        val start = surveyStartMs.get()
        val status = ScoutWifiStatuses.of(manager.state.value, wifiSource.get(), start, now)
        if (status != null) {
            ScoutWifiStatuses.graceEndsAtMs(start, now)?.let { end ->
                handler.removeCallbacks(graceRefresh)
                handler.postDelayed(graceRefresh, end - now + GRACE_REFRESH_MARGIN_MS)
            }
        }
        return status
    }

    /** @return True while [runtime] records Wi-Fi and the Scout, its source, keeps it out. */
    fun isWifiOutage(runtime: SurveyRuntimeState): Boolean =
        runtime.recordTypes.any { it == RecordType.WIFI || it.needsScout } && status()?.outage != null

    companion object {
        /** A little past the grace, so the refresh sees it as over. */
        const val GRACE_REFRESH_MARGIN_MS = 250L
    }
}
