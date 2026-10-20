package com.craxiom.networksurvey.ui.wifi.model

/**
 * State rendered by the Wi-Fi list screen.
 *
 * [scanState] says what stops a scan, if anything; [throttleWarning] is set while Android throttles
 * scans and the user has not dismissed the warning.
 *
 * [apCount] is the deduped total from the latest scan while [visibleApCount] is the number of
 * APs that pass the display [filter]; the two only differ when the filter is active.
 */
data class WifiListUiState(
    val items: List<WifiDisplayItem> = emptyList(),
    val scanNumber: Int = 0,
    val apCount: Int = 0,
    val visibleApCount: Int = 0,
    val filter: WifiDisplayFilter = WifiDisplayFilter(),
    val scanState: WifiScanState = WifiScanState.READY,
    val throttleWarning: ThrottleWarning? = null,
    val updatesPaused: Boolean = false,
    val mode: WifiListMode = WifiListMode.GROUPED,
    val sortIndex: Int = 0,
)
