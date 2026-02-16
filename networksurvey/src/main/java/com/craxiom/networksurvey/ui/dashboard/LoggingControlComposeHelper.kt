package com.craxiom.networksurvey.ui.dashboard

import androidx.compose.ui.platform.ComposeView
import com.craxiom.networksurvey.ui.theme.NsTheme
import java.util.function.Consumer

/**
 * Helper class to set up the logging control Compose views from Java code.
 * This bridges the gap between Java and Kotlin Compose APIs, following the same
 * pattern as [com.craxiom.networksurvey.ui.nsanalytics.NsAnalyticsComposeHelper].
 */
object LoggingControlComposeHelper {

    /**
     * Sets up the logging control card content in a ComposeView.
     * This method can be called from Java code.
     */
    @JvmStatic
    fun setupLoggingCard(
        composeView: ComposeView,
        cellularEnabled: Boolean,
        phoneStateEnabled: Boolean,
        phoneStateAutoStarted: Boolean,
        wifiEnabled: Boolean,
        bluetoothEnabled: Boolean,
        gnssEnabled: Boolean,
        cdrEnabled: Boolean,
        onCellularToggle: Consumer<Boolean>,
        onPhoneStateToggle: Consumer<Boolean>,
        onWifiToggle: Consumer<Boolean>,
        onBluetoothToggle: Consumer<Boolean>,
        onGnssToggle: Consumer<Boolean>,
        onCdrToggle: Consumer<Boolean>,
        onPhoneStateHelpClick: Runnable,
        onCdrHelpClick: Runnable,
    ) {
        composeView.setContent {
            NsTheme {
                LoggingControlContent(
                    cellularEnabled = cellularEnabled,
                    phoneStateEnabled = phoneStateEnabled,
                    phoneStateAutoStarted = phoneStateAutoStarted,
                    wifiEnabled = wifiEnabled,
                    bluetoothEnabled = bluetoothEnabled,
                    gnssEnabled = gnssEnabled,
                    cdrEnabled = cdrEnabled,
                    onCellularToggle = { onCellularToggle.accept(it) },
                    onPhoneStateToggle = { onPhoneStateToggle.accept(it) },
                    onWifiToggle = { onWifiToggle.accept(it) },
                    onBluetoothToggle = { onBluetoothToggle.accept(it) },
                    onGnssToggle = { onGnssToggle.accept(it) },
                    onCdrToggle = { onCdrToggle.accept(it) },
                    onPhoneStateHelpClick = { onPhoneStateHelpClick.run() },
                    onCdrHelpClick = { onCdrHelpClick.run() },
                )
            }
        }
    }
}
