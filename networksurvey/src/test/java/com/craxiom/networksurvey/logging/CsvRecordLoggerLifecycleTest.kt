package com.craxiom.networksurvey.logging

import android.content.Context
import android.content.RestrictionsManager
import android.os.Bundle
import android.os.Environment
import com.craxiom.messaging.DeviceStatus
import com.craxiom.messaging.DeviceStatusData
import com.craxiom.networksurvey.services.NetworkSurveyService
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowEnvironment

/**
 * Lifecycle tests for [CsvRecordLogger], exercised through the lazy [DeviceStatusCsvLogger]
 * (every CSV logger except the CDR logger uses lazy file creation).
 *
 * Regression coverage for the NullPointerException that occurred when a lazy logger was
 * toggled off before it ever wrote a record: enabling sets the logging flag but defers
 * CSVPrinter creation, so the disable path closed a null printer.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CsvRecordLoggerLifecycleTest {

    @Before
    fun setUp() {
        // The enable path checks Environment.getExternalStorageState(); Robolectric defaults
        // it to not-mounted, so mark it mounted to let enableLogging(true) proceed.
        ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED)

        // The enable path reads the rollover-size preference, which inspects MDM application
        // restrictions. Robolectric returns null restrictions by default (a real device
        // returns an empty Bundle), so provide an empty Bundle to avoid a spurious NPE.
        val restrictionsManager = RuntimeEnvironment.getApplication()
            .getSystemService(Context.RESTRICTIONS_SERVICE) as RestrictionsManager
        shadowOf(restrictionsManager).setApplicationRestrictions(Bundle())
    }

    private fun createLazyLogger(): DeviceStatusCsvLogger {
        val service = mock(NetworkSurveyService::class.java)
        `when`(service.applicationContext).thenReturn(RuntimeEnvironment.getApplication())
        return DeviceStatusCsvLogger(service)
    }

    @Test
    fun `disabling a lazy logger that was enabled but never wrote a record returns true without throwing`() {
        val logger = createLazyLogger()

        // Enable: lazy creation sets loggingEnabled true but does not create the CSVPrinter
        // until the first record is written.
        assertTrue("Enabling a lazy logger should succeed", logger.enableLogging(true))

        // Disable before any record is written. Before the fix this threw an NPE because the
        // printer was still null when the disable path called printer.close(true).
        assertTrue(
            "Disabling a never-written lazy logger should report success",
            logger.enableLogging(false)
        )
    }

    @Test
    fun `disabling a lazy logger after a record was written returns true without throwing`() {
        val logger = createLazyLogger()

        assertTrue("Enabling a lazy logger should succeed", logger.enableLogging(true))

        // Writing a record forces lazy file/printer creation, so the disable path now closes a
        // real (non-null) printer. This guards the actual close/flush branch.
        logger.onDeviceStatus(buildDeviceStatus())

        assertTrue(
            "Disabling after writing a record should report success",
            logger.enableLogging(false)
        )
    }

    @Test
    fun `disabling a logger that was never enabled is a no-op that returns false`() {
        val logger = createLazyLogger()

        assertFalse(
            "Disabling when logging was never enabled should report no-op",
            logger.enableLogging(false)
        )
    }

    private fun buildDeviceStatus(): DeviceStatus {
        val data = DeviceStatusData.newBuilder()
            .setDeviceTime("2026-06-02T12:00:00Z")
            .setLatitude(38.8977)
            .setLongitude(-77.0365)
            .build()
        return DeviceStatus.newBuilder().setData(data).build()
    }
}
