package com.craxiom.networksurvey.ui.wifi.model


import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The view model for the Wi-Fi spectrum screen.
 */
class WifiSpectrumScreenViewModel : ViewModel() {

    private val _scanRateSeconds = MutableStateFlow(-1)
    val scanRate = _scanRateSeconds.asStateFlow()

    /**
     * Sets the scan rate in seconds.
     */
    fun setScanRateSeconds(scanRateSeconds: Int) {
        _scanRateSeconds.value = scanRateSeconds
    }
}
