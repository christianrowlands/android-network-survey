package com.craxiom.networksurvey.ui.organization

import com.craxiom.mqttlibrary.MqttConstants
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.survey.mdm.AppRestrictionsDefinitions
import com.craxiom.networksurvey.survey.mdm.RestrictionsSnapshot
import com.craxiom.networksurvey.survey.mdm.SurveyMdmKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The Organization settings page's content: every value the organization set, grouped and titled
 * from the managed configuration schema, the data permissions always shown, and the notes.
 */
@RunWith(RobolectricTestRunner::class)
class OrganizationSettingsReaderTest {

    private val definitions = AppRestrictionsDefinitions.read(RuntimeEnvironment.getApplication().resources)

    @Test
    fun `the schema is read with titles for every key`() {
        assertTrue(definitions.size > 40)
        val cellular = definitions.first { it.key == SurveyMdmKeys.SURVEY_CELLULAR }
        assertEquals(R.string.mdm_survey_cellular_title, cellular.titleRes)
        assertTrue(definitions.all { it.titleRes != 0 })
    }

    @Test
    fun `an unmanaged phone has nothing to list`() {
        val ui = read(emptyMap())
        assertFalse(ui.managed)
        assertTrue(ui.groups.isEmpty())
    }

    @Test
    fun `values are grouped, in schema order, in the page's words`() {
        val ui = read(
            mapOf(
                SurveyMdmKeys.SURVEY_WIFI to SurveyMdmKeys.VALUE_OFF,
                SurveyMdmKeys.SURVEY_CELLULAR to SurveyMdmKeys.VALUE_ON,
                SurveyMdmKeys.AUTO_START_SURVEY to SurveyMdmKeys.VALUE_USER,
                NetworkSurveyConstants.MDM_PROPERTY_ALLOW_EXTERNAL_DATA_UPLOAD to true,
                NetworkSurveyConstants.PROPERTY_CELLULAR_SCAN_INTERVAL_SECONDS to 5,
                "something_unknown" to "x",
            )
        )

        assertEquals(listOf(OrgGroup.SURVEY, OrgGroup.PERMISSIONS, OrgGroup.OTHER), ui.groups.map { it.group })
        val survey = ui.groups[0].rows
        assertEquals(listOf(SurveyMdmKeys.SURVEY_CELLULAR, SurveyMdmKeys.SURVEY_WIFI, SurveyMdmKeys.AUTO_START_SURVEY), survey.map { it.key })
        assertEquals(listOf(OrgValue.On, OrgValue.Off, OrgValue.UserChooses), survey.map { it.value })

        val other = ui.groups[2].rows
        assertEquals(OrgValue.Text("5"), other.first { it.key == NetworkSurveyConstants.PROPERTY_CELLULAR_SCAN_INTERVAL_SECONDS }.value)
        // A key the app does not declare is shown by its raw name, last.
        assertEquals(OrgRowUi("something_unknown", 0, OrgValue.Text("x")), other.last())
        assertFalse(ui.communityOffBecauseManaged)
    }

    @Test
    fun `the data permissions are always listed with the value in force`() {
        val permissions = read(mapOf(SurveyMdmKeys.SURVEY_CELLULAR to SurveyMdmKeys.VALUE_ON))
            .groups.first { it.group == OrgGroup.PERMISSIONS }.rows.associate { it.key to it.value }

        // A managed phone may not upload to public databases unless the organization allows it.
        assertEquals(OrgValue.NotAllowed, permissions[NetworkSurveyConstants.MDM_PROPERTY_ALLOW_EXTERNAL_DATA_UPLOAD])
        assertEquals(OrgValue.Allowed, permissions[NetworkSurveyConstants.MDM_PROPERTY_ALLOW_NS_ANALYTICS])
        assertEquals(OrgValue.UserChooses, permissions[NetworkSurveyConstants.PROPERTY_ALLOW_INTENT_CONTROL])
    }

    @Test
    fun `a broker-only fleet gets its note, the password stays secret, and Community is explained`() {
        val ui = read(
            mapOf(
                MqttConstants.PROPERTY_MQTT_CONNECTION_HOST to "mqtt.example.org",
                MqttConstants.PROPERTY_MQTT_CLIENT_ID to "device-1",
                MqttConstants.PROPERTY_MQTT_PASSWORD to "secret",
            )
        )

        assertTrue(ui.brokerOnly)
        assertTrue(ui.communityOffBecauseManaged)
        assertEquals(R.string.allow_data_upload, ui.externalUploadTitleRes)
        val mqtt = ui.groups.first { it.group == OrgGroup.MQTT }.rows
        assertEquals(OrgValue.Secret, mqtt.first { it.key == MqttConstants.PROPERTY_MQTT_PASSWORD }.value)
        assertFalse(mqtt.any { it.value == OrgValue.Text("secret") })
    }

    @Test
    fun `the override is carried for the page's note`() {
        assertTrue(read(mapOf(SurveyMdmKeys.SURVEY_CELLULAR to SurveyMdmKeys.VALUE_ON), overrideOn = true).overrideOn)
    }

    private fun read(values: Map<String, Any?>, overrideOn: Boolean = false) =
        OrganizationSettingsReader.read(RestrictionsSnapshot(values), definitions, overrideOn)
}
