package com.craxiom.networksurvey.ui.wifi


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.patrykandpatrick.vico.core.model.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.model.LineCartesianLayerModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

private const val CHART_WIDTH = 60
private const val UPDATE_FREQUENCY = 2000L

/**
 * The view model for the Wifi Details screen.
 */
internal class WifiDetailsViewModel : ViewModel() {

    internal val modelProducer = CartesianChartModelProducer.build()

    private val xValueQueue: ArrayDeque<Int> by dequeLimiter(CHART_WIDTH)
    private val rssiQueue: ArrayDeque<Float> by dequeLimiter(CHART_WIDTH)
    private var latestRssi: Float = 0f
    private val lastXValue: Int
        get() = xValueQueue.lastOrNull() ?: 0

    init {
        viewModelScope.launch(Dispatchers.Default) {
            while (currentCoroutineContext().isActive) {
                // This coroutine will make sure that the chart is updated every n seconds, even
                // if there are not any new values coming in.
                val lastValue = latestRssi
                if (lastValue != 0f) {
                    addRssiToChart(lastValue)
                }

                delay(UPDATE_FREQUENCY)
            }
        }
    }

    /**
     * Adds the initial RSSI value to the chart. This is used to make sure that the chart is
     * populated with something when the screen is first shown.
     */
    fun addInitialRssi(rssi: Float) {
        if (rssiQueue.isNotEmpty()) return
        if (rssi == 0f) return

        for (i in 0 until CHART_WIDTH) {
            addRssiToChart(-200f)
        }

        // Add it two times to trigger something to show on the chart. A single value won't be
        // displayed
        addNewRssi(rssi)
        addRssiToChart(rssi)
        addRssiToChart(rssi)
    }

    fun addNewRssi(rssi: Float) {
        if (rssi == 0f) return
        // TODO If the rssi is out of range, bring it in range
        latestRssi = rssi
    }

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
                val removed = deque.removeFirst()
                println("dequeLimiter removed $removed")
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