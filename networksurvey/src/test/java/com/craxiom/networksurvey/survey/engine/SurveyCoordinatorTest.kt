package com.craxiom.networksurvey.survey.engine

import android.content.Context
import androidx.core.content.edit
import com.craxiom.networksurvey.survey.mdm.RestrictionsSnapshot
import com.craxiom.networksurvey.survey.mdm.SurveyMdmKeys
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.survey.model.SurveySetup
import com.craxiom.networksurvey.survey.setup.SurveySetupStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Tests the coordinator's rules around the orchestrator: auto start, the "stopped by the user" flag
 * on a managed phone, and the start origin.
 */
@RunWith(RobolectricTestRunner::class)
class SurveyCoordinatorTest {

    private val outputs = FakeSurveyOutputs()
    private lateinit var store: SurveySetupStore
    private lateinit var executor: ExecutorService
    private var restrictions: Map<String, Any?> = emptyMap()
    private var overrideOn = false
    private var stateChanges = 0
    private lateinit var coordinator: SurveyCoordinator

    @Before
    fun setUp() {
        val preferences = RuntimeEnvironment.getApplication().getSharedPreferences("coordinator_test", Context.MODE_PRIVATE)
        preferences.edit { clear() }
        store = SurveySetupStore(preferences)
        store.setCurrentSetup(SurveySetup.custom(setOf(RecordType.WIFI), setOf(Destination.LOG_FILES)))
        executor = Executors.newSingleThreadExecutor()
        lateinit var holder: SurveyCoordinator
        val orchestrator = SurveyOrchestrator(outputs) { holder.policy() }
        coordinator = SurveyCoordinator(
            orchestrator, store, { RestrictionsSnapshot(restrictions) }, { overrideOn }, executor,
        ) { stateChanges++ }
        holder = coordinator
    }

    @After
    fun tearDown() {
        executor.shutdownNow()
    }

    private fun managed(values: Map<String, Any?>) {
        restrictions = values
        coordinator.refreshPolicy()
    }

    @Test
    fun `start with no setup starts the working setup with the organization's locks applied`() {
        managed(mapOf(SurveyMdmKeys.SURVEY_GNSS to "on"))
        val result = coordinator.startSurvey(null, SurveyOrigin.USER).get()

        assertTrue(result.recording)
        assertEquals(setOf(RecordType.WIFI, RecordType.GNSS), outputs.fileTypes)
        assertEquals(SurveyOrigin.USER, coordinator.startOrigin)
        assertTrue(stateChanges > 0)
    }

    @Test
    fun `a user stop on a phone the organization shapes sets the stopped by user flag`() {
        managed(mapOf(SurveyMdmKeys.AUTO_START_SURVEY to "on"))
        coordinator.startSurvey(null, SurveyOrigin.USER).get()
        coordinator.stopSurvey(SurveyOrigin.USER).get()

        assertTrue(store.stoppedByUser)
        assertNull(coordinator.startOrigin)
    }

    @Test
    fun `a stop on an unmanaged phone does not set the flag, and one from the Intent API counts like the user's`() {
        coordinator.startSurvey(null, SurveyOrigin.USER).get()
        coordinator.stopSurvey(SurveyOrigin.USER).get()
        assertFalse(store.stoppedByUser)

        managed(mapOf(SurveyMdmKeys.AUTO_START_SURVEY to "on"))
        coordinator.startSurvey(null, SurveyOrigin.USER).get()
        coordinator.stopSurvey(SurveyOrigin.ORGANIZATION).get()
        assertFalse(store.stoppedByUser)

        coordinator.startSurvey(null, SurveyOrigin.USER).get()
        coordinator.stopSurvey(SurveyOrigin.INTENT).get()
        assertTrue(store.stoppedByUser)
    }

    @Test
    fun `app open auto start respects the stopped by user flag and boot clears it`() {
        managed(mapOf(SurveyMdmKeys.AUTO_START_SURVEY to "on"))
        store.stoppedByUser = true

        assertNull(coordinator.startAutoSurveyIfEnabled(SurveyOrigin.APP_OPEN))
        assertFalse(outputs.isRunning(Destination.LOG_FILES))

        val result = coordinator.startAutoSurveyIfEnabled(SurveyOrigin.BOOT)!!.get()
        assertTrue(result.recording)
        assertFalse(store.stoppedByUser)
        assertEquals(SurveyOrigin.BOOT, coordinator.startOrigin)
    }

    @Test
    fun `auto start follows the user's setting when the organization leaves it to the user`() {
        assertNull(coordinator.startAutoSurveyIfEnabled(SurveyOrigin.APP_OPEN))

        store.userAutoStart = true
        val result = coordinator.startAutoSurveyIfEnabled(SurveyOrigin.APP_OPEN)!!.get()
        assertTrue(result.recording)
    }

    @Test
    fun `app open auto start keeps what a boot started survey left out`() {
        store.userAutoStart = true
        outputs.typeProblems[RecordType.BLUETOOTH] = SkipReason.RADIO_OFF
        store.setCurrentSetup(SurveySetup.custom(setOf(RecordType.WIFI, RecordType.BLUETOOTH), setOf(Destination.LOG_FILES)))
        coordinator.startAutoSurveyIfEnabled(SurveyOrigin.BOOT)!!.get()

        coordinator.startAutoSurveyIfEnabled(SurveyOrigin.APP_OPEN)!!.get()

        assertEquals(mapOf(RecordType.BLUETOOTH to SkipReason.RADIO_OFF), coordinator.lastResult!!.recordTypeProblems)
    }

    @Test
    fun `auto start never restarts a running survey`() {
        store.userAutoStart = true
        coordinator.startSurvey(null, SurveyOrigin.USER).get()
        val callsBefore = outputs.calls.size

        coordinator.startAutoSurveyIfEnabled(SurveyOrigin.APP_OPEN)!!.get()
        assertEquals(callsBefore, outputs.calls.size)
        assertEquals(SurveyOrigin.USER, coordinator.startOrigin)
    }

    @Test
    fun `the organization turning auto start off beats the user's setting`() {
        store.userAutoStart = true
        managed(mapOf(SurveyMdmKeys.AUTO_START_SURVEY to "off"))
        assertFalse(coordinator.isAutoStartEnabled())
    }

    @Test
    fun `the override lifts the organization's auto start`() {
        managed(mapOf(SurveyMdmKeys.AUTO_START_SURVEY to "on"))
        overrideOn = true
        coordinator.refreshPolicy()
        assertFalse(coordinator.isAutoStartEnabled())
    }

    @Test
    fun `the last result is kept for the attention plate`() {
        outputs.destinationProblems[Destination.MQTT] = SkipReason.NOT_CONFIGURED
        coordinator.startSurvey(SurveySetup.custom(setOf(RecordType.WIFI), setOf(Destination.LOG_FILES, Destination.MQTT)), SurveyOrigin.USER).get()
        assertEquals(mapOf(Destination.MQTT to SkipReason.NOT_CONFIGURED), coordinator.lastResult!!.skipped)
    }

    @Test
    fun `a destination set up while recording starts for the running survey without a new Mission ID`() {
        store.setCurrentSetup(SurveySetup.custom(setOf(RecordType.WIFI), setOf(Destination.LOG_FILES, Destination.GRPC)))
        outputs.destinationProblems[Destination.GRPC] = SkipReason.NOT_CONFIGURED
        coordinator.startSurvey(null, SurveyOrigin.USER).get()
        val rolls = outputs.rolls
        outputs.destinationProblems.remove(Destination.GRPC)

        var reconnects = 0
        coordinator.onDestinationSettingsChanged(Destination.GRPC, { reconnects++ }, { true }).get()

        assertEquals(1, reconnects)
        assertEquals(setOf(RecordType.WIFI), outputs.grpcTypes)
        assertEquals(rolls, outputs.rolls)
    }

    @Test
    fun `a destination an intent asked for starts once set up, even if the user's setup leaves it out`() {
        outputs.destinationProblems[Destination.GRPC] = SkipReason.NOT_CONFIGURED
        coordinator.mergeIntoRunningSurvey(
            SurveySetup.custom(setOf(RecordType.CELLULAR), setOf(Destination.LOG_FILES, Destination.GRPC)), SurveyOrigin.INTENT,
        ).get()
        outputs.destinationProblems.remove(Destination.GRPC)

        coordinator.onDestinationSettingsChanged(Destination.GRPC, {}, { true }).get()

        assertEquals(setOf(RecordType.CELLULAR, RecordType.PHONE_STATE), outputs.grpcTypes)
        // The user's working setup (Wi-Fi to Log files) never replaced the intent's survey
        assertEquals(setOf(RecordType.CELLULAR, RecordType.PHONE_STATE), outputs.fileTypes)
    }

    @Test
    fun `dropping a destination keeps the rest running, and dropping the last one stops the survey`() {
        coordinator.mergeIntoRunningSurvey(
            SurveySetup.custom(setOf(RecordType.CELLULAR), setOf(Destination.LOG_FILES, Destination.NS_ANALYTICS)), SurveyOrigin.INTENT,
        ).get()
        val rolls = outputs.rolls
        var dropped = 0

        coordinator.dropDestination(Destination.NS_ANALYTICS) { dropped++ }.get()
        assertNull(outputs.nsAnalyticsTypes)
        assertTrue(coordinator.orchestrator.isRecording())
        assertEquals(rolls, outputs.rolls)
        assertEquals(1, dropped)

        coordinator.dropDestination(Destination.NS_ANALYTICS) { dropped++ }.get()
        assertEquals(1, dropped)

        coordinator.dropDestination(Destination.LOG_FILES) { dropped++ }.get()
        assertFalse(coordinator.orchestrator.isRecording())
        assertEquals(2, dropped)
    }

    @Test
    fun `dropping the only running destination stops the survey even when a skipped one was asked for`() {
        outputs.destinationProblems[Destination.MQTT] = SkipReason.NOT_CONFIGURED
        coordinator.startSurvey(
            SurveySetup.custom(setOf(RecordType.CELLULAR), setOf(Destination.NS_ANALYTICS, Destination.MQTT)), SurveyOrigin.USER,
        ).get()
        var dropped = 0

        coordinator.dropDestination(Destination.NS_ANALYTICS) { dropped++ }.get()

        assertNull(outputs.nsAnalyticsTypes)
        assertFalse(coordinator.orchestrator.isRecording())
        assertEquals(1, dropped)
    }

    @Test
    fun `a settings change never starts an idle survey`() {
        store.setCurrentSetup(SurveySetup.custom(setOf(RecordType.WIFI), setOf(Destination.GRPC)))

        coordinator.onDestinationSettingsChanged(Destination.GRPC, {}, { true }).get()

        assertNull(outputs.grpcTypes)
        assertFalse(coordinator.orchestrator.isRecording())
    }

    @Test
    fun `a settings change does not start a destination the setup leaves out or the organization keeps off`() {
        coordinator.startSurvey(null, SurveyOrigin.USER).get()
        coordinator.onDestinationSettingsChanged(Destination.GRPC, {}, { true }).get()
        assertNull(outputs.grpcTypes)

        store.setCurrentSetup(SurveySetup.custom(setOf(RecordType.WIFI), setOf(Destination.LOG_FILES, Destination.GRPC)))
        managed(mapOf(SurveyMdmKeys.DESTINATION_GRPC to "off"))
        coordinator.onDestinationSettingsChanged(Destination.GRPC, {}, { true }).get()
        assertNull(outputs.grpcTypes)
    }

    @Test
    fun `a destination that is still not set up is not started`() {
        store.setCurrentSetup(SurveySetup.custom(setOf(RecordType.WIFI), setOf(Destination.LOG_FILES, Destination.GRPC)))
        outputs.destinationProblems[Destination.GRPC] = SkipReason.NOT_CONFIGURED
        coordinator.startSurvey(null, SurveyOrigin.USER).get()

        coordinator.onDestinationSettingsChanged(Destination.GRPC, {}, { false }).get()

        assertNull(outputs.grpcTypes)
    }
}
