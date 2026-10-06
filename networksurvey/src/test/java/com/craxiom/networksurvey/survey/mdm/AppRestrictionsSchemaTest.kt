package com.craxiom.networksurvey.survey.mdm

import android.content.Context
import android.content.RestrictionEntry
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.fragments.SettingsFragment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.xmlpull.v1.XmlPullParser

/**
 * Pins the managed configuration schema (`res/xml/app_restrictions.xml`) that EMM consoles read.
 * Fleets reference these keys by name, so a rename here must fail a test, not ship.
 */
@RunWith(RobolectricTestRunner::class)
class AppRestrictionsSchemaTest {

    private val androidNs = "http://schemas.android.com/apk/res/android"
    private lateinit var context: Context
    private val restrictions = mutableMapOf<String, Map<String, String?>>()

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        val parser = context.resources.getXml(R.xml.app_restrictions)
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "restriction") {
                val key = parser.getAttributeValue(androidNs, "key")
                restrictions[key] = mapOf(
                    "type" to parser.getAttributeValue(androidNs, "restrictionType"),
                    "default" to parser.getAttributeValue(androidNs, "defaultValue"),
                    "entryValues" to parser.getAttributeValue(androidNs, "entryValues"),
                )
            }
        }
    }

    @Test
    fun `every survey setup key is a three way choice that defaults to user`() {
        val values = context.resources.getStringArray(R.array.mdm_survey_choice_values).toList()
        assertEquals(listOf(SurveyMdmKeys.VALUE_USER, SurveyMdmKeys.VALUE_ON, SurveyMdmKeys.VALUE_OFF), values)

        for (key in SurveyMdmKeys.ALL_SETUP_KEYS) {
            val restriction = restrictions[key]
            assertTrue("missing $key", restriction != null)
            // The compiled schema stores the restriction type as its RestrictionEntry constant.
            assertEquals(key, RestrictionEntry.TYPE_CHOICE.toString(), restriction!!["type"])
            assertEquals(key, SurveyMdmKeys.VALUE_USER, restriction["default"])
        }
    }

    @Test
    fun `the deprecated keys stay in the schema for the legacy fallback release`() {
        for (key in LegacyMdmMapper.DEPRECATED_KEYS) {
            assertTrue("missing deprecated key $key", restrictions.containsKey(key))
        }
    }

    @Test
    fun `the kept keys are still there and log_file_type is gone`() {
        for (key in listOf(
            NetworkSurveyConstants.PROPERTY_MQTT_DEVICE_STATUS_STREAM_ENABLED,
            NetworkSurveyConstants.PROPERTY_MQTT_WATCHLIST_STREAM_ENABLED,
            NetworkSurveyConstants.MDM_PROPERTY_ALLOW_EXTERNAL_DATA_UPLOAD,
            NetworkSurveyConstants.MDM_PROPERTY_ALLOW_NS_ANALYTICS,
            NetworkSurveyConstants.PROPERTY_ALLOW_INTENT_CONTROL,
        )) {
            assertTrue("missing $key", restrictions.containsKey(key))
        }
        assertFalse(restrictions.containsKey("log_file_type"))
    }

    @Test
    fun `the Settings overlap list includes every survey setup key`() {
        val overlap = SettingsFragment.MDM_OVERLAP_PROPERTY_KEYS.toSet()
        for (key in SurveyMdmKeys.ALL_SETUP_KEYS) assertTrue("missing $key", key in overlap)
        assertFalse("log_file_type" in overlap)
    }
}
