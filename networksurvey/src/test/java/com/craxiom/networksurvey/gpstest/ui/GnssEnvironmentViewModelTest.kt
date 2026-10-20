package com.craxiom.networksurvey.gpstest.ui

import android.content.Intent
import android.location.LocationManager
import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** The GNSS view's obstacles: read at start, again on refresh and when location changes. */
@RunWith(RobolectricTestRunner::class)
class GnssEnvironmentViewModelTest {

    private var state = GnssScanState.PERMISSION_NEEDED
    private val application = RuntimeEnvironment.getApplication()
    private val viewModel = GnssEnvironmentViewModel(application) { state }

    @Test
    fun theStateIsRead_atStart_andOnRefresh() {
        assertEquals(GnssScanState.PERMISSION_NEEDED, viewModel.scanState.value)
        state = GnssScanState.READY
        viewModel.refresh()
        assertEquals(GnssScanState.READY, viewModel.scanState.value)
    }

    @Test
    fun aLocationChange_readsTheStateAgain() {
        state = GnssScanState.LOCATION_OFF
        application.sendBroadcast(Intent(LocationManager.PROVIDERS_CHANGED_ACTION).setPackage(application.packageName))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(GnssScanState.LOCATION_OFF, viewModel.scanState.value)
    }

    @Test
    fun theAndroidEnvironment_checksThePermissionFirst() {
        // Robolectric grants nothing by default.
        assertEquals(GnssScanState.PERMISSION_NEEDED, AndroidGnssEnvironment(application).scanState())
    }
}
