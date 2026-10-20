package com.craxiom.networksurvey.ui.signals

import android.content.Context
import android.content.RestrictionsManager
import android.os.Bundle
import androidx.preference.PreferenceManager
import com.craxiom.networksurvey.gpstest.model.GnssType
import com.craxiom.networksurvey.util.PreferenceUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** The Signals tab's shared state: whether the GNSS filter is on, which badges its top bar action. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SignalsViewModelTest {

    private val application = RuntimeEnvironment.getApplication()
    private val preferences = PreferenceManager.getDefaultSharedPreferences(application)

    @Before
    fun setUp() {
        preferences.edit().clear().commit()
        // The view model reads the scan intervals at once, through the (here empty) MDM restrictions.
        val restrictions = application.getSystemService(Context.RESTRICTIONS_SERVICE) as RestrictionsManager
        shadowOf(restrictions).setApplicationRestrictions(Bundle())
    }

    @Test
    fun theGnssFilterBadge_followsTheSavedFilter() {
        val viewModel = SignalsViewModel(application).apply { ioDispatcher = UnconfinedTestDispatcher() }
        assertFalse(viewModel.gnssFilterActive.value)

        PreferenceUtils.saveGnssFilter(application, setOf(GnssType.GALILEO), preferences)
        assertTrue(viewModel.gnssFilterActive.value)

        PreferenceUtils.clearGnssFilter(application, preferences)
        assertFalse(viewModel.gnssFilterActive.value)
    }
}
