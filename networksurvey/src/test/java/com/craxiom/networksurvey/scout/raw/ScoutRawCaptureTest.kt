package com.craxiom.networksurvey.scout.raw

import com.craxiom.networksurvey.scout.link.ScoutCapture
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The raw frames capture: raw frames only, which turns the Raw subscription and Configure fields on. */
class ScoutRawCaptureTest {

    @Test
    fun `the raw capture asks for raw frames only, with the board's defaults`() {
        val configure = ScoutRawSource.CAPTURE.toConfigureRequest().configure
        assertTrue(configure.rawFrames)
        assertFalse(configure.wifiAp)
        assertFalse(configure.wifiClient)
        assertFalse(configure.wifiProbes)
        assertFalse(configure.ble)
        assertTrue(configure.rawSnaplen == 0 && configure.rawMaxFramesPerSec == 0 && configure.rawTypeMask == 0)
    }

    @Test
    fun `merged with the access point capture both are on, and without it raw frames are off`() {
        val merged = ScoutCapture().merge(ScoutRawSource.CAPTURE)
        assertTrue(merged.rawFrames)
        assertTrue(merged.wifiAccessPoints)
        assertFalse(ScoutCapture().merge(ScoutCapture(wifiClients = true)).rawFrames)
    }
}
