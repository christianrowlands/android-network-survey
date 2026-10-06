package com.craxiom.networksurvey.survey.setup

import android.content.Context
import android.content.RestrictionsManager
import android.os.Bundle
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.craxiom.mqttlibrary.MqttConstants
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.survey.mdm.SurveyMdmKeys
import com.craxiom.networksurvey.util.PreferenceUtils
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * Tests the two decisions made before the survey service exists: whether the boot receiver starts
 * the survey ([SurveyAutoStart]), and whether the Intent API is allowed to control it
 * (`allow_intent_control`).
 */
@RunWith(RobolectricTestRunner::class)
class SurveyAutoStartAndIntentGateTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        PreferenceManager.getDefaultSharedPreferences(context).edit { clear() }
        setRestrictions(Bundle())
    }

    private fun setRestrictions(bundle: Bundle) {
        val restrictionsManager = context.getSystemService(Context.RESTRICTIONS_SERVICE) as RestrictionsManager
        shadowOf(restrictionsManager).setApplicationRestrictions(bundle)
    }

    @Test
    fun `boot does not start the survey by default`() {
        assertFalse(SurveyAutoStart.isEnabled(context))
    }

    @Test
    fun `boot starts the survey when the user turned auto start on`() {
        SurveySetupStore(context).userAutoStart = true
        assertTrue(SurveyAutoStart.isEnabled(context))
    }

    @Test
    fun `the organization's auto start choice beats the user's`() {
        SurveySetupStore(context).userAutoStart = true
        setRestrictions(Bundle().apply { putString(SurveyMdmKeys.AUTO_START_SURVEY, SurveyMdmKeys.VALUE_OFF) })
        assertFalse(SurveyAutoStart.isEnabled(context))

        setRestrictions(Bundle().apply { putString(SurveyMdmKeys.AUTO_START_SURVEY, SurveyMdmKeys.VALUE_ON) })
        SurveySetupStore(context).userAutoStart = false
        assertTrue(SurveyAutoStart.isEnabled(context))
    }

    @Test
    fun `a legacy fleet with an MDM broker and mqtt_start_on_boot still starts at boot`() {
        setRestrictions(Bundle().apply {
            putString(MqttConstants.PROPERTY_MQTT_CONNECTION_HOST, "broker.example.com")
            putString(MqttConstants.PROPERTY_MQTT_CLIENT_ID, "device-1")
            putBoolean(NetworkSurveyConstants.PROPERTY_MQTT_START_ON_BOOT, true)
        })
        assertTrue(SurveyAutoStart.isEnabled(context))
    }

    @Test
    fun `a legacy fleet with a phone state auto start key starts at boot`() {
        // Before v2 the boot receiver ignored auto_start_phone_state_logging entirely.
        setRestrictions(Bundle().apply {
            putBoolean(NetworkSurveyConstants.PROPERTY_AUTO_START_PHONE_STATE_LOGGING, true)
        })
        assertTrue(SurveyAutoStart.isEnabled(context))
    }

    @Test
    fun `the Intent API is allowed by default`() {
        assertTrue(PreferenceUtils.getAllowIntentControlPreference(context))
    }

    @Test
    fun `the organization can block the Intent API`() {
        setRestrictions(Bundle().apply { putBoolean(NetworkSurveyConstants.PROPERTY_ALLOW_INTENT_CONTROL, false) })
        assertFalse(PreferenceUtils.getAllowIntentControlPreference(context))
    }

    @Test
    fun `the user can block the Intent API on an unmanaged phone`() {
        PreferenceManager.getDefaultSharedPreferences(context).edit {
            putBoolean(NetworkSurveyConstants.PROPERTY_ALLOW_INTENT_CONTROL, false)
        }
        assertFalse(PreferenceUtils.getAllowIntentControlPreference(context))
    }
}
