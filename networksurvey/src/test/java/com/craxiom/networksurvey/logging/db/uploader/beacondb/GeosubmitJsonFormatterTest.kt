package com.craxiom.networksurvey.logging.db.uploader.beacondb

import com.craxiom.networksurvey.logging.db.model.BaseRecordEntity
import com.craxiom.networksurvey.logging.db.model.LteRecordEntity
import com.craxiom.networksurvey.logging.db.model.WifiBeaconRecordEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

/**
 * The BeaconDB (Ichnaea geosubmit v2) report must carry the time the record was observed: the
 * spec defines `timestamp` as "the time of observation of the data, measured in milliseconds since
 * the UNIX epoch", and BeaconDB requires it and stores it apart from its own submission time. The
 * uploader used to send the upload time for every record. Runs under a comma decimal default
 * locale, since the JSON must stay locale independent.
 */
@RunWith(RobolectricTestRunner::class)
class GeosubmitJsonFormatterTest {
    private lateinit var originalLocale: Locale

    @Before
    fun useCommaDecimalLocale() {
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.GERMANY)
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun `cell and wifi reports carry the observation time, not the upload time`() {
        val json = GeosubmitJsonFormatter.formatRecords(
            listOf(lteRecord()), emptyList(), emptyList(), emptyList(), listOf(wifiRecord())
        )

        val items = json.getJSONArray("items")
        assertEquals(2, items.length())
        for (i in 0 until items.length()) {
            assertEquals(DEVICE_TIME_EPOCH_MILLIS, items.getJSONObject(i).getLong("timestamp"))
        }
    }

    @Test
    fun `an unreadable device time still sends the required timestamp`() {
        val before = System.currentTimeMillis()
        val record = lteRecord().apply { deviceTime = "not a time" }

        val json = GeosubmitJsonFormatter.formatRecords(listOf(record), emptyList(), emptyList(), emptyList(), emptyList())

        val timestamp = json.getJSONArray("items").getJSONObject(0).getLong("timestamp")
        assertTrue("Falls back to the upload time", timestamp >= before)
    }

    @Test
    fun `numbers are written with a dot under a comma decimal locale`() {
        val json = GeosubmitJsonFormatter.formatRecords(listOf(lteRecord()), emptyList(), emptyList(), emptyList(), emptyList())

        val text = json.toString()
        assertTrue(text, text.contains("\"latitude\":38.8894841"))
        assertTrue(text, text.contains("\"signalStrength\":-101.5"))
        assertFalse(text, text.contains("38,88"))
    }

    private fun BaseRecordEntity.base() {
        deviceSerialNumber = "serial"
        deviceName = "device"
        deviceTime = DEVICE_TIME
        latitude = 38.8894841
        longitude = -77.0352781
        accuracy = 7
        speed = 1.25f
    }

    private fun lteRecord() = LteRecordEntity().apply {
        base()
        mcc = 311; mnc = 480; tac = 7001; eci = 123456789; pci = 401; rsrp = -101.5f; servingCell = true
    }

    private fun wifiRecord() = WifiBeaconRecordEntity().apply {
        base()
        bssid = "aa:bb:cc:dd:ee:ff"; ssid = "Test"; channel = 6; frequencyMhz = 2437; signalStrength = -60f
    }

    private companion object {
        const val DEVICE_TIME = "2026-10-28T09:15:42.123-04:00"
        const val DEVICE_TIME_EPOCH_MILLIS = 1_793_193_342_123L
    }
}
