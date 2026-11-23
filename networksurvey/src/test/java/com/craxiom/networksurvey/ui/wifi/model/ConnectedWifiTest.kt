package com.craxiom.networksurvey.ui.wifi.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** How the connected access point's BSSID is cleaned up and matched against scan results. */
class ConnectedWifiTest {

    @Test
    fun theBssidIsLowercased() {
        assertEquals("02:00:a4:11:f0:21", ConnectedWifi.normalize("02:00:A4:11:F0:21"))
    }

    @Test
    fun aMissingOrHiddenBssid_readsAsNotConnected() {
        assertNull(ConnectedWifi.normalize(null))
        assertNull(ConnectedWifi.normalize(""))
        // What Android reports without the location permission
        assertNull(ConnectedWifi.normalize("02:00:00:00:00:00"))
    }

    @Test
    fun matchingIgnoresCase_andNothingMatchesWithoutAConnection() {
        assertTrue(ConnectedWifi.isConnected("02:00:A4:11:F0:21", "02:00:a4:11:f0:21"))
        assertFalse(ConnectedWifi.isConnected("02:00:A4:11:F0:22", "02:00:a4:11:f0:21"))
        assertFalse(ConnectedWifi.isConnected("02:00:A4:11:F0:21", null))
    }
}
