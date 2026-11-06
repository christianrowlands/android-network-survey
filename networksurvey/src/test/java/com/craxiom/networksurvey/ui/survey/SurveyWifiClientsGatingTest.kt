package com.craxiom.networksurvey.ui.survey

import com.craxiom.networksurvey.scout.model.ScoutState
import com.craxiom.networksurvey.scout.wifi.ScoutWifiTestData
import com.craxiom.networksurvey.survey.engine.DestinationResult
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
import com.craxiom.networksurvey.ui.survey.model.LocationStatus
import com.craxiom.networksurvey.ui.survey.model.SurveyInputs
import com.craxiom.networksurvey.ui.survey.model.SurveyUiStateMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Wi-Fi clients record type on the Survey screen: invisible without a paired Scout (even when a
 * saved setup holds it), a tile that says it needs the Scout while this phone is the Wi-Fi source,
 * and "not sent" on the destinations that can't take it.
 */
class SurveyWifiClientsGatingTest {
    private val scout = ScoutWifiTestData.scout
    private val connected = ScoutState.Connected(scout, batteryPercent = null, channel = 36, framesPerSecond = 40, capturing = false, dataMissed = false)
    private val allReady = DestinationInfo(
        nsAnalyticsRegistered = true, workspaceName = "Acme", mqttConfigured = true, mqttBrokerLabel = "broker",
        grpcAddress = "10.0.0.1:2621", communityTargets = setOf(RecordType.CELLULAR, RecordType.WIFI),
        openCelliDSelected = true, beaconDbSelected = true,
    )

    private fun inputs(
        scoutState: ScoutState,
        types: Set<RecordType> = setOf(RecordType.WIFI, RecordType.WIFI_CLIENTS),
        destinations: Set<Destination> = setOf(Destination.LOG_FILES),
        restrictions: Map<String, Any?> = emptyMap(),
    ) = SurveyInputs(
        runtime = SurveyRuntimeState().copy(policy = SurveyPolicy.evaluate(RestrictionsSnapshot(restrictions), false)),
        userSetup = SurveySetup.custom(types, destinations),
        autoIncludePhoneState = true,
        destinationInfo = allReady,
        location = LocationStatus.Fixed(accuracyMeters = 4),
        nowMs = NOW,
        scout = scoutState,
    )

    @Test
    fun `without a paired Scout nothing about Wi-Fi clients shows, even from a saved setup`() {
        val state = SurveyUiStateMapper.map(inputs(ScoutState.NotPaired, destinations = setOf(Destination.LOG_FILES, Destination.NS_ANALYTICS)))

        assertFalse(state.tiles.any { it.type == RecordType.WIFI_CLIENTS })
        assertEquals(RecordType.entries.count { !it.needsScout }, state.typeCount.total)
        assertEquals(1, state.typeCount.on)
        assertFalse(state.routing!!.rows.any { it.type == RecordType.WIFI_CLIENTS })
        assertFalse(RecordType.WIFI_CLIENTS in state.routing!!.unselected)
        assertTrue(state.destinations.all { it.notSent.isEmpty() })
    }

    @Test
    fun `with the Scout as the source the tile is an ordinary tile`() {
        val tile = SurveyUiStateMapper.map(inputs(connected)).tiles.single { it.type == RecordType.WIFI_CLIENTS }
        assertTrue(tile.on)
        assertNull(tile.needsScoutName)
    }

    @Test
    fun `with this phone as the source an on tile says it needs the Scout`() {
        val state = SurveyUiStateMapper.map(inputs(ScoutState.NotInUse(scout)))
        assertEquals(scout.name, state.tiles.single { it.type == RecordType.WIFI_CLIENTS }.needsScoutName)

        val off = SurveyUiStateMapper.map(inputs(ScoutState.NotInUse(scout), types = setOf(RecordType.WIFI)))
        assertNull(off.tiles.single { it.type == RecordType.WIFI_CLIENTS }.needsScoutName)
    }

    @Test
    fun `NS Analytics and the community databases say Wi-Fi clients are not sent`() {
        val all = setOf(Destination.LOG_FILES, Destination.NS_ANALYTICS, Destination.MQTT, Destination.GRPC, Destination.COMMUNITY)
        val rows = SurveyUiStateMapper.map(inputs(connected, destinations = all)).destinations.associateBy { it.destination }

        assertEquals(listOf(RecordType.WIFI_CLIENTS), rows.getValue(Destination.NS_ANALYTICS).notSent)
        assertEquals(listOf(RecordType.WIFI_CLIENTS), rows.getValue(Destination.COMMUNITY).notSent)
        for (taker in listOf(Destination.LOG_FILES, Destination.MQTT, Destination.GRPC)) {
            assertTrue(taker.name, rows.getValue(taker).notSent.isEmpty())
        }
    }

    @Test
    fun `the organization's default off on a managed phone hides the tile even with a Scout`() {
        val managed = mapOf(SurveyMdmKeys.DESTINATION_MQTT to SurveyMdmKeys.VALUE_USER)
        val state = SurveyUiStateMapper.map(inputs(connected, restrictions = managed))
        assertFalse(state.tiles.any { it.type == RecordType.WIFI_CLIENTS })
    }

    @Test
    fun `a survey that left Wi-Fi clients out for want of a Scout lists no problem while recording`() {
        val runtime = SurveyRuntimeState(
            destinations = mapOf(Destination.LOG_FILES to com.craxiom.networksurvey.survey.engine.DestinationRuntime(setOf(RecordType.WIFI))),
            lastResult = SurveyStartResult(
                mapOf(Destination.LOG_FILES to DestinationResult.Started(setOf(RecordType.WIFI))),
                mapOf(RecordType.WIFI_CLIENTS to SkipReason.NEEDS_SCOUT),
                recording = true,
            ),
        )
        assertTrue(LeftOutItems.of(runtime, wifiOff = false).none { it is LeftOutItem.Type })
    }

    private companion object {
        const val NOW = 1_792_000_000_000L
    }
}
