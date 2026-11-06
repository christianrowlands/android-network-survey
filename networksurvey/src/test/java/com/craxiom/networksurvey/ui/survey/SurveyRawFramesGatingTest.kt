package com.craxiom.networksurvey.ui.survey

import com.craxiom.networksurvey.logging.RawFramesProgress
import com.craxiom.networksurvey.scout.model.ScoutState
import com.craxiom.networksurvey.scout.wifi.ScoutWifiTestData
import com.craxiom.networksurvey.survey.engine.DestinationResult
import com.craxiom.networksurvey.survey.engine.DestinationRuntime
import com.craxiom.networksurvey.survey.engine.SkipReason
import com.craxiom.networksurvey.survey.engine.SurveyRuntimeState
import com.craxiom.networksurvey.survey.engine.SurveyStartResult
import com.craxiom.networksurvey.survey.mdm.RestrictionsSnapshot
import com.craxiom.networksurvey.survey.mdm.SurveyMdmKeys
import com.craxiom.networksurvey.survey.mdm.SurveyPolicy
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.survey.model.SurveySetup
import com.craxiom.networksurvey.survey.status.LeftOutItem
import com.craxiom.networksurvey.survey.status.LeftOutItems
import com.craxiom.networksurvey.ui.survey.model.DestinationInfo
import com.craxiom.networksurvey.ui.survey.model.ItemLock
import com.craxiom.networksurvey.ui.survey.model.LocationStatus
import com.craxiom.networksurvey.ui.survey.model.SurveyInputs
import com.craxiom.networksurvey.ui.survey.model.SurveyUiStateMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Raw frames record type on the Survey screen: hidden without a paired Scout, "Needs NS-A1B2"
 * with the phone as the source, the no-destination look (never a file) without Log files, its own
 * Records line instead of a bar segment, and when the privacy question is asked.
 */
class SurveyRawFramesGatingTest {
    private val scout = ScoutWifiTestData.scout
    private val connected = ScoutState.Connected(scout, batteryPercent = null, channel = 36, framesPerSecond = 40, capturing = true, dataMissed = false)

    private fun inputs(
        scoutState: ScoutState = connected,
        types: Set<RecordType> = setOf(RecordType.WIFI, RecordType.WIFI_RAW),
        destinations: Set<Destination> = setOf(Destination.LOG_FILES),
        restrictions: Map<String, Any?> = emptyMap(),
        runtime: SurveyRuntimeState = SurveyRuntimeState(),
        raw: RawFramesProgress? = null,
    ) = SurveyInputs(
        runtime = runtime.copy(policy = SurveyPolicy.evaluate(RestrictionsSnapshot(restrictions), false)),
        userSetup = SurveySetup.custom(types, destinations),
        autoIncludePhoneState = true,
        destinationInfo = DestinationInfo(mqttConfigured = true, mqttBrokerLabel = "broker", nsAnalyticsRegistered = true),
        location = LocationStatus.Fixed(accuracyMeters = 4),
        nowMs = NOW,
        scout = scoutState,
        rawFrames = raw,
    )

    @Test
    fun `without a paired Scout nothing about Raw frames shows, even from a saved setup`() {
        val state = SurveyUiStateMapper.map(inputs(ScoutState.NotPaired, destinations = setOf(Destination.MQTT)))
        assertFalse(state.tiles.any { it.type == RecordType.WIFI_RAW })
        assertFalse(state.routing!!.rows.any { it.type == RecordType.WIFI_RAW })
        assertNull(state.sentence)
    }

    @Test
    fun `with Log files the tile is an ordinary tile and goes to files only`() {
        val state = SurveyUiStateMapper.map(inputs(destinations = setOf(Destination.LOG_FILES, Destination.MQTT)))
        val tile = state.tiles.single { it.type == RecordType.WIFI_RAW }
        assertTrue(tile.on)
        assertFalse(tile.notSentAnywhere)
        val row = state.routing!!.rows.single { it.type == RecordType.WIFI_RAW }
        assertFalse(row.notSentAnywhere)
        // MQTT says it doesn't take them, like the other Scout-only type
        assertEquals(listOf(RecordType.WIFI_RAW), state.destinations.single { it.destination == Destination.MQTT }.notSent)
    }

    @Test
    fun `MQTT only gives the tile the no-destination look and the warning sentence`() {
        val state = SurveyUiStateMapper.map(inputs(destinations = setOf(Destination.MQTT)))
        assertTrue(state.tiles.single { it.type == RecordType.WIFI_RAW }.notSentAnywhere)
        assertEquals(listOf(RecordType.WIFI_RAW), state.sentence!!.notSent)
        // Start isn't blocked: Wi-Fi still goes to MQTT
        assertNull(state.startBlock)
    }

    @Test
    fun `with this phone as the source an on tile says it needs the Scout`() {
        val state = SurveyUiStateMapper.map(inputs(ScoutState.NotInUse(scout)))
        assertEquals(scout.name, state.tiles.single { it.type == RecordType.WIFI_RAW }.needsScoutName)
    }

    @Test
    fun `a managed phone hides Raw frames unless the organization allows them`() {
        val managed = mapOf(SurveyMdmKeys.DESTINATION_MQTT to SurveyMdmKeys.VALUE_USER)
        assertFalse(SurveyUiStateMapper.map(inputs(restrictions = managed)).tiles.any { it.type == RecordType.WIFI_RAW })
        val allowed = managed + (SurveyMdmKeys.SURVEY_WIFI_RAW to SurveyMdmKeys.VALUE_USER)
        assertTrue(SurveyUiStateMapper.map(inputs(restrictions = allowed)).tiles.any { it.type == RecordType.WIFI_RAW })
    }

    @Test
    fun `while recording raw frames are not counted with the records but get their own line`() {
        val running = SurveyRuntimeState(
            destinations = mapOf(Destination.LOG_FILES to DestinationRuntime(setOf(RecordType.WIFI, RecordType.WIFI_RAW))),
            missionSessionActive = true,
            sessionStartTimeMs = NOW - 60_000,
        )
        val progress = RawFramesProgress(framesSaved = 12_480, bytesSaved = 40_000_000)
        val recording = SurveyUiStateMapper.map(inputs(runtime = running, raw = progress)).recording!!
        assertFalse(RecordType.WIFI_RAW in recording.counts)
        assertEquals(progress, recording.rawFrames)
        assertEquals(listOf(RecordType.WIFI, RecordType.WIFI_RAW), recording.types)
    }

    @Test
    fun `a survey without raw frames shows no raw line, even with a stale progress`() {
        val running = SurveyRuntimeState(destinations = mapOf(Destination.LOG_FILES to DestinationRuntime(setOf(RecordType.WIFI))))
        assertNull(SurveyUiStateMapper.map(inputs(runtime = running, raw = RawFramesProgress())).recording!!.rawFrames)
    }

    @Test
    fun `Raw frames left out for want of Log files is never a left out item while recording`() {
        val runtime = SurveyRuntimeState(
            destinations = mapOf(Destination.MQTT to DestinationRuntime(setOf(RecordType.WIFI))),
            lastResult = SurveyStartResult(
                mapOf(Destination.MQTT to DestinationResult.Started(setOf(RecordType.WIFI))),
                mapOf(RecordType.WIFI_RAW to SkipReason.NEEDS_LOG_FILES),
                recording = true,
            ),
        )
        assertTrue(LeftOutItems.of(runtime, wifiOff = false).none { it is LeftOutItem.Type })
    }

    @Test
    fun `the privacy question is asked only when raw frames are newly turned on and offered`() {
        val tiles = SurveyUiStateMapper.map(inputs(types = setOf(RecordType.WIFI))).tiles
        val withRaw = setOf(RecordType.WIFI, RecordType.WIFI_RAW)
        assertTrue(RawFramesConfirmation.isNeeded(setOf(RecordType.WIFI), withRaw, tiles))
        assertFalse(RawFramesConfirmation.isNeeded(withRaw, withRaw, tiles))
        assertFalse(RawFramesConfirmation.isNeeded(withRaw, setOf(RecordType.WIFI), tiles))
        // No tile without a Scout: nothing to ask
        val noScout = SurveyUiStateMapper.map(inputs(ScoutState.NotPaired, types = setOf(RecordType.WIFI))).tiles
        assertFalse(RawFramesConfirmation.isNeeded(setOf(RecordType.WIFI), withRaw, noScout))
        // The organization turned them on: the tile isn't the user's to change
        val orgOn = SurveyUiStateMapper.map(inputs(restrictions = mapOf(SurveyMdmKeys.SURVEY_WIFI_RAW to SurveyMdmKeys.VALUE_ON))).tiles
        assertEquals(ItemLock.ORGANIZATION, orgOn.single { it.type == RecordType.WIFI_RAW }.lock)
        assertFalse(RawFramesConfirmation.isNeeded(setOf(RecordType.WIFI), withRaw, orgOn))
    }

    private companion object {
        const val NOW = 1_792_000_000_000L
    }
}
