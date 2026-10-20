package com.craxiom.networksurvey.ui.cellular.details

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.craxiom.networksurvey.model.CellularProtocol
import com.craxiom.networksurvey.ui.UNKNOWN_RSSI
import com.craxiom.networksurvey.ui.cellular.CellularChartViewModel
import com.craxiom.networksurvey.ui.cellular.details.model.ChartSample

/**
 * Where the Cellular view's serving cell signal is plotted: one chart per SIM. An interface so the
 * view model tests can record what is plotted without the chart's one second ticker (which would
 * keep a test's scheduler busy for ever). Called on the main thread only.
 */
interface CellularChartSink {
    /** @return The chart of the SIM whose scans carry [subscriptionId], created on first use. */
    fun chartFor(subscriptionId: Int): CellularChartViewModel?

    /** Plots one scan's serving cell, or clears the chart when the scan had no cells ([sample] null). */
    fun plot(subscriptionId: Int, sample: ChartSample?)

    /** Stops or restarts the charts' clock while the screen is away. */
    fun setPaused(paused: Boolean)

    /** Releases every chart. */
    fun clear()
}

/**
 * The charts, each a [CellularChartViewModel] in a store the Cellular view model owns, so they keep
 * their two minutes of history across sub-tab switches and rotation and are released with it.
 */
class StoredCellularCharts : CellularChartSink {
    private val store = ViewModelStore()
    private val provider = ViewModelProvider(store, ViewModelProvider.NewInstanceFactory())
    private val created = mutableSetOf<Int>()
    private var paused = false

    override fun chartFor(subscriptionId: Int): CellularChartViewModel {
        val chart = provider.get(KEY_PREFIX + subscriptionId, CellularChartViewModel::class.java)
        if (created.add(subscriptionId)) {
            chart.addInitialRssi(UNKNOWN_RSSI)
            if (paused) chart.pauseChartUpdates()
        }
        return chart
    }

    override fun plot(subscriptionId: Int, sample: ChartSample?) {
        val chart = chartFor(subscriptionId)
        if (sample == null) {
            chart.setCellularProtocol(CellularProtocol.NONE)
            return
        }
        // A change of technology clears the chart, since the scales differ.
        chart.setCellularProtocol(sample.protocol)
        chart.setMinRssi(sample.minDbm.toFloat())
        chart.setMaxRssi(sample.maxDbm.toFloat())
        // The value goes in before a cell change marker so the marker is drawn on top of it.
        sample.value?.let { chart.addNewRssi(it.toFloat()) }
        sample.cellId?.let(chart::setServingCellId)
    }

    override fun setPaused(paused: Boolean) {
        this.paused = paused
        created.forEach { id ->
            val chart = chartFor(id)
            if (paused) chart.pauseChartUpdates() else chart.resumeChartUpdates()
        }
    }

    override fun clear() {
        store.clear()
        created.clear()
    }

    private companion object {
        const val KEY_PREFIX = "cellular_chart_"
    }
}
