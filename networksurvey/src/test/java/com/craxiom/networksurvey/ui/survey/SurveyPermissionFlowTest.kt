package com.craxiom.networksurvey.ui.survey

import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.util.LocalNetworkPermissionUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Tests the local network step that runs before anything connects to an MQTT broker or a gRPC server,
 * and the battery optimization question before a survey starts.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class SurveyPermissionFlowTest {

    private val context = RuntimeEnvironment.getApplication()
    private val flow = SurveyPermissionFlow(context)
    private var runs = 0

    /** What happened, in order: "start" for the survey start, "settings" for the battery settings. */
    private val events = mutableListOf<String>()
    private var batteryDue = true
    private val batteryFlow = SurveyPermissionFlow(context, battery = object : BatteryPrompt {
        override fun isDue() = batteryDue
        override fun openSettings() {
            events += "settings"
        }
    })

    /** The system permission requests the flows handed to the screen, as the screen would launch them. */
    private val requested: List<List<String>>
        get() = listOfNotNull(flow.state.permissionRequest, batteryFlow.state.permissionRequest).map { it.toList() }

    /** The survey start as the screen wires it: the continuation runs once the start is done. */
    private var finishStart: () -> Unit = {}

    private fun start(finishesAtOnce: Boolean = true) = batteryFlow.ensureBatteryChoice { afterStart ->
        events += "start"
        if (finishesAtOnce) afterStart() else finishStart = afterStart
    }

    @Test
    fun `a server destination asks first, and connects whether access is granted or denied`() {
        flow.ensureLocalNetwork(listOf(Destination.GRPC)) { runs++ }
        assertEquals(listOf(listOf(LocalNetworkPermissionUtil.ACCESS_LOCAL_NETWORK)), requested)
        assertEquals(0, runs)

        flow.onRequestResult(allGranted = false)
        assertEquals(1, runs)

        flow.state.permissionRequest = null
        flow.ensureLocalNetwork(listOf(Destination.MQTT)) { runs++ }
        flow.onRequestResult(allGranted = true)
        assertEquals(2, runs)
    }

    @Test
    fun `destinations that do not connect to a server never ask`() {
        flow.ensureLocalNetwork(listOf(Destination.LOG_FILES, Destination.NS_ANALYTICS, Destination.COMMUNITY)) { runs++ }

        assertEquals(1, runs)
        assertEquals(emptyList<List<String>>(), requested)
    }

    @Test
    fun `once granted, it never asks again`() {
        shadowOf(context).grantPermissions(LocalNetworkPermissionUtil.ACCESS_LOCAL_NETWORK)

        flow.ensureLocalNetwork(listOf(Destination.GRPC)) { runs++ }

        assertEquals(1, runs)
        assertEquals(emptyList<List<String>>(), requested)
    }

    @Test
    @Config(sdk = [36])
    fun `before Android 17 it never asks`() {
        flow.ensureLocalNetwork(listOf(Destination.GRPC)) { runs++ }

        assertEquals(1, runs)
        assertEquals(emptyList<List<String>>(), requested)
    }

    @Test
    fun `with no battery question due, Start starts at once`() {
        batteryDue = false

        start()

        assertEquals(listOf("start"), events)
        assertFalse(batteryFlow.batteryQuestion)
    }

    @Test
    fun `Later starts the survey once`() {
        start()
        assertTrue(batteryFlow.batteryQuestion)
        assertEquals(emptyList<String>(), events)

        batteryFlow.onBatteryLater()
        batteryFlow.onBatteryLater()

        assertEquals(listOf("start"), events)
        assertFalse(batteryFlow.batteryQuestion)
    }

    @Test
    fun `Go to Settings starts the survey once, then opens the battery settings`() {
        start()

        batteryFlow.onBatteryGoToSettings()

        assertEquals(listOf("start", "settings"), events)
        assertFalse(batteryFlow.batteryQuestion)
    }

    @Test
    fun `Go to Settings waits for the start to finish before leaving the app`() {
        start(finishesAtOnce = false)

        batteryFlow.onBatteryGoToSettings()
        assertEquals(listOf("start"), events)

        finishStart()
        assertEquals(listOf("start", "settings"), events)
    }

    @Test
    fun `Back or a tap outside starts nothing`() {
        start()

        batteryFlow.onBatteryDismissed()
        batteryFlow.onBatteryLater()

        assertEquals(emptyList<String>(), events)
        assertFalse(batteryFlow.batteryQuestion)
    }

    @Test
    fun `the local network question comes first, then the battery question, then the start`() {
        batteryFlow.ensureLocalNetwork(listOf(Destination.MQTT)) { start() }
        assertEquals(listOf(listOf(LocalNetworkPermissionUtil.ACCESS_LOCAL_NETWORK)), requested)
        assertFalse(batteryFlow.batteryQuestion)

        batteryFlow.onRequestResult(allGranted = true)
        assertTrue(batteryFlow.batteryQuestion)

        batteryFlow.onBatteryLater()
        assertEquals(listOf("start"), events)
    }

    @Test
    fun `the flow keeps its state in the view model, so a new flow on it after a rotation answers the old question`() {
        start()
        // The screen was recreated: a new flow on the same state (the view model survived)
        val afterRotation = SurveyPermissionFlow(context, batteryFlow.state, battery = object : BatteryPrompt {
            override fun isDue() = true
            override fun openSettings() = Unit
        })
        assertTrue(afterRotation.batteryQuestion)

        afterRotation.onBatteryLater()
        assertEquals(listOf("start"), events)
    }

    @Test
    fun `a start that needs the call records permissions goes on when they are denied`() {
        flow.ensure(RecordType.CDR, includeOptional = false, runIfDenied = true) { runs++ }
        assertEquals(SurveyPermissionFlow.Rationale.CDR_REQUIRED, flow.rationale)

        flow.onRationaleConfirmed(SurveyPermissionFlow.Rationale.CDR_REQUIRED)
        flow.onRationaleDismissed()
        flow.onRequestResult(allGranted = false)

        assertEquals(1, runs)
    }

    @Test
    fun `turning on Call records waits for the grant, and a denial turns nothing on`() {
        flow.ensure(RecordType.CDR) { runs++ }
        flow.onRationaleConfirmed(SurveyPermissionFlow.Rationale.CDR_REQUIRED)
        flow.onRequestResult(allGranted = false)

        assertEquals(0, runs)
    }

    @Test
    fun `while the local network question is open the flow says it waits for an answer`() {
        assertFalse(flow.isWaitingForAnswer)
        flow.ensureLocalNetwork(listOf(Destination.MQTT)) { runs++ }
        // The screen launched the request; the answer hasn't come back yet
        flow.state.permissionRequest = null
        assertTrue(flow.isWaitingForAnswer)

        flow.onRequestResult(allGranted = true)
        assertFalse(flow.isWaitingForAnswer)
        assertEquals(1, runs)
    }
}
