package com.craxiom.networksurvey.logging.db.uploader

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.logging.db.SurveyDatabase
import com.craxiom.networksurvey.logging.db.model.WifiBeaconRecordEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * The upload of the community records left when a survey stops with automatic upload on: it only
 * runs when records wait, and it waits for the same network and battery as the automatic uploads.
 */
@RunWith(RobolectricTestRunner::class)
class AutoUploadRequestTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val database get() = SurveyDatabase.getInstance(context)

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        background { database.clearAllTables() }
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(NetworkSurveyConstants.PROPERTY_UPLOAD_TO_BEACONDB, true)
            .putBoolean(NetworkSurveyConstants.PROPERTY_AUTO_UPLOAD_WIFI_ONLY, true)
            .commit()
    }

    @After
    fun tearDown() {
        background { database.clearAllTables() }
    }

    @Test
    fun `nothing waiting uploads nothing`() {
        assertFalse(background { AutoUploadRequest.uploadRemaining(context) })
        assertTrue(communityWork().isEmpty())
    }

    @Test
    fun `records left upload on Wi-Fi with a battery that isn't low`() {
        background { database.wifiRecordDao().insertRecords(listOf(wifiRecord())) }

        assertTrue(background { AutoUploadRequest.uploadRemaining(context) })

        val work = communityWork().single()
        assertEquals(WorkInfo.State.ENQUEUED, work.state)
        assertEquals(NetworkType.UNMETERED, work.constraints.requiredNetworkType)
        assertTrue(work.constraints.requiresBatteryNotLow())
    }

    @Test
    fun `Wi-Fi left after BeaconDB was turned off isn't uploaded`() {
        background { database.wifiRecordDao().insertRecords(listOf(wifiRecord())) }
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(NetworkSurveyConstants.PROPERTY_UPLOAD_TO_BEACONDB, false)
            .commit()

        assertFalse(background { AutoUploadRequest.uploadRemaining(context) })
        assertTrue(communityWork().isEmpty())
    }

    /** Room refuses the main thread, which Robolectric runs tests on; the service calls this off it too. */
    private fun <T> background(block: () -> T): T = Executors.newSingleThreadExecutor().let { executor ->
        try {
            executor.submit(Callable { block() }).get()
        } finally {
            executor.shutdown()
        }
    }

    private fun communityWork(): List<WorkInfo> = WorkManager.getInstance(context)
        .getWorkInfosForUniqueWork(NetworkSurveyConstants.COMMUNITY_UPLOAD_UNIQUE_WORK_NAME).get()

    private fun wifiRecord() = WifiBeaconRecordEntity().apply {
        deviceSerialNumber = "test"
        deviceName = "test"
        deviceTime = "2026-10-14T12:00:00Z"
        bssid = "00:11:22:33:44:55"
    }
}
