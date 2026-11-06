package com.craxiom.networksurvey.logging.db

import com.craxiom.messaging.WifiBeaconRecord
import com.craxiom.messaging.WifiBeaconRecordData
import com.craxiom.networksurvey.model.WifiRecordWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The BeaconDB and OpenCelliD upload gates: the movement threshold, and the per-record location
 * and accuracy check for NS Scout windows, whose records each carry their own fix.
 */
class DbUploadStoreTest {

    private fun wifi(bssid: String, latitude: Double, accuracy: Int) = WifiRecordWrapper(
        WifiBeaconRecord.newBuilder().setData(
            WifiBeaconRecordData.newBuilder().setBssid(bssid).setLatitude(latitude).setLongitude(-102.0).setAccuracy(accuracy)
        ).build(),
        "",
    )

    @Test
    fun `every Wi-Fi record needs its own location with a good accuracy`() {
        val good = wifi("aa:bb:cc:dd:ee:01", 23.0, 8)
        val badLater = wifi("aa:bb:cc:dd:ee:02", 23.001, DbUploadStore.ACCURACY_THRESHOLD_METERS + 1)
        val noFix = wifi("aa:bb:cc:dd:ee:03", 0.0, 8)
        val alsoGood = wifi("aa:bb:cc:dd:ee:04", 23.002, DbUploadStore.ACCURACY_THRESHOLD_METERS)

        assertEquals(listOf(good, alsoGood), DbUploadStore.wellLocated(listOf(good, badLater, noFix, alsoGood)))
    }

    @Test
    fun `a bad first Wi-Fi record does not hide the good ones after it`() {
        val bad = wifi("aa:bb:cc:dd:ee:01", 23.0, 0)
        val good = wifi("aa:bb:cc:dd:ee:02", 23.001, 12)

        assertEquals(listOf(good), DbUploadStore.wellLocated(listOf(bad, good)))
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


