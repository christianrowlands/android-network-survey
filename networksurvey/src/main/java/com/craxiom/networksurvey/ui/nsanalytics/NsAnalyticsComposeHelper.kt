package com.craxiom.networksurvey.ui.nsanalytics

import androidx.compose.ui.platform.ComposeView

/**
 * Helper class to set up Compose views from Java code.
 * This bridges the gap between Java and Kotlin Compose APIs.
 */
object NsAnalyticsComposeHelper {

    /**
     * Sets up the NS Analytics status card in a ComposeView.
     * This method can be called from Java code.
     */
    @JvmStatic
    fun setupNsAnalyticsCard(
        composeView: ComposeView,
        isSurveyActive: Boolean,
        surveyStartTime: Long,
        cellularCount: Int,
        wifiCount: Int,
        bluetoothCount: Int,
        gnssCount: Int,
        phoneStateCount: Int,
        onToggleSurvey: Runnable,
        onOpenDetails: Runnable? = null
    ) {
        composeView.setContent {
            NsAnalyticsStatusCard(
                isSurveyActive = isSurveyActive,
                surveyStartTime = surveyStartTime,
                cellularCount = cellularCount,
                wifiCount = wifiCount,
                bluetoothCount = bluetoothCount,
                gnssCount = gnssCount,
                phoneStateCount = phoneStateCount,
                onToggleSurvey = { onToggleSurvey.run() },
                onOpenDetails = onOpenDetails?.let { { it.run() } },
                showDetailedInfo = false // Dashboard view doesn't need detailed info
            )
        }
    }
}