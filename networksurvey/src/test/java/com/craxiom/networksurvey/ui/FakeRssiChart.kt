package com.craxiom.networksurvey.ui

/** Records what a details page plots, without the real chart's one second ticker. */
class FakeRssiChart : RssiChartSink {
    override val chart: ASignalChartViewModel? = null
    var started: Float? = null
    val plotted = mutableListOf<Float>()
    var chartPaused = false
    var cleared = false

    override fun start(rssi: Float) {
        if (started == null) started = rssi
    }

    override fun plot(rssi: Float) {
        plotted += rssi
    }

    override fun setPaused(paused: Boolean) {
        chartPaused = paused
    }

    override fun clear() {
        cleared = true
    }
}
