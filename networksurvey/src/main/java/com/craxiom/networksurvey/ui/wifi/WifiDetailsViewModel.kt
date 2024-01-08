package com.craxiom.networksurvey.ui.wifi


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.craxiom.networksurvey.model.WifiNetwork
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
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

const val UNKNOWN_RSSI = -200f
const val MAX_WIFI_RSSI = -20f
const val MIN_WIFI_RSSI = -100f
private const val CHART_WIDTH = 60
private const val UPDATE_FREQUENCY = 2000L

/**
 * The view model for the Wifi Details screen.
 */
internal class WifiDetailsViewModel : ViewModel() {

    internal val modelProducer = CartesianChartModelProducer.build()

    lateinit var wifiNetwork: WifiNetwork

    // This is the RSSI that is displayed in the details header. It is not necessarily the same
    // as the RSSI that is displayed in the chart because on the chart we have to limit the range
    // of the RSSI values.
    private val _rssi = MutableStateFlow(UNKNOWN_RSSI)
    val rssiFlow = _rssi.asStateFlow()

    private val xValueQueue: ArrayDeque<Int> by dequeLimiter(CHART_WIDTH)
    private val rssiQueue: ArrayDeque<Float> by dequeLimiter(CHART_WIDTH)

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
                if (!rssiQueue.isEmpty()) {
                    addRssiToChart(latestChartRssi.value)
                }

                delay(UPDATE_FREQUENCY)
            }
        }
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

            if (unknownRssiCount <= 2) {
                // Ignore the first couple times the RSSI is missing from the scan results since it
                // appears to be a common occurrence where a network is not found in a scan result.
                // even though it is close to the device.
                Timber.i("Ignoring the RSSI value of $rssi for ${wifiNetwork.ssid} since it is missing from the scan results")
                return
            } else {
                unknownRssiCount = 0
                Timber.i("Resetting the unknown RSSI count for ${wifiNetwork.ssid}")
            }
        }

        if (rssi != UNKNOWN_RSSI) {
            if (rssi < MIN_WIFI_RSSI) {
                rssiToChart = MIN_WIFI_RSSI
            } else if (rssi > MAX_WIFI_RSSI) {
                rssiToChart = MAX_WIFI_RSSI
            }
        }

        _latestChartRssi.value = rssiToChart
        // Display the actual RSSI value in the header, not the limited value that is used on the chart
        _rssi.value = rssi
    }

    @Synchronized
    private fun addRssiToChart(rssi: Float) {
        rssiQueue.add(rssi)
        xValueQueue.add(lastXValue + 1)

        val lineLayerModelPartial: LineCartesianLayerModel.Partial =
            LineCartesianLayerModel.partial {
                series(xValueQueue, rssiQueue)
            }

        modelProducer.tryRunTransaction { add(lineLayerModelPartial) }
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