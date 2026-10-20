package com.craxiom.networksurvey.ui.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.craxiom.networksurvey.ui.showPage
import com.craxiom.networksurvey.ui.textLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Each first run card, stateless, at a small phone's width and at large font. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h740dp")
// Real text measurement, so the large-font checks measure the actual font.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OnboardingContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val actions = RecordingActions()

    private fun show(
        step: OnboardingStep,
        permissionsMissing: Boolean = true,
        locationOff: Boolean = false,
        permissions: List<OnboardingPermission> = TIRAMISU_PERMISSIONS,
        largeFont: Boolean = false,
    ) {
        compose.showPage(largeFont = largeFont) {
            OnboardingContent(step, permissions, permissionsMissing, locationOff, actions)
        }
    }

    @Test
    fun intro_atLargeFont_keepsTheTitleAndBothButtonsWhole() {
        show(OnboardingStep.INTRO, largeFont = true)

        compose.onNodeWithText(INTRO_TITLE).assertIsDisplayed()
        assertWhole(INTRO_TITLE)
        listOf("Skip", "Continue").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
            assertEquals(1, compose.textLayout(it).lineCount)
            assertWhole(it)
        }
        compose.onNodeWithContentDescription("Step 1 of 3").assertIsDisplayed()
    }

    @Test
    fun intro_continueAndSkip() {
        show(OnboardingStep.INTRO)
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Skip").performClick()
        assertEquals(listOf("continue", "skip"), actions.calls)
    }

    @Test
    fun permissions_atLargeFont_listEachReason_andAskWithAllowAndContinue() {
        show(OnboardingStep.PERMISSIONS, largeFont = true)

        compose.onNodeWithText("Permissions, and why").assertIsDisplayed()
        listOf("Location", "Phone", "Notifications").forEach { compose.onNodeWithText(it).performScrollTo().assertIsDisplayed() }
        compose.onNodeWithText("Bluetooth and call records ask for their own permissions when you turn them on.")
            .performScrollTo().assertIsDisplayed()
        assertWhole("Allow and continue")
        compose.onNodeWithText("Allow and continue").performClick()
        compose.onNodeWithContentDescription("Step 2 of 3").assertIsDisplayed()
        assertEquals(listOf("allow"), actions.calls)
    }

    @Test
    fun permissions_beforeAndroid13_explainStorageInsteadOfNotifications() {
        show(OnboardingStep.PERMISSIONS, permissions = OnboardingPermission.forRequested(PRE_TIRAMISU))
        compose.onNodeWithText("Storage").performScrollTo().assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithTextCount("Notifications"))
    }

    @Test
    fun permissions_allGranted_continueWithoutAsking() {
        show(OnboardingStep.PERMISSIONS, permissionsMissing = false)
        compose.onNodeWithText("Continue").performClick()
        assertEquals(listOf("continue"), actions.calls)
    }

    @Test
    fun permissions_locationOff_offersToTurnItOn() {
        show(OnboardingStep.PERMISSIONS, locationOff = true)
        compose.onNodeWithText("Location is off on this phone.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Turn on").performScrollTo().performClick()
        assertEquals(listOf("turnOnLocation"), actions.calls)
    }

    @Test
    fun destination_atLargeFont_offersThreeChoices_withoutAFooter() {
        show(OnboardingStep.DESTINATION, largeFont = true)

        compose.onNodeWithText("Where should the data go?").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithTextCount("Skip"))
        listOf(NS_ANALYTICS, FILES, LATER).forEach {
            compose.onNodeWithText(it).performScrollTo().assertIsDisplayed()
            assertWhole(it)
        }
        compose.onNodeWithText(NS_ANALYTICS).performClick()
        compose.onNodeWithText(FILES).performClick()
        compose.onNodeWithText(LATER).performClick()
        assertEquals(listOf("choose:NS_ANALYTICS", "choose:LOG_FILES", "choose:DECIDE_LATER"), actions.calls)
        compose.onNodeWithContentDescription("Step 3 of 3").assertIsDisplayed()
    }

    private fun assertWhole(text: String) {
        val layout = compose.textLayout(text)
        assertFalse("$text is cut off", layout.isLineEllipsized(layout.lineCount - 1))
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(text: String) =
        onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().size

    private class RecordingActions : OnboardingActions {
        val calls = mutableListOf<String>()
        override fun onContinue() { calls += "continue" }
        override fun onAllowPermissions() { calls += "allow" }
        override fun onSkip() { calls += "skip" }
        override fun onTurnOnLocation() { calls += "turnOnLocation" }
        override fun onChoose(choice: OnboardingChoice) { calls += "choose:$choice" }
    }

    private companion object {
        const val INTRO_TITLE = "Walk around. It records what the radios see."
        const val NS_ANALYTICS = "My team's NS Analytics workspace"
        const val FILES = "Files on this phone"
        const val LATER = "I'll choose on the Survey screen"
        val TIRAMISU_PERMISSIONS = OnboardingPermission.forRequested(
            arrayOf("android.permission.ACCESS_FINE_LOCATION", "android.permission.READ_PHONE_STATE", "android.permission.POST_NOTIFICATIONS"),
        )
        val PRE_TIRAMISU = arrayOf(
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.READ_PHONE_STATE",
        )
    }
}
