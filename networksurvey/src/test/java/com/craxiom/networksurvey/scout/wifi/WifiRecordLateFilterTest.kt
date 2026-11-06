package com.craxiom.networksurvey.scout.wifi

import com.craxiom.messaging.WifiBeaconRecord
import com.craxiom.messaging.WifiBeaconRecordData
import com.craxiom.networksurvey.model.WifiRecordWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** Late (backlog) Scout records are kept out of BeaconDB and watchlist alerts. */
class WifiRecordLateFilterTest {
    private fun record(bssid: String, late: Boolean) = WifiRecordWrapper(
        WifiBeaconRecord.newBuilder().setData(WifiBeaconRecordData.newBuilder().setBssid(bssid)).build(), "", false, null, late,
    )

    @Test
    fun `late records are left out, and a list without any is passed through`() {
        val live = listOf(record("a", false), record("b", false))
        assertSame(live, WifiRecordWrapper.withoutLate(live))

        val mixed = listOf(record("a", false), record("b", true))
        assertEquals(listOf("a"), WifiRecordWrapper.withoutLate(mixed).map { it.wifiBeaconRecord.data.bssid })
    }
}
