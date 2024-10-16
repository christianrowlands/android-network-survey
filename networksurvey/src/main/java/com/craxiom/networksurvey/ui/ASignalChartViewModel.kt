package com.craxiom.networksurvey.ui


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.craxiom.networksurvey.ui.wifi.model.MAX_WIFI_RSSI
import com.craxiom.networksurvey.ui.wifi.model.MIN_WIFI_RSSI
import com.patrykandpatrick.vico.core.model.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.model.LineCartesianLayerModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.concurrent.Volatile
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

const val UNKNOWN_RSSI = -200f
private const val CHART_WIDTH = 120
private const val UPDATE_FREQUENCY = 1000L

/**
 * Abstract base class for the view model for a signal chart.
 */
abstract class ASignalChartViewModel : ViewModel() {

    internal val modelProducer = CartesianChartModelProducer.build()

    private val _markerList = MutableStateFlow<List<Int>>(emptyList())
    val markerList: StateFlow<List<Int>> = _markerList

    private val _scanRateSeconds = MutableStateFlow(-1)
    val scanRate = _scanRateSeconds.asStateFlow()

    private val _maxRssi = MutableStateFlow(MAX_WIFI_RSSI)

    /**
     * The maximum RSSI value to display in the chart. This will be the top end of the chart and
     * values outside this range will be reduced to this value.
     */
    val maxRssi = _maxRssi.asStateFlow()
    private val _minRssi = MutableStateFlow(MIN_WIFI_RSSI)

    /**
     * The minimum RSSI value to display in the chart. This will be the bottom end of the chart and
     * values outside this range will be increased to this value.
     */
    val minRssi = _minRssi.asStateFlow()

    // This is the RSSI that is displayed in the details header. It is not necessarily the same
    // as the RSSI that is displayed in the chart because on the chart we have to limit the range
    // of the RSSI values.
    private val _rssi = MutableStateFlow(UNKNOWN_RSSI)
    val rssiFlow = _rssi.asStateFlow()

    private val xValueQueue: ArrayDeque<Int> by dequeLimiter(CHART_WIDTH)
    private val rssiQueue: ArrayDeque<Float> by dequeLimiter(CHART_WIDTH)

    @Volatile
    private var isPaused = false

    private val _latestChartRssi = MutableStateFlow(UNKNOWN_RSSI)
    private val latestChartRssi: StateFlow<Float> = _latestChartRssi.asStateFlow()

    private var unknownRssiCount = 0
    private val lastXValue: Int
        get() = xValueQueue.lastOrNull() ?: 0

    init {
        viewModelScope.launch(Dispatchers.Main) {
            while (currentCoroutineContext().isActive) {
                // This coroutine will make sure that the chart is updated every n seconds, even
                // if there are not any new values coming in.
                if (!isPaused && !rssiQueue.isEmpty()) {
                    addRssiToChart(latestChartRssi.value)
                }

                delay(UPDATE_FREQUENCY)
            }
        }
    }

    /**
     * Pauses updates to the chart so that the chart does not keep getting values added when the
     * fragment is in the background.
     */
    fun pauseChartUpdates() {
        isPaused = true
    }

    /**
     * Resumes updates to the chart.
     */
    fun resumeChartUpdates() {
        isPaused = false
    }

    /**
     * The label to use for the marker text on the chart. If this is an empty string, then the
     * default marker text is displayed (the y-axis value). Override this method to provide a
     * custom marker label.
     */
    open fun getMarkerLabel(): String {
        return ""
    }

    fun addMarker() {
        _markerList.value = _markerList.value + lastXValue
    }

    /**
     * Sets the scan rate in seconds.
     */
    fun setScanRateSeconds(scanRateSeconds: Int) {
        _scanRateSeconds.value = scanRateSeconds
    }

    /**
     * Sets the maximum RSSI value to display in the chart. This will be the top end of the chart and
     * values outside this range will be reduced to this value.
     */
    fun setMaxRssi(maxRssi: Float) {
        _maxRssi.value = maxRssi
    }

    /**
     * Sets the minimum RSSI value to display in the chart. This will be the bottom end of the chart and
     * values outside this range will be increased to this value.
     */
    fun setMinRssi(minRssi: Float) {
        _minRssi.value = minRssi
    }

    /**
     * Clears the chart of all data and resets the stored RSSI values.
     */
    fun clearChart() {
        for (i in 0 until CHART_WIDTH) {
            addRssiToChart(UNKNOWN_RSSI)
        }
        _latestChartRssi.value = UNKNOWN_RSSI
        _rssi.value = UNKNOWN_RSSI
    }

    /**
     * Adds the initial RSSI value to the chart. This is used to make sure that the chart is
     * populated with something when the screen is first shown.
     */
    @Synchronized
    fun addInitialRssi(rssi: Float) {
        if (rssiQueue.isNotEmpty()) {
            Timber.e("The initial RSSI value is being added to the chart, but the chart is not empty")
            return
        }

        for (i in 0 until CHART_WIDTH) {
            addRssiToChart(UNKNOWN_RSSI)
        }

        // Add it two times to trigger something to show on the chart. A single value won't be
        // displayed
        addNewRssi(rssi)
        addRssiToChart(rssi)
        addRssiToChart(rssi)
    }

    @Synchronized
    fun addNewRssi(rssi: Float) {
        var rssiToChart = rssi

        if (rssi == UNKNOWN_RSSI && latestChartRssi.value != UNKNOWN_RSSI) {
            unknownRssiCount++

            if (unknownRssiCount <= 1) {
                // Ignore the first time the RSSI is missing from the scan results since it
                // appears to be a common occurrence where a network is not found in a scan result.
                // even though it is close to the device.
                Timber.i("Ignoring the RSSI value for charting since it is missing from the scan results")
                return
            } else {
                unknownRssiCount = 0
                Timber.i("Resetting the unknown RSSI count")
            }
        }

        if (rssi != UNKNOWN_RSSI) {
            val minRssiValue = minRssi.value
            if (rssi < minRssiValue) {
                rssiToChart = minRssiValue
            } else {
                val maxRssiValue = maxRssi.value
                if (rssi > maxRssiValue) {
                    rssiToChart = maxRssiValue
                }
            }
        }

        _latestChartRssi.value = rssiToChart
        // Display the actual RSSI value in the header, not the limited value that is used on the chart
        _rssi.value = rssi
    }

    @Synchronized
    internal open fun addRssiToChart(rssi: Float) {
        rssiQueue.add(rssi)
        xValueQueue.add(lastXValue + 1)

        val lineLayerModelPartial: LineCartesianLayerModel.Partial =
            LineCartesianLayerModel.partial {
                series(xValueQueue, rssiQueue)
            }

        modelProducer.tryRunTransaction { add(lineLayerModelPartial) }

        // Remove any makers that have moved "off screen"
        xValueQueue.firstOrNull()?.let { xValue ->
            _markerList.value -= xValue
        }
    }
}

fun <E> dequeLimiter(limit: Int): ReadWriteProperty<Any?, ArrayDeque<E>> =
    object : ReadWriteProperty<Any?, ArrayDeque<E>> {

        private var deque: ArrayDeque<E> = ArrayDeque(limit)

        private fun applyLimit() {
            while (deque.size > limit) {
                deque.removeFirst()
            }
        }

        override fun getValue(thisRef: Any?, property: KProperty<*>): ArrayDeque<E> {
            applyLimit()
            return deque
        }

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: ArrayDeque<E>) {
            this.deque = value
            applyLimit()
        }
    }