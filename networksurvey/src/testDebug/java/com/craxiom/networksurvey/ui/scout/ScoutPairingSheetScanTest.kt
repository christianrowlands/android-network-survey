package com.craxiom.networksurvey.ui.scout

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.craxiom.networksurvey.scout.link.ScoutLinkFailure
import com.craxiom.networksurvey.scout.model.ScoutPairingState
import com.craxiom.networksurvey.ui.theme.NsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Add a Scout sheet's scan: the page stops it whenever it pauses, so a resume starts it again,
 * except while a pairing step shows.
 */
@RunWith(RobolectricTestRunner::class)
// Espresso reflects on InputManager.getInstance(), which API 37 removed; see SurveyScreenContentTest.
@Config(sdk = [35])
class ScoutPairingSheetScanTest {

    @get:Rule
    val compose = createComposeRule()

    private var scans = 0
    private val actions = object : ScoutPairingActions {
        override fun onStartScan() {
            scans++
        }
    }
    private val pairing = mutableStateOf<ScoutPairingState>(ScoutPairingState.Idle)
    private val owner = object : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Test
    fun aResume_startsTheScanAgain() {
        showResumed()
        assertEquals(1, scans)
        pauseAndResume()
        assertEquals(2, scans)
    }

    @Test
    fun aResume_duringThePairPrompt_startsNoScan() {
        showResumed()
        compose.runOnIdle { pairing.value = ScoutPairingState.Pairing("NS-ABCD") }
        pauseAndResume()
        assertEquals(1, scans)
    }

    @Test
    fun aResume_keepsAFailureUp() {
        showResumed()
        compose.runOnIdle { pairing.value = ScoutPairingState.Failed("NS-ABCD", ScoutLinkFailure.PAIRING_REJECTED) }
        pauseAndResume()
        assertEquals(1, scans)
    }

    @Test
    fun openingDuringAPairing_startsNoScan() {
        pairing.value = ScoutPairingState.Pairing("NS-ABCD")
        showResumed()
        assertEquals(0, scans)
    }

    private fun showResumed() {
        owner.registry.currentState = Lifecycle.State.RESUMED
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                NsTheme(darkTheme = false) {
                    ScoutPairingSheet(ScoutPairingUi(pairing = pairing.value), pairedName = null, actions = actions, onReplace = {})
                }
            }
        }
        compose.waitForIdle()
    }

    private fun pauseAndResume() {
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.waitForIdle()
    }
}
