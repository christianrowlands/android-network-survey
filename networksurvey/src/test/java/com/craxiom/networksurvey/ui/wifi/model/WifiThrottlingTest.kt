package com.craxiom.networksurvey.ui.wifi.model

import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** When the Wi-Fi list warns that Android throttles scans, per Android version. */
class WifiThrottlingTest {

    private val throttled = WifiThrottleInputs(throttleEnabled = true, developerOptionsOn = true, ignored = false)
    private val notThrottled = throttled.copy(throttleEnabled = false)

    @Test
    fun android11AndLater_followTheThrottleSetting() {
        assertEquals(ThrottleWarning(android9 = false, developerOptionsOn = true), warn(Build.VERSION_CODES.R, throttled, gap = null))
        assertNull(warn(Build.VERSION_CODES.R, notThrottled, gap = 999_999))
    }

    @Test
    fun withDeveloperOptionsOff_theWarningSaysHowToReachTheSetting() {
        val warning = warn(36, throttled.copy(developerOptionsOn = false), gap = null)

        assertEquals(ThrottleWarning(android9 = false, developerOptionsOn = false), warning)
    }

    @Test
    fun android9And10_waitForASecondScan() {
        assertNull(warn(Build.VERSION_CODES.P, notThrottled.copy(developerOptionsOn = false), gap = null))
        assertNull(warn(Build.VERSION_CODES.Q, notThrottled, gap = null))
    }

    @Test
    fun android9And10_warnWhenScansArriveMuchSlowerThanTheInterval() {
        assertNull(warn(Build.VERSION_CODES.Q, notThrottled, gap = RATE_MS * WifiThrottling.GAP_FACTOR))
        assertEquals(
            ThrottleWarning(android9 = false, developerOptionsOn = true),
            warn(Build.VERSION_CODES.Q, notThrottled, gap = RATE_MS * WifiThrottling.GAP_FACTOR + 1),
        )
        assertEquals(
            ThrottleWarning(android9 = true, developerOptionsOn = true),
            warn(Build.VERSION_CODES.P, notThrottled, gap = RATE_MS * WifiThrottling.GAP_FACTOR + 1),
        )
    }

    @Test
    fun android10_warnsWhenDeveloperOptionsAreOff_evenWithFastScans() {
        val warning = warn(Build.VERSION_CODES.Q, notThrottled.copy(developerOptionsOn = false), gap = RATE_MS)

        assertEquals(ThrottleWarning(android9 = false, developerOptionsOn = false), warning)
    }

    @Test
    fun android8_neverWarns() {
        assertNull(warn(Build.VERSION_CODES.O_MR1, throttled.copy(developerOptionsOn = false), gap = 999_999))
    }

    @Test
    fun ignoringTheWarning_hidesItOnEveryVersion() {
        val ignored = throttled.copy(developerOptionsOn = false, ignored = true)

        assertNull(warn(36, ignored, gap = null))
        assertNull(warn(Build.VERSION_CODES.Q, ignored, gap = 999_999))
    }

    private fun warn(sdk: Int, inputs: WifiThrottleInputs, gap: Long?) = WifiThrottling.warning(sdk, inputs, gap, RATE_MS)

    private companion object {
        const val RATE_MS = 15_000L
    }
}
