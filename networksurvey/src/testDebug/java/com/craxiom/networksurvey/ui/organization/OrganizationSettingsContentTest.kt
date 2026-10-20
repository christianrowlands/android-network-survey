package com.craxiom.networksurvey.ui.organization

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.craxiom.mqttlibrary.MqttConstants
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.survey.mdm.AppRestrictionsDefinitions
import com.craxiom.networksurvey.survey.mdm.RestrictionsSnapshot
import com.craxiom.networksurvey.survey.mdm.SurveyMdmKeys
import com.craxiom.networksurvey.ui.showPage
import com.craxiom.networksurvey.ui.string
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The Organization settings page on the stateless [OrganizationSettingsContent]. */
@RunWith(RobolectricTestRunner::class)
// Espresso reflects on InputManager.getInstance(), which API 37 removed; see SurveyScreenContentTest.
@Config(sdk = [35])
class OrganizationSettingsContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val actions = object : OrganizationSettingsActions {
        override fun onUseOwnSettings() { calls += "useOwn" }
        override fun onRestore() { calls += "restore" }
    }

    @Test
    fun unmanaged_saysNoOrganization() {
        show(emptyMap())
        compose.onNodeWithText("No organization manages this phone.").assertIsDisplayed()
    }

    @Test
    fun managed_listsTheValuesInTheConsoleWords_andOffersOwnSettings() {
        show(mapOf(SurveyMdmKeys.SURVEY_CELLULAR to SurveyMdmKeys.VALUE_ON, SurveyMdmKeys.SURVEY_GNSS to SurveyMdmKeys.VALUE_USER))
        compose.onNodeWithText("Survey").assertIsDisplayed()
        // GNSS left to the person using the phone, and so is control by other apps.
        compose.onAllNodesWithText("You choose").assertCountEquals(2)
        compose.onNodeWithText("Not allowed").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(string(R.string.org_note_community_managed, string(R.string.allow_data_upload))).assertIsDisplayed()
        compose.onNodeWithText("Use my own settings").performScrollTo().performClick()
        compose.onNodeWithText("Use your own settings?").assertIsDisplayed()
    }

    @Test
    fun brokerOnly_saysEverythingElseIsUpToYou_andHidesThePassword() {
        show(
            mapOf(
                MqttConstants.PROPERTY_MQTT_CONNECTION_HOST to "mqtt.example.org",
                MqttConstants.PROPERTY_MQTT_CLIENT_ID to "device-1",
                MqttConstants.PROPERTY_MQTT_PASSWORD to "hunter2",
            )
        )
        compose.onNodeWithText("Your organization provides the MQTT broker. Everything else is up to you.").assertIsDisplayed()
        compose.onNodeWithText("hunter2").assertDoesNotExist()
        compose.onNodeWithText("Set").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun overridden_saysSo_andOffersRestore() {
        show(mapOf(SurveyMdmKeys.SURVEY_CELLULAR to SurveyMdmKeys.VALUE_ON), overrideOn = true)
        compose.onNodeWithText("You're using your own settings. These are your organization's values.").assertIsDisplayed()
        compose.onNodeWithText("Restore").performScrollTo().performClick()
        compose.onNodeWithText("Restore your organization's settings?").assertIsDisplayed()
    }

    @Test
    fun largeFonts_stillShowTheGroups() {
        val state = read(mapOf(SurveyMdmKeys.SURVEY_CELLULAR to SurveyMdmKeys.VALUE_ON), false)
        compose.showPage(largeFont = true) { OrganizationSettingsContent(state, recording = false, actions = actions) }
        compose.onNodeWithText("Permissions").performScrollTo().assertIsDisplayed()
        assertEquals(emptyList<String>(), calls)
    }

    private fun show(values: Map<String, Any?>, overrideOn: Boolean = false) {
        val state = read(values, overrideOn)
        compose.showPage { OrganizationSettingsContent(state, recording = false, actions = actions) }
    }

    private fun read(values: Map<String, Any?>, overrideOn: Boolean) = OrganizationSettingsReader.read(
        RestrictionsSnapshot(values),
        AppRestrictionsDefinitions.read(RuntimeEnvironment.getApplication().resources),
        overrideOn,
    )
}
