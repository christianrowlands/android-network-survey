package com.craxiom.networksurvey.services.controller

import com.craxiom.networksurvey.listeners.ILiveScanView
import com.craxiom.networksurvey.survey.model.RecordType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** How open live views set a controller's effective scan interval, and what the screens are told. */
class AControllerLiveViewsTest {

    private val controller = FakeController(configuredMs = 15_000)

    @After
    fun tearDown() {
        LiveScanRates.clear(RecordType.WIFI)
    }

    @Test
    fun withoutLiveViews_theRadioScansAtItsSurveyInterval() {
        assertFalse(controller.hasLiveViews())
        assertEquals(15_000, controller.effectiveScanRateMs)
        assertFalse(controller.isScanningFaster)
    }

    @Test
    fun aLiveView_makesTheRadioScanAtItsShorterInterval() {
        controller.addLiveView(view(5_000))

        assertEquals(5_000, controller.effectiveScanRateMs)
        assertTrue(controller.isScanningFaster)
        // The survey's own interval never changes.
        assertEquals(15_000, controller.scanRateMs)
    }

    @Test
    fun severalLiveViews_useTheShortestInterval() {
        val map = view(5_000)
        val details = view(2_000)
        controller.addLiveView(map)
        controller.addLiveView(details)
        assertEquals(2_000, controller.effectiveScanRateMs)

        controller.removeLiveView(details)
        assertEquals(5_000, controller.effectiveScanRateMs)

        controller.removeLiveView(map)
        assertEquals(15_000, controller.effectiveScanRateMs)
    }

    @Test
    fun aSurveyIntervalAlreadyShorter_isKept() {
        val fast = FakeController(configuredMs = 1_000)
        fast.addLiveView(view(2_000))

        assertEquals(1_000, fast.effectiveScanRateMs)
        assertFalse(fast.isScanningFaster)
    }

    @Test
    fun aViewAskingForNoFasterScans_keepsTheSurveyInterval() {
        // The Bluetooth views open as live views without asking for faster scans.
        controller.addLiveView(object : ILiveScanView {})

        assertTrue(controller.hasLiveViews())
        assertEquals(15_000, controller.effectiveScanRateMs)
    }

    @Test
    fun whenThePlatformRefuses_theSurveyIntervalIsKept() {
        controller.addLiveView(view(5_000))
        controller.fasterAllowed = false

        assertEquals(15_000, controller.effectiveScanRateMs)
        assertFalse(controller.isScanningFaster)
    }

    @Test
    fun addingOrRemovingTwice_changesTheViewsOnce() {
        val details = view(2_000)
        controller.addLiveView(details)
        controller.addLiveView(details)
        assertEquals(1, controller.changes)

        controller.removeLiveView(details)
        controller.removeLiveView(details)
        assertEquals(2, controller.changes)
        assertFalse(controller.hasLiveViews())
    }

    @Test
    fun theScreensAreToldOnlyAFasterInterval() {
        LiveScanRates.publish(RecordType.WIFI, controller)
        assertNull(LiveScanRates.rates.value[RecordType.WIFI])

        controller.addLiveView(view(5_000))
        LiveScanRates.publish(RecordType.WIFI, controller)
        assertEquals(5_000, LiveScanRates.rates.value[RecordType.WIFI])

        LiveScanRates.clear(RecordType.WIFI)
        assertNull(LiveScanRates.rates.value[RecordType.WIFI])
    }

    private fun view(intervalMs: Int) = object : ILiveScanView {
        override fun getLiveScanIntervalMs(): Int = intervalMs
    }

    /** A controller with a fixed survey interval that counts its live view changes. */
    private class FakeController(private val configuredMs: Int) : AController(null, null) {
        var fasterAllowed = true
        var changes = 0

        override fun getScanRateMs(): Int = configuredMs

        override fun canScanFaster(): Boolean = fasterAllowed

        override fun onLiveViewsChanged() {
            changes++
        }
    }
}
