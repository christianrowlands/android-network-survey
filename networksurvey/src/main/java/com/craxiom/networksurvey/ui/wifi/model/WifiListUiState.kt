package com.craxiom.networksurvey.ui.wifi.model

import com.craxiom.networksurvey.R

/**
 * State rendered by the Wi-Fi list screen.
 *
 * [scanStatusId] is a string resource id so it can be flipped between scanning/paused/disabled
 * without the VM having to hold Context.
 */
data class WifiListUiState(
    val items: List<WifiDisplayItem> = emptyList(),
    val scanNumber: Int = 0,
    val apCount: Int = 0,
    val scanStatusId: Int = R.string.scan_status_scanning,
    val updatesPaused: Boolean = false,
    val mode: WifiListMode = WifiListMode.GROUPED,
    val sortIndex: Int = 0,
)
