package com.craxiom.networksurvey.ui.mqtt

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.craxiom.networksurvey.ui.showPage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The broker editor on the stateless [MqttBrokerEditorContent]. */
@RunWith(RobolectricTestRunner::class)
// Espresso reflects on InputManager.getInstance(), which API 37 removed; see SurveyScreenContentTest.
@Config(sdk = [35])
class MqttBrokerEditorContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val actions = RecordingActions()
    private val saved = MqttBrokerForm(isNew = false, name = "Lab", host = "10.0.0.40", port = "1883", canShare = true)

    @Test
    fun newBroker_savesAndSelects_andShowsFieldErrors() {
        show(MqttBrokerForm(isNew = true, hostError = true, portError = true))
        compose.onNodeWithText("Add a broker").assertIsDisplayed()
        compose.onNodeWithText("Enter the broker's host name or address").assertIsDisplayed()
        compose.onNodeWithText("Enter a port from 1 to 65535").assertIsDisplayed()
        compose.onNodeWithText("Remove this broker").assertDoesNotExist()
        compose.onNodeWithText("Save and select").performClick()
        assertEquals(listOf("save"), actions.calls)
    }

    @Test
    fun moreOptions_showsTheTopicPrefixAndQos() {
        show(saved.copy(moreOptionsExpanded = true))
        compose.onNodeWithText("Topic prefix").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Exactly once (2)").performScrollTo().performClick()
        assertEquals(listOf("edit"), actions.calls)
    }

    @Test
    fun editingTheStreamingBroker_saysSavingReconnects() {
        show(saved.copy(isActive = true, streaming = true))
        compose.onNodeWithText("Saving reconnects the stream to this broker. The survey keeps going.").assertIsDisplayed()
    }

    @Test
    fun remove_confirmsAndWarnsWhenItStopsTheStream() {
        show(saved.copy(isActive = true, streaming = true, isLast = true))
        compose.onNodeWithText("Remove this broker").performScrollTo().performClick()
        compose.onNodeWithText("Remove Lab?").assertIsDisplayed()
        compose.onNodeWithText(
            "Its saved password is deleted from this phone.\n\nMQTT stops for this survey. Other destinations keep going." +
                "\n\nMQTT shows Set up on the Survey screen until you add another."
        ).assertIsDisplayed()
        compose.onNodeWithText("Remove").performClick()
        assertEquals(listOf("remove"), actions.calls)
    }

    @Test
    fun remove_saysTheSurveyEndsWhenMqttIsItsOnlyDestination() {
        show(saved.copy(isActive = true, streaming = true, onlyDestination = true))
        compose.onNodeWithText("Remove this broker").performScrollTo().performClick()
        compose.onNodeWithText(
            "Its saved password is deleted from this phone.\n\nMQTT stops, and so does this survey: it is the only destination."
        ).assertIsDisplayed()
    }

    @Test
    fun share_warnsThatTheCodeCarriesThePassword() {
        show(saved)
        compose.onNodeWithText("This code includes the password. Show it only to people who should use this broker.")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Share as QR code").performScrollTo().performClick()
        assertEquals(listOf("share"), actions.calls)
    }

    @Test
    fun largeFonts_stillShowSave() {
        compose.showPage(largeFont = true) { MqttBrokerEditorContent(saved, actions, SnackbarHostState()) }
        compose.onNodeWithText("Save").assertIsDisplayed()
    }

    private fun show(form: MqttBrokerForm) = compose.showPage { MqttBrokerEditorContent(form, actions, SnackbarHostState()) }

    private class RecordingActions : MqttBrokerEditorActions {
        val calls = mutableListOf<String>()
        override fun onEdit(change: (MqttBrokerForm) -> MqttBrokerForm) { calls += "edit" }
        override fun onSave() { calls += "save" }
        override fun onShare() { calls += "share" }
        override fun onRemove() { calls += "remove" }
    }
}
