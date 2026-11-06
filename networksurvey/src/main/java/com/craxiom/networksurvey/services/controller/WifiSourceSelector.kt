package com.craxiom.networksurvey.services.controller

import com.craxiom.networksurvey.scout.model.WifiSource

/** The answer to a request to change where Wi-Fi comes from. */
enum class WifiSourceSwitch {
    /** The new source is selected, and runs now if Wi-Fi was running. */
    SWITCHED,

    /** It already was the source. */
    UNCHANGED,

    /** A survey is recording; one Mission never mixes Wi-Fi from two radios. */
    REJECTED_RECORDING,

    /** The Scout was asked for but none is paired. */
    REJECTED_NO_SCOUT,
}

/**
 * Keeps exactly one [WifiScanSource] in charge: the selected one runs while Wi-Fi is wanted, and
 * a switch stops the old one before the new one starts, carrying over a pause. Callers hold the
 * Wi-Fi controller's lock around every call, so a switch never races a start, stop or pause.
 */
class WifiSourceSelector(
    private val sources: Map<WifiSource, WifiScanSource>,
    initial: WifiSource,
) {
    /** The selected source. */
    @Volatile
    var source: WifiSource = initial
        private set

    /** True while something needs Wi-Fi, whether or not it is paused. */
    @Volatile
    var isActive: Boolean = false
        private set

    /** True while scanning is paused for battery or queue backpressure. */
    var isPaused: Boolean = false
        private set

    /** The selected source's scanner. */
    val current: WifiScanSource get() = sources.getValue(source)

    /** @return True when this started the selected source, false when it already ran. */
    fun start(): Boolean {
        if (isActive) return false
        isActive = true
        current.start()
        if (isPaused) current.pause()
        return true
    }

    fun stop() {
        if (!isActive) return
        isActive = false
        current.stop()
    }

    fun pause() {
        if (isPaused) return
        isPaused = true
        if (isActive) current.pause()
    }

    fun resume() {
        if (!isPaused) return
        isPaused = false
        if (isActive) current.resume()
    }

    /**
     * A switch the user asked for: refused while [recording], and the Scout only when [scoutPaired].
     */
    fun request(newSource: WifiSource, recording: Boolean, scoutPaired: Boolean): WifiSourceSwitch = when {
        newSource == source -> WifiSourceSwitch.UNCHANGED
        recording -> WifiSourceSwitch.REJECTED_RECORDING
        newSource == WifiSource.SCOUT && !scoutPaired -> WifiSourceSwitch.REJECTED_NO_SCOUT
        else -> {
            apply(newSource)
            WifiSourceSwitch.SWITCHED
        }
    }

    /**
     * Makes [newSource] the source without the checks, for a change the Scout manager made itself
     * (pairing selects the Scout, removing it selects the phone). @return True when it changed.
     */
    fun apply(newSource: WifiSource): Boolean {
        if (newSource == source) return false
        if (isActive) current.stop()
        source = newSource
        if (isActive) {
            current.start()
            if (isPaused) current.pause()
        }
        return true
    }

    /** Stops whatever runs and releases every source, when the service is destroyed. */
    fun destroy() {
        stop()
        sources.values.forEach { it.destroy() }
    }
}
