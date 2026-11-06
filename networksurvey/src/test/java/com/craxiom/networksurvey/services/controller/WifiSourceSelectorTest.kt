package com.craxiom.networksurvey.services.controller

import com.craxiom.networksurvey.scout.model.WifiSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One Wi-Fi source at a time: switching with listeners registered stops the old source before the
 * new one starts, a pause carries over, and a switch is refused while recording.
 */
class WifiSourceSelectorTest {

    /** Records every call, in order, across both sources. */
    private val calls = ArrayList<String>()

    private inner class FakeSource(private val name: String) : WifiScanSource {
        var running = false
        override fun start() {
            running = true
            calls += "$name start"
        }

        override fun stop() {
            running = false
            calls += "$name stop"
        }

        override fun pause() {
            calls += "$name pause"
        }

        override fun resume() {
            calls += "$name resume"
        }
    }

    private val phone = FakeSource("phone")
    private val scout = FakeSource("scout")
    private val selector = WifiSourceSelector(mapOf(WifiSource.PHONE to phone, WifiSource.SCOUT to scout), WifiSource.PHONE)

    @Test
    fun `starts the selected source once`() {
        assertTrue(selector.start())
        assertFalse(selector.start())

        assertEquals(listOf("phone start"), calls)
    }

    @Test
    fun `a switch while Wi-Fi runs stops the old source before the new one starts, so they never run together`() {
        selector.start()

        assertEquals(WifiSourceSwitch.SWITCHED, selector.request(WifiSource.SCOUT, recording = false, scoutPaired = true))

        assertEquals(listOf("phone start", "phone stop", "scout start"), calls)
        assertFalse(phone.running)
        assertTrue(scout.running)
        assertEquals(WifiSource.SCOUT, selector.source)
    }

    @Test
    fun `a switch while nothing needs Wi-Fi only selects the source`() {
        selector.request(WifiSource.SCOUT, recording = false, scoutPaired = true)
        assertTrue(calls.isEmpty())

        selector.start()
        assertEquals(listOf("scout start"), calls)
    }

    @Test
    fun `a switch is refused while recording, and the Scout without a paired one`() {
        selector.start()

        assertEquals(WifiSourceSwitch.REJECTED_RECORDING, selector.request(WifiSource.SCOUT, recording = true, scoutPaired = true))
        assertEquals(WifiSourceSwitch.REJECTED_NO_SCOUT, selector.request(WifiSource.SCOUT, recording = false, scoutPaired = false))
        assertEquals(WifiSourceSwitch.UNCHANGED, selector.request(WifiSource.PHONE, recording = true, scoutPaired = true))

        assertEquals(WifiSource.PHONE, selector.source)
        assertEquals(listOf("phone start"), calls)
    }

    @Test
    fun `a pause carries over a switch and a start`() {
        selector.start()
        selector.pause()
        selector.apply(WifiSource.SCOUT)
        selector.resume()

        assertEquals(listOf("phone start", "phone pause", "phone stop", "scout start", "scout pause", "scout resume"), calls)

        calls.clear()
        selector.stop()
        selector.pause()
        selector.start()
        assertEquals(listOf("scout stop", "scout start", "scout pause"), calls)
    }

    @Test
    fun `the manager's own change applies without the checks, and stop stops the current source`() {
        selector.start()
        assertTrue(selector.apply(WifiSource.SCOUT))
        assertFalse(selector.apply(WifiSource.SCOUT))
        selector.stop()

        assertEquals(listOf("phone start", "phone stop", "scout start", "scout stop"), calls)
        assertFalse(selector.isActive)
    }
}
