package com.craxiom.networksurvey.ui

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.craxiom.networksurvey.NetworkSurveyActivity
import com.craxiom.networksurvey.fragments.CodeScannerFragment
import com.craxiom.networksurvey.fragments.NsAnalyticsQrScannerFragment
import com.craxiom.networksurvey.fragments.QrCodeShareFragment
import com.craxiom.networksurvey.fragments.model.MqttConnectionSettings
import com.craxiom.networksurvey.ui.SmokeTestSupport.activityIntent
import com.craxiom.networksurvey.ui.SmokeTestSupport.grantedPermissions
import com.craxiom.networksurvey.ui.main.SharedViewModel
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * The screens that stay Fragments (the QR scanners and the QR share), each hosted with
 * AndroidFragment: every one opens from its user path (QR share, which needs a broker, from the
 * shared view model's trigger), shows its fragment resumed, and Back removes it. The Settings pages
 * are Compose now; see SettingsSmokeTest.
 */
@RunWith(AndroidJUnit4::class)
class FragmentHostsSmokeTest {

    private val compose: ComposeTestRule = createEmptyComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(SmokeTestSupport.SeededStartup())
        .around(GrantPermissionRule.grant(*grantedPermissions(), Manifest.permission.CAMERA))
        .around(compose)

    private val shell = ShellDriver(compose)
    private var scenario: ActivityScenario<NetworkSurveyActivity>? = null

    @After
    fun tearDown() {
        scenario?.close()
    }

    @Test
    fun theMqttScanner_opensFromTheMqttPage() {
        launch()
        shell.tab("More").performClick()
        compose.onNodeWithText("MQTT stream").performClick()
        compose.onNodeWithText("Scan a QR code").performClick()

        opensAndCloses<CodeScannerFragment>("Scan a QR code", recreate = true)
    }

    @Test
    fun theMqttShareCode_opensForABroker() {
        launch()
        scenario!!.onActivity { activity ->
            val settings = MqttConnectionSettings.Builder().host("broker.example.com").port(1883).build()
            ViewModelProvider(activity)[SharedViewModel::class.java].triggerNavigationToQrCodeShare(settings)
        }

        opensAndCloses<QrCodeShareFragment>("Share as QR code")
    }

    @Test
    fun theNsAnalyticsScanner_opensFromTheConnectPage() {
        launch()
        shell.tab("More").performClick()
        compose.onNodeWithText("NS Analytics").performClick()
        compose.onNodeWithText("Scan the QR code").performClick()

        opensAndCloses<NsAnalyticsQrScannerFragment>("Scan the NS Analytics code")
    }

    /**
     * Waits for the page's title and its fragment to be resumed; with [recreate], recreates the
     * activity (as a rotation does) and checks one fragment came back, not a second one beside the
     * restored first. Then presses Back and waits until the fragment is removed.
     */
    private inline fun <reified T : Fragment> opensAndCloses(title: String, recreate: Boolean = false) {
        shell.waitFor("the $title page") { fragments<T>().any { it.isResumed } }
        compose.onNodeWithText(title).assertIsDisplayed()
        if (recreate) {
            scenario!!.recreate()
            shell.waitFor("the $title page after a recreation") { fragments<T>().singleOrNull()?.isResumed == true }
        }
        Espresso.pressBack()
        shell.waitFor("the $title page to close") { fragments<T>().isEmpty() }
    }

    private inline fun <reified T : Fragment> fragments(): List<Fragment> {
        var found = emptyList<Fragment>()
        scenario!!.onActivity { activity -> found = activity.supportFragmentManager.fragments.filter { it is T } }
        return found
    }

    private fun launch() {
        scenario = ActivityScenario.launch(activityIntent())
        shell.waitForHeading("Survey")
    }
}
