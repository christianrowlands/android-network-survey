package com.craxiom.networksurvey.survey.mdm

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.survey.engine.FakeSurveyOutputs
import com.craxiom.networksurvey.survey.engine.SurveyCoordinator
import com.craxiom.networksurvey.survey.engine.SurveyOrchestrator
import com.craxiom.networksurvey.survey.engine.SurveyOrigin
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.survey.model.SurveySetup
import com.craxiom.networksurvey.survey.setup.SurveySetupStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Tests how the override switch and organization pushes change the survey.
 */
@RunWith(RobolectricTestRunner::class)
class MdmOverrideControllerTest {

    private val outputs = FakeSurveyOutputs()
    private lateinit var preferences: SharedPreferences
    private lateinit var store: SurveySetupStore
    private lateinit var executor: ExecutorService
    private lateinit var coordinator: SurveyCoordinator
    private lateinit var controller: MdmOverrideController
    private var restrictions: Map<String, Any?> = emptyMap()
    private var deviceStatusSent = 0
    private var brokerCopies = 0
    private var reconnectChecks = 0

    @Before
    fun setUp() {
        preferences = RuntimeEnvironment.getApplication().getSharedPreferences("override_test", Context.MODE_PRIVATE)
        preferences.edit { clear() }
        store = SurveySetupStore(preferences)
        executor = Executors.newSingleThreadExecutor()
        val source = RestrictionsSource { RestrictionsSnapshot(restrictions) }
        lateinit var holder: SurveyCoordinator
        coordinator = SurveyCoordinator(
            SurveyOrchestrator(outputs) { holder.policy() }, store, source,
            { preferences.getBoolean(NetworkSurveyConstants.PROPERTY_MDM_OVERRIDE_KEY, false) }, executor,
        ) {}
        holder = coordinator
        controller = MdmOverrideController(
            coordinator, store, preferences, source,
            { deviceStatusSent++ }, { brokerCopies++ }, { reconnectChecks++ },
        )
    }

    @After
    fun tearDown() {
        executor.shutdownNow()
    }

    /** Waits for every queued coordinator task to finish. */
    private fun drain() {
        coordinator.submit { }.get(5, TimeUnit.SECONDS)
    }

    private fun setOverride(on: Boolean) {
        preferences.edit { putBoolean(NetworkSurveyConstants.PROPERTY_MDM_OVERRIDE_KEY, on) }
        controller.onOverrideChanged(on)
        drain()
    }

    @Test
    fun `an identical re-push does nothing`() {
        restrictions = mapOf(SurveyMdmKeys.SURVEY_CELLULAR to "on")
        controller.initialize()
        assertFalse(controller.onRestrictionsChanged())
    }

    @Test
    fun `a push while overridden changes nothing and flags Review`() {
        restrictions = mapOf(SurveyMdmKeys.SURVEY_CELLULAR to "on")
        controller.initialize()
        setOverride(true)
        coordinator.startSurvey(SurveySetup.custom(setOf(RecordType.WIFI), setOf(Destination.LOG_FILES)), SurveyOrigin.USER).get()

        restrictions = mapOf(SurveyMdmKeys.SURVEY_CELLULAR to "on", SurveyMdmKeys.SURVEY_GNSS to "on")
        assertTrue(controller.onRestrictionsChanged())
        drain()

        assertTrue(controller.organizationUpdatedWhileOverridden)
        assertEquals(setOf(RecordType.WIFI), outputs.fileTypes)
    }

    @Test
    fun `a push applies to the running survey in place and clears the stopped flag`() {
        controller.initialize()
        coordinator.startSurvey(SurveySetup.custom(setOf(RecordType.WIFI), setOf(Destination.LOG_FILES)), SurveyOrigin.USER).get()
        store.setCurrentSetup(SurveySetup.custom(setOf(RecordType.WIFI), setOf(Destination.LOG_FILES)))
        store.stoppedByUser = true

        restrictions = mapOf(SurveyMdmKeys.SURVEY_GNSS to "on")
        assertTrue(controller.onRestrictionsChanged())
        drain()

        assertFalse(store.stoppedByUser)
        assertTrue(RecordType.GNSS in outputs.fileTypes)
        assertEquals(1, outputs.rolls)
        assertEquals(1, reconnectChecks)
    }

    @Test
    fun `a push never starts an idle survey`() {
        controller.initialize()
        restrictions = mapOf(SurveyMdmKeys.DESTINATION_LOG_FILES to "on", SurveyMdmKeys.SURVEY_WIFI to "on")
        controller.onRestrictionsChanged()
        drain()
        assertFalse(coordinator.orchestrator.isRecording())
    }

    @Test
    fun `a phone that just became managed takes the organization's setup as its starting point`() {
        controller.initialize()
        store.setCurrentSetup(SurveySetup.custom(setOf(RecordType.BLUETOOTH), setOf(Destination.NS_ANALYTICS)))

        restrictions = mapOf(SurveyMdmKeys.DESTINATION_MQTT to "on", SurveyMdmKeys.SURVEY_CELLULAR to "on")
        controller.onRestrictionsChanged()

        assertEquals(SurveySetup.empty(), store.getCurrentSetup())
    }

    @Test
    fun `turning the override on keeps what runs and seeds the user setup from the organization's`() {
        restrictions = mapOf(SurveyMdmKeys.SURVEY_CELLULAR to "on", SurveyMdmKeys.DESTINATION_LOG_FILES to "on")
        controller.initialize()
        store.setCurrentSetup(SurveySetup.empty())
        coordinator.startSurvey(null, SurveyOrigin.USER).get()
        val callsBefore = outputs.calls.size

        setOverride(true)

        assertEquals(callsBefore, outputs.calls.size)
        assertEquals(setOf(RecordType.CELLULAR), store.getCurrentSetup().recordTypes)
        assertEquals(setOf(Destination.LOG_FILES), store.getCurrentSetup().destinations)
        assertEquals(1, deviceStatusSent)
        assertEquals(1, brokerCopies)
        assertEquals(1, outputs.rolls)
    }

    @Test
    fun `restore puts the organization's setup back without rolling the Mission ID`() {
        restrictions = mapOf(SurveyMdmKeys.SURVEY_CELLULAR to "on", SurveyMdmKeys.DESTINATION_LOG_FILES to "on")
        controller.initialize()
        setOverride(true)
        coordinator.startSurvey(SurveySetup.custom(setOf(RecordType.WIFI), setOf(Destination.LOG_FILES)), SurveyOrigin.USER).get()

        setOverride(false)

        assertEquals(SurveySetup.empty(), store.getCurrentSetup())
        assertEquals(setOf(RecordType.CELLULAR, RecordType.PHONE_STATE), outputs.fileTypes)
        assertEquals(1, outputs.rolls)
        assertFalse(controller.organizationUpdatedWhileOverridden)
    }

    @Test
    fun `restore never starts an idle survey`() {
        restrictions = mapOf(SurveyMdmKeys.SURVEY_CELLULAR to "on", SurveyMdmKeys.DESTINATION_LOG_FILES to "on")
        controller.initialize()
        setOverride(true)
        setOverride(false)
        assertFalse(coordinator.orchestrator.isRecording())
    }
}
