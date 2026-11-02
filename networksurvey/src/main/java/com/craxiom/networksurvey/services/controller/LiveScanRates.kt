package com.craxiom.networksurvey.services.controller

import com.craxiom.networksurvey.survey.model.RecordType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The scan interval of each radio that an open live view (see
 * [com.craxiom.networksurvey.listeners.ILiveScanView]) makes scan faster than its survey interval,
 * so the screens can say how often they really update. The controllers publish it; a radio
 * scanning at its survey interval has no entry.
 */
object LiveScanRates {

    private val mutableRates = MutableStateFlow<Map<RecordType, Int>>(emptyMap())

    /** The faster scan interval in milliseconds of each radio scanning faster for a live view. */
    val rates: StateFlow<Map<RecordType, Int>> = mutableRates.asStateFlow()

    /**
     * Publishes [controller]'s current scan interval for [type]: an entry while it scans faster than
     * its survey interval, none otherwise.
     */
    @JvmStatic
    fun publish(type: RecordType, controller: AController) {
        val effectiveMs = controller.effectiveScanRateMs
        val faster = effectiveMs < controller.scanRateMs
        mutableRates.update { if (faster) it + (type to effectiveMs) else it - type }
    }

    /** Removes [type]'s entry, when its controller stops scanning or is destroyed. */
    @JvmStatic
    fun clear(type: RecordType) {
        mutableRates.update { it - type }
    }
}
