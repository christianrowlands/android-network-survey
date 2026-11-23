package com.craxiom.networksurvey.logging.db

import com.craxiom.messaging.WifiBeaconRecord
import com.craxiom.messaging.WifiBeaconRecordData
import com.craxiom.networksurvey.model.WifiRecordWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The BeaconDB and OpenCelliD upload gates: the movement threshold, the per-record location and
 * accuracy check for NS Scout windows, whose records each carry their own fix, and the hidden and
 * opted-out network filter (for the queue and for the community survey point).
 */
class DbUploadStoreTest {

    private fun wifi(bssid: String, latitude: Double, accuracy: Int, ssid: String = "Cafe") = WifiRecordWrapper(
        WifiBeaconRecord.newBuilder().setData(
            WifiBeaconRecordData.newBuilder().setBssid(bssid).setSsid(ssid).setLatitude(latitude).setLongitude(-102.0)
                .setAccuracy(accuracy)
        ).build(),
        "",
    )

    @Test
    fun `every Wi-Fi record needs its own location with a good accuracy`() {
        val good = wifi("aa:bb:cc:dd:ee:01", 23.0, 8)
        val badLater = wifi("aa:bb:cc:dd:ee:02", 23.001, DbUploadStore.ACCURACY_THRESHOLD_METERS + 1)
        val noFix = wifi("aa:bb:cc:dd:ee:03", 0.0, 8)
        val alsoGood = wifi("aa:bb:cc:dd:ee:04", 23.002, DbUploadStore.ACCURACY_THRESHOLD_METERS)

        assertEquals(listOf(good, alsoGood), DbUploadStore.uploadable(listOf(good, badLater, noFix, alsoGood)))
    }

    @Test
    fun `a bad first Wi-Fi record does not hide the good ones after it`() {
        val bad = wifi("aa:bb:cc:dd:ee:01", 23.0, 0)
        val good = wifi("aa:bb:cc:dd:ee:02", 23.001, 12)

        assertEquals(listOf(good), DbUploadStore.uploadable(listOf(bad, good)))
    }

    @Test
    fun `hidden and opted-out networks are never queued for BeaconDB`() {
        val good = wifi("aa:bb:cc:dd:ee:01", 23.0, 8)
        val hidden = wifi("aa:bb:cc:dd:ee:02", 23.0, 8, ssid = "")
        val nulBytes = wifi("aa:bb:cc:dd:ee:03", 23.0, 8, ssid = "\u0000\u0000\u0000")
        val noMap = wifi("aa:bb:cc:dd:ee:04", 23.0, 8, ssid = "Home_nomap")
        val optOut = wifi("aa:bb:cc:dd:ee:05", 23.0, 8, ssid = "Home_optout")

        assertEquals(listOf(good), DbUploadStore.uploadable(listOf(hidden, nulBytes, noMap, optOut, good)))
    }

    @Test
    fun `the community survey point never stands for hidden or opted-out networks`() {
        val good = wifi("aa:bb:cc:dd:ee:01", 23.0, 8)
        val noMap = wifi("aa:bb:cc:dd:ee:02", 23.0, 8, ssid = "Home_nomap")
        val hidden = wifi("aa:bb:cc:dd:ee:03", 23.0, 8, ssid = "")
        // Bad accuracy is the point gate's call, not this filter's
        val coarse = wifi("aa:bb:cc:dd:ee:04", 23.0, DbUploadStore.ACCURACY_THRESHOLD_METERS + 1)

        assertEquals(listOf(good, coarse), DbUploadStore.withoutOptedOut(listOf(noMap, good, hidden, coarse)))
        // A batch of only opted-out networks makes no point, so none can be marked as sent to BeaconDB
        assertTrue(DbUploadStore.withoutOptedOut(listOf(noMap, hidden)).isEmpty())
    }

    @Test
    fun `a randomized BSSID is still queued for BeaconDB`() {
        // 0x02 in the first octet: a locally administered address
        val randomized = wifi("da:a1:19:00:00:01", 23.0, 8)

        assertEquals(listOf(randomized), DbUploadStore.uploadable(listOf(randomized)))
    }

    @Test
    fun `test first record always allows movement`() {
        val result = DbUploadStore.hasMovedEnough(23.0000, -102.0000, null)
        assertTrue("First record should always be allowed.", result)
    }

    @Test
    fun `test small movement does not trigger`() {
        val result = DbUploadStore.hasMovedEnough(23.0000, -102.0000, Pair(23.0001, -102.0001))
        assertFalse("Small movement should not trigger.", result)
    }

    @Test
    fun `test movement beyond threshold triggers`() {
        val result = DbUploadStore.hasMovedEnough(23.0000, -102.0000, Pair(23.0005, -102.0005))
        assertTrue("Movement beyond threshold should trigger.", result)
    }

    @Test
    fun `test exactly at threshold`() {
        val result = DbUploadStore.hasMovedEnough(23.0000, -102.0000, Pair(23.0003, -102.0003))
        assertTrue("Movement at threshold should trigger.", result)
    }

    @Test
    fun `test staying in same location`() {
        val result = DbUploadStore.hasMovedEnough(23.0000, -102.0000, Pair(23.0000, -102.0000))
        assertFalse("No movement should not trigger.", result)
    }

    @Test
    fun `test large movement definitely triggers`() {
        val result = DbUploadStore.hasMovedEnough(23.0000, -102.0000, Pair(23.0500, -102.0500))
        assertTrue("Large movement should trigger.", result)
    }
}


