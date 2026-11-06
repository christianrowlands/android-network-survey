package com.craxiom.networksurvey.ui.wifi.clients

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.craxiom.networksurvey.scout.clients.ScoutClient
import com.craxiom.networksurvey.scout.clients.ScoutClientSnapshot
import com.craxiom.networksurvey.services.NetworkSurveyService
import com.craxiom.networksurvey.ui.RssiChartClock
import com.craxiom.networksurvey.ui.RssiChartSink
import com.craxiom.networksurvey.ui.StoredRssiChart
import com.craxiom.networksurvey.ui.UNKNOWN_RSSI
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the Client details page shows: the client as last heard (kept while it is out of earshot,
 * with [WifiClientDetailsState.heardNow] false), the Scout's name, and the client's signal over
 * time, one point per snapshot. Scoped to the page's navigation entry.
 *
 * The live listener is registered while the page is bound to the service; registering keeps the
 * NS Scout capturing clients while the page shows.
 */
class WifiClientDetailsViewModel internal constructor(
    application: Application,
    private val chartSink: RssiChartSink,
    mainDispatcher: CoroutineDispatcher,
) : AndroidViewModel(application) {

    constructor(application: Application) : this(application, StoredRssiChart(), Dispatchers.Main)

    private var mac: String? = null
    private val feed = WifiClientFeed(viewModelScope, mainDispatcher, ::applySnapshot)
    private val chartClock = RssiChartClock(chartSink, canScan = true)

    private val mutableState = MutableStateFlow(WifiClientDetailsState())
    val state: StateFlow<WifiClientDetailsState> = mutableState.asStateFlow()

    /** The chart to draw (null in tests). */
    val chart = chartSink.chart

    /**
     * Shows the client with [mac], starting from [initial] (what the list showed) when given. Only
     * the first call counts, so a recomposition or rotation keeps the history.
     */
    fun show(mac: String, initial: ScoutClient?) {
        if (this.mac != null) return
        this.mac = mac
        mutableState.value = WifiClientDetailsState(mac = mac, client = initial, heardNow = initial != null)
        chartSink.start(initial?.rssi?.toFloat() ?: UNKNOWN_RSSI)
    }

    /** Called with the bound service, and with null before the page unbinds. */
    fun onServiceChanged(service: NetworkSurveyService?) = feed.onServiceChanged(service, wanted = true)

    /** Stops the chart's clock while the page is away. */
    fun setChartPaused(paused: Boolean) = chartClock.setPageShowing(!paused)

    override fun onCleared() {
        feed.release()
        chartSink.clear()
    }

    private fun applySnapshot(snapshot: ScoutClientSnapshot) {
        val wanted = mac ?: return
        val client = snapshot.client(wanted)
        chartSink.plot(client?.rssi?.toFloat() ?: UNKNOWN_RSSI)
        val current = mutableState.value
        mutableState.value = current.copy(
            client = client ?: current.client,
            heardNow = client != null,
            scoutName = snapshot.scoutName.ifEmpty { current.scoutName },
            nowMs = snapshot.atMs,
        )
    }
}

/**
 * @property client   The client as last heard, null before the page has heard it at all.
 * @property heardNow True when the latest snapshot still has the client.
 * @property nowMs    The latest snapshot's time, for "Last heard".
 */
data class WifiClientDetailsState(
    val mac: String = "",
    val client: ScoutClient? = null,
    val heardNow: Boolean = false,
    val scoutName: String = "",
    val nowMs: Long = 0L,
)
