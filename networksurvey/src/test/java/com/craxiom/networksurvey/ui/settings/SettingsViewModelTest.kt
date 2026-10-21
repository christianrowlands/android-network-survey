package com.craxiom.networksurvey.ui.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.survey.mdm.ManagedState
import com.craxiom.networksurvey.survey.mdm.RestrictionsSnapshot
import com.craxiom.networksurvey.ui.theme.NsAccent
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowLooper

/** The checks the Settings page makes when a value changes, as the old preference screen did. */
@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {

    private lateinit var preferences: SharedPreferences
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        val application = RuntimeEnvironment.getApplication()
        preferences = application.getSharedPreferences("settings_vm_test", Context.MODE_PRIVATE)
        preferences.edit { clear() }
        viewModel = SettingsViewModel(
            application,
            preferences,
            { appInstanceId ->
                SettingsReader.Environment(RestrictionsSnapshot(emptyMap()), ManagedState.USER, true, true, false, null, appInstanceId)
            },
            Dispatchers.Unconfined,
            { _, onResult -> onResult(APP_INSTANCE_ID) },
        )
    }

    @Test
    fun theAppInstanceId_showsOnceItIsKnown() {
        assertEquals(APP_INSTANCE_ID, viewModel.state.value.appInstanceId)
    }

    @Test
    fun aBluetoothIntervalBelowTheMinimum_isRaised_andSaysSo() {
        viewModel.setScanRate(ScanRate.BLUETOOTH, 10)

        val minimum = NetworkSurveyConstants.MINIMUM_BLUETOOTH_SCAN_INTERVAL_SECONDS
        assertEquals(minimum.toString(), preferences.getString(ScanRate.BLUETOOTH.key, null))
        assertEquals(SettingsNotice.BluetoothMinimum(minimum), viewModel.notice.value)
        settle()
        assertEquals(minimum, viewModel.state.value.scanRates.getValue(ScanRate.BLUETOOTH).value)
    }

    @Test
    fun theAccent_isStoredByItsPrefValue_andReadBack() {
        viewModel.setAccent(NsAccent.TEAL)

        assertEquals("teal", preferences.getString(NetworkSurveyConstants.PROPERTY_APP_ACCENT, null))
        settle()
        assertEquals(NsAccent.TEAL, viewModel.state.value.accent)
    }

    @Test
    fun otherIntervals_areStoredAsTyped_asStrings() {
        viewModel.setScanRate(ScanRate.WIFI, 10)

        assertEquals("10", preferences.getString(ScanRate.WIFI.key, null))
        assertNull(viewModel.notice.value)
    }

    @Test
    fun theBatteryThreshold_isStoredAsAnInt_withinItsRange() {
        viewModel.setBatteryThreshold(120)
        assertEquals(SettingsViewModel.BATTERY_THRESHOLD_MAX, preferences.getInt(NetworkSurveyConstants.PROPERTY_BATTERY_THRESHOLD_PERCENT, -1))

        viewModel.setBatteryThreshold(-5)
        assertEquals(0, preferences.getInt(NetworkSurveyConstants.PROPERTY_BATTERY_THRESHOLD_PERCENT, -1))
    }

    @Test
    fun noQueueLimit_asksFirst_andCancelPutsThePreviousLimitBack() {
        viewModel.setQueueLimit(5000)
        viewModel.setQueueLimit(0)

        assertEquals(SettingsPrompt.DisableQueueLimit("5000"), viewModel.prompt.value)
        viewModel.revertQueueLimit("5000")

        assertEquals("5000", preferences.getString(NetworkSurveyConstants.PROPERTY_STREAMING_QUEUE_LIMIT, null))
        assertNull(viewModel.prompt.value)
    }

    @Test
    fun aVeryHighQueueLimit_asksFirst_andCanUseTheRecommendedOne() {
        viewModel.setQueueLimit(90000)

        assertEquals(SettingsPrompt.HighQueueLimit, viewModel.prompt.value)
        viewModel.useRecommendedQueueLimit()

        assertEquals(
            NetworkSurveyConstants.DEFAULT_STREAMING_QUEUE_LIMIT.toString(),
            preferences.getString(NetworkSurveyConstants.PROPERTY_STREAMING_QUEUE_LIMIT, null),
        )
    }

    @Test
    fun keepingAHighLimit_makesItThePreviousLimit_forTheNextNoLimit() {
        viewModel.setQueueLimit(90000)
        viewModel.keepQueueLimit()
        viewModel.setQueueLimit(0)

        assertEquals(SettingsPrompt.DisableQueueLimit("90000"), viewModel.prompt.value)
    }

    @Test
    fun theOverride_isWritten() {
        viewModel.setOverride(true)

        assertEquals(true, preferences.getBoolean(NetworkSurveyConstants.PROPERTY_MDM_OVERRIDE_KEY, false))
        settle()
        assertEquals(true, viewModel.state.value.overrideOn)
    }

    @Test
    fun restoringTheOrganizationsSettings_copiesItsValuesInAgain() {
        val application = RuntimeEnvironment.getApplication()
        preferences.edit { putBoolean(NetworkSurveyConstants.PROPERTY_MDM_OVERRIDE_KEY, true) }
        val managed = RestrictionsSnapshot(mapOf(NetworkSurveyConstants.PROPERTY_WIFI_SCAN_INTERVAL_SECONDS to 60))
        val managedViewModel = SettingsViewModel(
            application,
            preferences,
            { appInstanceId -> SettingsReader.Environment(managed, ManagedState.USER, true, true, false, null, appInstanceId) },
            Dispatchers.Unconfined,
            { _, _ -> },
        )
        managedViewModel.setScanRate(ScanRate.WIFI, 20)

        preferences.edit { putBoolean(NetworkSurveyConstants.PROPERTY_MDM_OVERRIDE_KEY, false) }
        settle()

        assertEquals("60", preferences.getString(ScanRate.WIFI.key, null))
        assertEquals(SettingValue(60, locked = true), managedViewModel.state.value.scanRates[ScanRate.WIFI])
    }

    private fun settle() = ShadowLooper.idleMainLooper()

    private companion object {
        const val APP_INSTANCE_ID = "test-instance"
    }
}
