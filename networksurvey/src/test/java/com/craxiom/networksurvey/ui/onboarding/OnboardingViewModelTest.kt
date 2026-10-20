package com.craxiom.networksurvey.ui.onboarding

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.craxiom.networksurvey.survey.mdm.RestrictionsSnapshot
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.survey.model.SurveyDefaults
import com.craxiom.networksurvey.survey.model.SurveySetup
import com.craxiom.networksurvey.survey.setup.SurveySetupStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** First run's view model: the skip rules as the activity sees them, and the choice it stores. */
@RunWith(RobolectricTestRunner::class)
class OnboardingViewModelTest {

    private lateinit var preferences: SharedPreferences
    private lateinit var store: SurveySetupStore
    private var restrictions: Map<String, Any?> = emptyMap()
    private var registered = false
    private var linkPending = false

    @Before
    fun setUp() {
        preferences = RuntimeEnvironment.getApplication().getSharedPreferences("onboarding_test", Context.MODE_PRIVATE)
        preferences.edit { clear() }
        store = SurveySetupStore(preferences)
        store.onboardingPending = true
    }

    private fun viewModel() = OnboardingViewModel(
        RuntimeEnvironment.getApplication() as Application,
        store,
        { RestrictionsSnapshot(restrictions) },
        { registered },
        { linkPending },
        { true },
        { hasUserBroker },
    )

    private var hasUserBroker = false

    @Test
    fun `an upgraded MQTT stream without record types is welcomed as device status only`() {
        store.onboardingPending = false
        store.upgradeWelcomePending = true
        store.setCurrentSetup(SurveySetup.custom(emptySet(), setOf(Destination.MQTT)))
        store.userAutoStart = true
        hasUserBroker = true

        val setup = viewModel().welcomeSetup()

        assertTrue(setup.deviceStatusOnly)
        assertTrue(setup.autoStart)
        // MQTT is in the survey, so there is nothing to say about the saved broker
        assertFalse(setup.mqttBrokerSaved)
    }

    @Test
    fun `a saved broker the survey does not send to is mentioned`() {
        store.onboardingPending = false
        store.upgradeWelcomePending = true
        store.setCurrentSetup(SurveySetup.custom(setOf(RecordType.WIFI), setOf(Destination.LOG_FILES)))
        hasUserBroker = true

        val setup = viewModel().welcomeSetup()

        assertTrue(setup.mqttBrokerSaved)
        assertFalse(setup.deviceStatusOnly)
    }

    @Test
    fun `an upgraded phone gets the welcome once, with its own setup`() {
        store.onboardingPending = false
        store.upgradeWelcomePending = true
        store.setCurrentSetup(SurveySetup.custom(setOf(RecordType.WIFI, RecordType.CELLULAR), setOf(Destination.LOG_FILES)))
        store.userAutoStart = true
        val viewModel = viewModel()

        assertFalse(viewModel.visible.value)
        assertTrue(viewModel.welcome.value)
        assertTrue(viewModel.coversTabs)
        val setup = viewModel.welcomeSetup()
        // Phone state comes with Cellular, as the Survey tab shows it
        assertEquals(listOf(RecordType.CELLULAR, RecordType.PHONE_STATE, RecordType.WIFI), setup.types)
        assertEquals(listOf(Destination.LOG_FILES), setup.destinations)
        assertTrue(setup.autoStart)

        viewModel.finishWelcome()
        assertFalse(viewModel.welcome.value)
        assertFalse(store.upgradeWelcomePending)
        assertFalse(viewModel().welcome.value)
    }

    @Test
    fun `a managed phone skips the welcome for good`() {
        store.onboardingPending = false
        store.upgradeWelcomePending = true
        restrictions = mapOf("mqtt_connection_host" to "broker.example.net")
        assertFalse(viewModel().welcome.value)
        assertFalse(store.upgradeWelcomePending)
    }

    @Test
    fun `a fresh install shows the cards`() {
        assertTrue(viewModel().visible.value)
        assertTrue(store.onboardingPending)
    }

    @Test
    fun `a managed phone skips them for good`() {
        restrictions = mapOf("mqtt_connection_host" to "broker.example.net")
        assertFalse(viewModel().visible.value)
        assertFalse(store.onboardingPending)
    }

    @Test
    fun `a registered phone skips them for good`() {
        registered = true
        assertFalse(viewModel().visible.value)
        assertFalse(store.onboardingPending)
    }

    @Test
    fun `a registration link that arrives while the cards show skips them on resume`() {
        val viewModel = viewModel()
        assertTrue(viewModel.visible.value)
        linkPending = true
        viewModel.onResume()
        assertFalse(viewModel.visible.value)
        assertFalse(store.onboardingPending)
    }

    @Test
    fun `files on this phone sends the default record types to Log files`() = runTest {
        val viewModel = viewModel()
        viewModel.finish(OnboardingChoice.LOG_FILES)
        assertEquals(setOf(Destination.LOG_FILES), store.getCurrentSetup().destinations)
        assertEquals(SurveyDefaults.RECORD_TYPES, store.getCurrentSetup().recordTypes)
        assertFalse(viewModel.visible.value)
        assertFalse(store.onboardingPending)
        assertEquals(Unit, viewModel.finished.first())
    }

    @Test
    fun `NS Analytics keeps the record types and sends them to NS Analytics`() {
        store.setCurrentSetup(SurveyDefaults.freshInstallSetup().copy(destinations = setOf(Destination.LOG_FILES)))
        viewModel().finish(OnboardingChoice.NS_ANALYTICS)
        assertEquals(setOf(Destination.NS_ANALYTICS), store.getCurrentSetup().destinations)
    }

    @Test
    fun `deciding later and skipping leave the setup as it is`() {
        viewModel().finish(OnboardingChoice.DECIDE_LATER)
        assertEquals(SurveyDefaults.freshInstallSetup(), store.getCurrentSetup())
        store.onboardingPending = true
        viewModel().finish(null)
        assertEquals(SurveyDefaults.freshInstallSetup(), store.getCurrentSetup())
        assertFalse(store.onboardingPending)
    }

    @Test
    fun `a failing registration read shows the cards instead of crashing`() {
        val viewModel = OnboardingViewModel(
            RuntimeEnvironment.getApplication() as Application,
            store,
            { RestrictionsSnapshot(emptyMap()) },
            { throw IllegalStateException("keystore") },
            { false },
        )
        assertTrue(viewModel.visible.value)
    }
}
