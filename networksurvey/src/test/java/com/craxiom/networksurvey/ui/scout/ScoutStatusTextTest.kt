package com.craxiom.networksurvey.ui.scout

import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.scout.ScoutPolicy
import com.craxiom.networksurvey.scout.model.PairedScout
import com.craxiom.networksurvey.scout.model.ScoutState
import com.craxiom.networksurvey.survey.mdm.RestrictionsSnapshot
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.survey.model.SurveySetup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.Locale

/** The one NS Scout vocabulary: words, levels and detail lines for every state, Ready and Recording. */
@RunWith(RobolectricTestRunner::class)
class ScoutStatusTextTest {

    private val resources = RuntimeEnvironment.getApplication().resources

    @Test
    fun connected_readsBandsAndPowerBeforeStart_andChannelAndFramesWhileRecording() {
        val idle = ScoutState.Connected(SCOUT, batteryPercent = 82, channel = 0, framesPerSecond = 0, capturing = false, dataMissed = false)
        assertEquals("Connected", ScoutStatusText.word(resources, idle, recording = false))
        assertEquals("2.4 and 5 GHz · 82% battery", detail(idle, recording = false))

        val capturing = idle.copy(batteryPercent = null, channel = 36, framesPerSecond = 1_074, capturing = true)
        assertEquals("Ch 36 · hearing 1,074 frames/s", detail(capturing, recording = true))
        assertEquals("2.4 and 5 GHz · USB powered", detail(capturing, recording = false))
        assertEquals(ScoutLevel.OK, ScoutStatusText.level(capturing))
    }

    @Test
    fun outOfRange_isNotConnectedBeforeStart_andOutOfRangeWhileRecording() {
        val state = ScoutState.OutOfRange(SCOUT, batteryMayBeEmpty = false)
        assertEquals("Not connected", ScoutStatusText.word(resources, state, recording = false))
        assertEquals("Out of range", ScoutStatusText.word(resources, state, recording = true))
        assertEquals(ScoutLevel.WARN, ScoutStatusText.level(state))
    }

    @Test
    fun cantConnect_readsTheSameBeforeStartAndWhileRecording_andSaysToUnplugIt() {
        val state = ScoutState.CantConnect(SCOUT)
        assertEquals("Can't connect", ScoutStatusText.word(resources, state, recording = false))
        assertEquals("Can't connect", ScoutStatusText.word(resources, state, recording = true))
        val unplug = "NS-A1B2 answers but won't finish connecting. Unplug it for 5 seconds and plug it back in."
        assertEquals(unplug, detail(state, recording = false))
        assertEquals(unplug, detail(state, recording = true, lastHeard = NOW - 2 * MINUTE))
        assertEquals(ScoutLevel.BAD, ScoutStatusText.level(state))
    }

    @Test
    fun outOfRange_whileRecording_saysTheScoutStillListensForFiveMinutes() {
        val state = ScoutState.OutOfRange(SCOUT, batteryMayBeEmpty = false)
        assertEquals("Still listening on the Scout · last heard 2 minutes ago", detail(state, recording = true, lastHeard = NOW - 2 * MINUTE))
        assertEquals("Last heard 12 minutes ago", detail(state, recording = true, lastHeard = NOW - 12 * MINUTE))
        assertEquals("Plug it in and hold it near this phone", detail(state, recording = true, lastHeard = null))
        // Before Start the plate says that, so the row describes the Scout instead
        assertEquals("2.4 and 5 GHz", detail(state, recording = false, lastHeard = null))
        assertEquals("Battery may be empty", detail(state.copy(batteryMayBeEmpty = true), recording = true, lastHeard = NOW - 2 * MINUTE))
    }

    @Test
    fun theStatesThatNeedTheUser_areRed_andSayWhy() {
        assertEquals(ScoutLevel.BAD, ScoutStatusText.level(ScoutState.BluetoothOff(SCOUT)))
        assertEquals("Allow Nearby devices", ScoutStatusText.word(resources, ScoutState.PermissionMissing(SCOUT), recording = false))
        val firmware = ScoutState.NeedsFirmwareUpdate(SCOUT, "0.1.0")
        assertEquals("Needs a firmware update", ScoutStatusText.word(resources, firmware, recording = false))
        assertEquals("Firmware 0.1.0 is too old for this app", detail(firmware, recording = false))
        assertEquals("Update Network Survey to use NS-A1B2", detail(ScoutState.AppUpdateNeeded(SCOUT), recording = false))
        assertEquals(ScoutLevel.BAD, ScoutStatusText.level(ScoutState.PairingLost(SCOUT)))
    }

    @Test
    fun catchingUp_countsWhatCameFromTheBuffer() {
        val state = ScoutState.CatchingUp(SCOUT, backlogRecords = 1_240, ringFillPercent = 30, dataMissed = false)
        assertEquals("1,240 records from its buffer · placed where they were heard", detail(state, recording = true))
    }

    @Test
    fun notInUse_isNeutral_withNoDetail() {
        assertEquals(ScoutLevel.OFF, ScoutStatusText.level(ScoutState.NotInUse(SCOUT)))
        assertEquals(null, detail(ScoutState.NotInUse(SCOUT), recording = false))
    }

    @Test
    fun theOrganizationPolicy_allowsUnlessSetToFalse() {
        assertTrue(ScoutPolicy.isAllowed(RestrictionsSnapshot(emptyMap())))
        assertTrue(ScoutPolicy.isAllowed(RestrictionsSnapshot(mapOf(NetworkSurveyConstants.MDM_PROPERTY_ALLOW_NS_SCOUT to true))))
        assertFalse(ScoutPolicy.isAllowed(RestrictionsSnapshot(mapOf(NetworkSurveyConstants.MDM_PROPERTY_ALLOW_NS_SCOUT to false))))
    }

    @Test
    fun savedSetupsWithWifiClientsNeedAScout() {
        assertTrue(RecordType.WIFI_CLIENTS in ScoutSetups.SCOUT_ONLY_TYPES)
        val phoneOnly = SurveySetup.custom(setOf(RecordType.WIFI), setOf(Destination.LOG_FILES))
        val clients = SurveySetup.custom(setOf(RecordType.WIFI, RecordType.WIFI_CLIENTS), setOf(Destination.LOG_FILES))
        assertEquals(listOf(clients), ScoutSetups.needingScout(listOf(SurveySetup.empty(), phoneOnly, clients)))
    }

    private fun detail(state: ScoutState, recording: Boolean, lastHeard: Long? = null) =
        ScoutStatusText.detail(resources, state, recording, lastHeard, NOW, Locale.US)

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val MINUTE = 60_000L
        val SCOUT = PairedScout(
            id = "NS-02000000A1B2", address = "02:00:00:00:A1:B2", shortId = "A1B2", boardModel = 2, modelName = "",
            firmwareVersion = "0.1.0", protocolMajor = 1, bands = listOf(1, 2), pairedAtMs = 0L,
        )
    }
}
