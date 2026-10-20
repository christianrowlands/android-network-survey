package com.craxiom.networksurvey.ui.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.ui.showPage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The upgrade welcome's "Your survey" section for the upgrades that only stream or upload. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h740dp")
class UpgradeWelcomeContentTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(setup: UpgradeWelcomeSetup) {
        compose.showPage { UpgradeWelcomeContent(setup, onDone = {}) }
    }

    private fun assertShown(text: String) {
        compose.onNodeWithText(text, substring = true).performScrollTo().assertIsDisplayed()
    }

    private fun assertNotShown(text: String) {
        compose.onNodeWithText(text, substring = true).assertDoesNotExist()
    }

    @Test
    fun mqttOnlyWithoutRecordTypes_saysDeviceStatusOnly_andThatItStartsOnItsOwn() {
        show(UpgradeWelcomeSetup(emptyList(), listOf(Destination.MQTT), autoStart = true))
        assertShown(YOUR_SURVEY)
        assertShown("Will send only device status to MQTT stream.")
        assertShown(AUTO_START)
    }

    @Test
    fun mqttOnlyWithRecordTypes_saysItStartsOnItsOwn() {
        show(UpgradeWelcomeSetup(listOf(RecordType.CELLULAR, RecordType.WIFI), listOf(Destination.MQTT), autoStart = true))
        assertShown("MQTT stream")
        assertShown(AUTO_START)
        assertNotShown(BROKER_SAVED)
    }

    @Test
    fun communityOnly_saysItStartsOnItsOwn() {
        show(UpgradeWelcomeSetup(listOf(RecordType.CELLULAR, RecordType.WIFI), listOf(Destination.COMMUNITY), autoStart = true))
        assertShown(YOUR_SURVEY)
        assertShown(AUTO_START)
    }

    @Test
    fun aSavedBrokerOutsideTheSurvey_saysHowToUseIt() {
        show(UpgradeWelcomeSetup(listOf(RecordType.WIFI), listOf(Destination.LOG_FILES), autoStart = false, mqttBrokerSaved = true))
        assertShown(BROKER_SAVED)
        assertNotShown(AUTO_START)
    }

    private companion object {
        const val YOUR_SURVEY = "Your survey"
        const val AUTO_START = "It starts on its own when the phone starts or the app opens."
        const val BROKER_SAVED = "Your MQTT broker is saved. Turn on MQTT stream to use it"
    }
}
