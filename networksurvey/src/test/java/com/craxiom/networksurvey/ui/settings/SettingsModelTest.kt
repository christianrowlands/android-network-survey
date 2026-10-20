package com.craxiom.networksurvey.ui.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.survey.mdm.ManagedState
import com.craxiom.networksurvey.survey.mdm.RestrictionsSnapshot
import com.craxiom.networksurvey.survey.mdm.SurveyMdmKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * What Settings shows (stored values, or the organization's values locked), the organization's
 * values copied into the preferences with the types their readers expect, and the first-launch
 * defaults that replaced the preference XML.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsModelTest {

    private lateinit var preferences: SharedPreferences

    @Before
    fun setUp() {
        preferences = RuntimeEnvironment.getApplication().getSharedPreferences("settings_test", Context.MODE_PRIVATE)
        preferences.edit { clear() }
    }

    @Test
    fun anUnmanagedPhone_showsTheStoredValues_withNothingLocked() {
        preferences.edit {
            putString(NetworkSurveyConstants.PROPERTY_WIFI_SCAN_INTERVAL_SECONDS, "20")
            putInt(NetworkSurveyConstants.PROPERTY_BATTERY_THRESHOLD_PERCENT, 15)
            putBoolean(NetworkSurveyConstants.PROPERTY_INCLUDE_NEIGHBOR_CELLS, false)
        }

        val state = read(RestrictionsSnapshot(emptyMap()))

        assertFalse(state.managed)
        assertEquals(SettingValue(20), state.scanRates[ScanRate.WIFI])
        assertEquals(SettingValue(NetworkSurveyConstants.DEFAULT_CELLULAR_SCAN_INTERVAL_SECONDS), state.scanRates[ScanRate.CELLULAR])
        assertEquals(SettingValue(15), state.batteryThreshold)
        assertEquals(SettingValue(false), state.neighborCells)
        assertFalse(state.uploadsBlocked)
    }

    @Test
    fun aManagedPhone_showsTheOrganizationsValuesLocked_andTheRestStored() {
        preferences.edit { putString(NetworkSurveyConstants.PROPERTY_WIFI_SCAN_INTERVAL_SECONDS, "20") }

        val state = read(managed, autoStart = ManagedState.ON, uploadsAllowed = false)

        assertTrue(state.managed)
        assertEquals(SettingValue(60, locked = true), state.scanRates[ScanRate.WIFI])
        assertEquals(SettingValue(25, locked = true), state.batteryThreshold)
        assertEquals(SettingValue(false, locked = true), state.intentControl)
        assertEquals(SettingValue(true, locked = true), state.autoStart)
        assertEquals(SettingValue(NetworkSurveyConstants.DEFAULT_GNSS_SCAN_INTERVAL_SECONDS), state.scanRates[ScanRate.GNSS])
        assertTrue(state.uploadsBlocked)
    }

    @Test
    fun theOverride_unlocksEverything_butNotTheUploadBlock() {
        preferences.edit {
            putBoolean(NetworkSurveyConstants.PROPERTY_MDM_OVERRIDE_KEY, true)
            putString(NetworkSurveyConstants.PROPERTY_WIFI_SCAN_INTERVAL_SECONDS, "20")
        }

        val state = read(managed, autoStart = ManagedState.ON, uploadsAllowed = false)

        assertTrue(state.overrideOn)
        assertEquals(SettingValue(20), state.scanRates[ScanRate.WIFI])
        assertFalse(state.autoStart.locked)
        assertTrue(state.uploadsBlocked)
    }

    @Test
    fun theOrganizationsValues_areCopiedWithTheTypesTheirReadersExpect() {
        ManagedSettingsSync.write(preferences, managed)

        assertEquals("60", preferences.getString(NetworkSurveyConstants.PROPERTY_WIFI_SCAN_INTERVAL_SECONDS, null))
        assertEquals(25, preferences.getInt(NetworkSurveyConstants.PROPERTY_BATTERY_THRESHOLD_PERCENT, -1))
        assertFalse(preferences.getBoolean(NetworkSurveyConstants.PROPERTY_ALLOW_INTENT_CONTROL, true))
        assertTrue(preferences.getBoolean(NetworkSurveyConstants.PROPERTY_AUTO_UPLOAD_ENABLED, false))
    }

    @Test
    fun nothingIsCopied_withTheOverrideOn_orWhenUnmanaged() {
        ManagedSettingsSync.write(preferences, RestrictionsSnapshot(emptyMap()))
        preferences.edit { putBoolean(NetworkSurveyConstants.PROPERTY_MDM_OVERRIDE_KEY, true) }
        ManagedSettingsSync.write(preferences, managed)

        assertFalse(preferences.contains(NetworkSurveyConstants.PROPERTY_WIFI_SCAN_INTERVAL_SECONDS))
        assertFalse(preferences.contains(NetworkSurveyConstants.PROPERTY_BATTERY_THRESHOLD_PERCENT))
    }

    @Test
    fun theDefaults_fillOnlyWhatIsMissing_asTheOldScreenStoredThem() {
        preferences.edit { putString(NetworkSurveyConstants.PROPERTY_CELLULAR_SCAN_INTERVAL_SECONDS, "2") }

        SettingsDefaults.applyMissing(preferences)

        assertEquals("2", preferences.getString(NetworkSurveyConstants.PROPERTY_CELLULAR_SCAN_INTERVAL_SECONDS, null))
        assertEquals(
            NetworkSurveyConstants.DEFAULT_WIFI_SCAN_INTERVAL_SECONDS.toString(),
            preferences.getString(NetworkSurveyConstants.PROPERTY_WIFI_SCAN_INTERVAL_SECONDS, null),
        )
        assertEquals("3", preferences.getString(NetworkSurveyConstants.PROPERTY_LOCATION_PROVIDER, null))
        assertFalse(preferences.getBoolean(SurveyMdmKeys.AUTO_START_SURVEY, true))
        assertTrue(preferences.getBoolean(NetworkSurveyConstants.PROPERTY_ALLOW_INTENT_CONTROL, false))
        // The battery threshold was never seeded by the old screen, so it stays unset
        assertFalse(preferences.contains(NetworkSurveyConstants.PROPERTY_BATTERY_THRESHOLD_PERCENT))
    }

    @Test
    fun theDefaults_areWrittenOncePerInstall_likeTheOldSeeding() {
        val context = RuntimeEnvironment.getApplication()
        val defaults = PreferenceManager.getDefaultSharedPreferences(context)
        defaults.edit { clear() }
        context.getSharedPreferences(PreferenceManager.KEY_HAS_SET_DEFAULT_VALUES, Context.MODE_PRIVATE).edit { clear() }

        SettingsDefaults.applyOnce(context)
        assertTrue(defaults.contains(NetworkSurveyConstants.PROPERTY_WIFI_SCAN_INTERVAL_SECONDS))

        // An install that already had its defaults is never written to again, even with keys missing
        defaults.edit { clear() }
        SettingsDefaults.applyOnce(context)
        assertFalse(defaults.contains(NetworkSurveyConstants.PROPERTY_WIFI_SCAN_INTERVAL_SECONDS))
    }

    private fun read(
        restrictions: RestrictionsSnapshot,
        autoStart: ManagedState = ManagedState.USER,
        uploadsAllowed: Boolean = true,
    ) = SettingsReader.read(
        preferences,
        SettingsReader.Environment(
            restrictions = restrictions,
            autoStartPolicy = autoStart,
            uploadsAllowed = uploadsAllowed,
            nsAnalyticsAllowed = true,
            batteryOptimizationOff = false,
            deviceSerial = "test-serial",
            appInstanceId = null,
        ),
    )

    private val managed = RestrictionsSnapshot(
        mapOf(
            NetworkSurveyConstants.PROPERTY_WIFI_SCAN_INTERVAL_SECONDS to 60,
            NetworkSurveyConstants.PROPERTY_BATTERY_THRESHOLD_PERCENT to 25,
            NetworkSurveyConstants.PROPERTY_ALLOW_INTENT_CONTROL to false,
            NetworkSurveyConstants.PROPERTY_AUTO_UPLOAD_ENABLED to true,
        )
    )
}
