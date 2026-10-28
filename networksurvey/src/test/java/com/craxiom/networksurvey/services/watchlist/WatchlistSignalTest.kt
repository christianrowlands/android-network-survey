package com.craxiom.networksurvey.services.watchlist

import com.craxiom.networksurvey.logging.db.model.WatchlistHitEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for [WatchlistSignal]: a missing or impossible Wi-Fi signal is stored as
 * [WatchlistHitEntity.RSSI_UNKNOWN] and always reads back as unknown, never as 0 dBm.
 */
class WatchlistSignalTest {

    @Test
    fun missingSignalIsStoredAsUnknownAndReadsBackAsNull() {
        assertEquals(WatchlistHitEntity.RSSI_UNKNOWN, WatchlistSignal.toStoredRssi(null))
        assertNull(WatchlistSignal.dbm(null))
        assertNull(WatchlistSignal.fromStoredRssi(WatchlistHitEntity.RSSI_UNKNOWN))
    }

    @Test
    fun measuredSignalIsTruncatedToAWholeDbm() {
        assertEquals(-63, WatchlistSignal.toStoredRssi(-63.7f))
        assertEquals(-63, WatchlistSignal.dbm(-63.7f))
        assertEquals(-63, WatchlistSignal.fromStoredRssi(-63))
    }

    @Test
    fun aValueThatIsNotNegativeIsNotARealWifiReading() {
        assertNull(WatchlistSignal.dbm(0f))
        assertNull(WatchlistSignal.dbm(-0.4f))
        assertNull(WatchlistSignal.dbm(5f))
        assertEquals(WatchlistHitEntity.RSSI_UNKNOWN, WatchlistSignal.toStoredRssi(5f))
        assertNull(WatchlistSignal.fromStoredRssi(5))
    }

    @Test
    fun hitExtensionReadsTheStoredSignal() {
        assertNull(WatchlistHitEntity().apply { rssi = WatchlistHitEntity.RSSI_UNKNOWN }.signalDbm)
        assertEquals(-71, WatchlistHitEntity().apply { rssi = -71 }.signalDbm)
    }
}
