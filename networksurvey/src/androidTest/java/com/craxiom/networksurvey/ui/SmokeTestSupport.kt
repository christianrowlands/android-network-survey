package com.craxiom.networksurvey.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.core.content.edit
import androidx.test.platform.app.InstrumentationRegistry
import com.craxiom.networksurvey.AppPermissions
import com.craxiom.networksurvey.NetworkSurveyActivity
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.services.DestinationTestSupport
import com.craxiom.networksurvey.survey.setup.SurveyPreferenceKeys
import org.junit.rules.ExternalResource

/** What the smoke tests on the real activity share: launching it, finding tabs, and the screen-on check. */
internal object SmokeTestSupport {
    const val TIMEOUT_MS = 15_000L
    private const val BATTERY_PREFERENCES = "battery_optimization"

    fun context(): Context = InstrumentationRegistry.getInstrumentation().targetContext

    /** The permissions the activity asks for at launch, so no permission prompt takes the screen. */
    fun grantedPermissions(): Array<String> =
        (AppPermissions.PERMISSIONS + AppPermissions.BLUETOOTH_PERMISSIONS + Manifest.permission.ACCESS_COARSE_LOCATION)
            .distinct().toTypedArray()

    fun activityIntent(): Intent = Intent(context(), NetworkSurveyActivity::class.java)

    fun shell(command: String): String {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
    }

    /**
     * True when the activity's window has FLAG_KEEP_SCREEN_ON. The view's keepScreenOn is folded
     * into the window's own copy of its attributes, which only the window manager reports.
     */
    fun keepScreenOnFlagSet(): Boolean {
        val windowName = "${context().packageName}/${NetworkSurveyActivity::class.java.name}"
        val dump = shell("dumpsys window windows")
        val window = dump.split("Window #").firstOrNull { it.contains(windowName) } ?: return false
        return window.contains("KEEP_SCREEN_ON")
    }

    /**
     * Answers the startup prompts and turns on Wi-Fi and location before the activity starts.
     * First run stays done unless [onboardingPending], which seeds what a fresh install's
     * migration writes (the migration itself has already run in this process).
     */
    class SeededStartup(private val onboardingPending: Boolean = false) : ExternalResource() {
        override fun before() {
            DestinationTestSupport.resetPreferences {
                it.putBoolean(context().getString(R.string.pref_key_ignore_raw_gnss_failure), true)
                it.putBoolean(NetworkSurveyConstants.PROPERTY_IGNORE_WIFI_SCAN_THROTTLING_WARNING, true)
                it.putBoolean(NetworkSurveyConstants.PROPERTY_KEY_DENIED_BACKGROUND_LOCATION_PERMISSION, true)
                it.putBoolean(SurveyPreferenceKeys.ONBOARDING_PENDING, onboardingPending)
            }
            context().getSharedPreferences(BATTERY_PREFERENCES, Context.MODE_PRIVATE).edit(commit = true) {
                putBoolean("dont_show_battery_optimization_prompt", true)
            }
            shell("svc wifi enable")
            shell("cmd location set-location-enabled true")
        }
    }
}

/** Finds the shell's tabs and titles and waits for them. */
internal class ShellDriver(private val compose: ComposeTestRule) {

    fun waitForHeading(title: String) = waitFor("the $title tab") {
        compose.onAllNodes(isHeading() and hasText(title)).fetchSemanticsNodes().isNotEmpty()
    }

    /** A tab or sub-tab, by its label. */
    fun tab(label: String) =
        compose.onNode(hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))

    /** A tab's top bar title. */
    fun heading(title: String) = compose.onNode(isHeading() and hasText(title))

    /** The Map tab has no title bar; its Layers button stands in for one. */
    fun mapChrome() = compose.onNode(hasContentDescription(mapLayers()))

    fun waitForMap() = waitFor("the Map tab") {
        compose.onAllNodes(hasContentDescription(mapLayers())).fetchSemanticsNodes().isNotEmpty()
    }

    private fun mapLayers() = SmokeTestSupport.context().getString(R.string.map_action_layers)

    fun waitFor(what: String, condition: () -> Boolean) {
        try {
            compose.waitUntil(SmokeTestSupport.TIMEOUT_MS, condition)
        } catch (e: Exception) {
            throw AssertionError("Timed out waiting for $what", e)
        }
    }
}
