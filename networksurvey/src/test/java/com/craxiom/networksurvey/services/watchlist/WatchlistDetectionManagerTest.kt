package com.craxiom.networksurvey.services.watchlist

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import com.craxiom.messaging.WifiBeaconRecord
import com.craxiom.messaging.WifiBeaconRecordData
import com.craxiom.networksurvey.logging.db.SurveyDatabase
import com.craxiom.networksurvey.logging.db.model.WatchlistEntryEntity
import com.craxiom.networksurvey.logging.db.model.WatchlistHitEntity
import com.craxiom.networksurvey.model.WifiRecordWrapper
import com.google.protobuf.FloatValue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * End-to-end tests for [WatchlistDetectionManager]: a scan that contains a watched network is
 * recorded to the history and raises an alert. Guards the 0 dBm bug, where an access point that
 * reported no signal strength was stored and announced as "is nearby (0 dBm)".
 */
@RunWith(RobolectricTestRunner::class)
class WatchlistDetectionManagerTest {

    private lateinit var app: Application
    private lateinit var manager: WatchlistDetectionManager

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        runBlocking {
            val database = SurveyDatabase.getInstance(app)
            database.watchlistHitDao().clearAll()
            database.watchlistDao().deleteAll()
        }
    }

    @After
    fun tearDown() {
        if (::manager.isInitialized) manager.stop()
    }

    @Test
    fun aSightingWithNoSignalIsStoredAndAnnouncedWithoutADbmValue() {
        val hit = detect(signalStrength = null)

        assertEquals(WatchlistHitEntity.RSSI_UNKNOWN, hit.rssi)
        val text = notificationText()
        assertFalse("Expected no dBm value in: $text", text.contains("dBm"))
        assertTrue(text.contains(LABEL))
    }

    @Test
    fun aSightingWithASignalIsStoredAndAnnouncedWithItsDbmValue() {
        val hit = detect(signalStrength = -63.4f)

        assertEquals(-63, hit.rssi)
        assertTrue(notificationText().contains("-63 dBm"))
    }

    /**
     * Watches [SSID], feeds the manager scans with that network until the sighting is recorded,
     * and returns the recorded hit.
     */
    private fun detect(signalStrength: Float?): WatchlistHitEntity {
        val database = SurveyDatabase.getInstance(app)
        val entryId = runBlocking {
            database.watchlistDao().insert(WatchlistEntryEntity().apply {
                label = LABEL
                ssid = SSID
                matchType = WatchlistEntryEntity.MATCH_TYPE_EXACT
                enabled = true
                createdAt = 1L
                cooldownSeconds = 0
            })
        }
        manager = WatchlistDetectionManager(app, "device", { null })
        manager.start()

        val data = WifiBeaconRecordData.newBuilder().setSsid(SSID).setBssid("12:34:56:78:9a:bc")
        signalStrength?.let { data.setSignalStrength(FloatValue.of(it)) }
        val scan = mutableListOf(WifiRecordWrapper(WifiBeaconRecord.newBuilder().setData(data).build(), ""))

        // The manager loads its watched-network snapshot asynchronously, so a scan that arrives
        // before the snapshot is ignored; keep scanning until the sighting lands.
        val hit = waitFor {
            manager.onWifiBeaconSurveyRecords(scan)
            runBlocking { database.watchlistHitDao().observeForEntry(entryId).first().firstOrNull() }
        }
        assertNotNull("The sighting was never recorded", hit)
        return hit!!
    }

    private fun notificationText(): String {
        val notificationManager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = waitFor { shadowOf(notificationManager).allNotifications.firstOrNull() }
        assertNotNull("No alert was shown", notification)
        return notification!!.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
    }

    private fun <T> waitFor(attempt: () -> T?): T? {
        repeat(WAIT_ATTEMPTS) {
            attempt()?.let { return it }
            Thread.sleep(WAIT_STEP_MS)
        }
        return null
    }

    private companion object {
        const val SSID = "Watched-Net"
        const val LABEL = "Office AP"
        const val WAIT_ATTEMPTS = 100
        const val WAIT_STEP_MS = 50L
    }
}
