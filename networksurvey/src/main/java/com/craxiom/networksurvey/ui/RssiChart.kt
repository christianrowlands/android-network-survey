package com.craxiom.networksurvey.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore

/**
 * The signal chart of a details page (a Wi-Fi network, a Bluetooth device): dBm from -100 to -20,
 * the defaults of [ASignalChartViewModel].
 */
class RssiChartViewModel : ASignalChartViewModel()

/**
 * Where a details page plots the signal of the one network or device it shows. An interface so the
 * view model tests can record what is plotted without the chart's one second ticker (which would
 * keep a test's scheduler busy for ever). Called on the main thread only.
 */
interface RssiChartSink {
    /** The chart to draw, or null in tests. */
    val chart: ASignalChartViewModel?

    /** Fills the chart's history with "no reading" and then [rssi]; only the first call counts. */
    fun start(rssi: Float)

    /** Plots one scan's reading, or [UNKNOWN_RSSI] when the scan did not hear it. */
    fun plot(rssi: Float)

    /** Stops or restarts the chart's clock while the page is away. */
    fun setPaused(paused: Boolean)

    /** Releases the chart. */
    fun clear()
}

/** The chart in a store its page's view model owns, so it keeps its history across rotation. */
class StoredRssiChart : RssiChartSink {
    private val store = ViewModelStore()
    private val stored = ViewModelProvider(store, ViewModelProvider.NewInstanceFactory())[KEY, RssiChartViewModel::class.java]
    private var started = false

    override val chart: ASignalChartViewModel = stored

    override fun start(rssi: Float) {
        if (started) return
        started = true
        stored.addInitialRssi(rssi)
    }

    override fun plot(rssi: Float) = stored.addNewRssi(rssi)

    override fun setPaused(paused: Boolean) = if (paused) stored.pauseChartUpdates() else stored.resumeChartUpdates()

    override fun clear() = store.clear()

    private companion object {
        const val KEY = "rssi_chart"
    }
}

/**
 * Runs a details chart's one second clock only while its page shows and its radio can scan, so a
 * page that cannot scan stops drawing the last reading as if it were still live.
 *
 * @param canScan Whether the radio can scan when the page opens.
 */
class RssiChartClock(private val sink: RssiChartSink, private var canScan: Boolean) {
    private var pageShowing = false

    init {
        apply()
    }

    /** The page resumed (true) or went away (false). */
    fun setPageShowing(showing: Boolean) {
        pageShowing = showing
        apply()
    }

    /** The radio can scan again (true) or something stops it (false). */
    fun setCanScan(can: Boolean) {
        canScan = can
        apply()
    }

    private fun apply() = sink.setPaused(!pageShowing || !canScan)
}

