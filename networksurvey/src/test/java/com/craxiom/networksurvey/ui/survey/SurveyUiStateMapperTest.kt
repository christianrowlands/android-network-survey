package com.craxiom.networksurvey.ui.survey

import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.survey.engine.DestinationResult
import com.craxiom.networksurvey.survey.engine.DestinationRuntime
import com.craxiom.networksurvey.survey.engine.LinkState
import com.craxiom.networksurvey.survey.engine.StreamLink
import com.craxiom.networksurvey.survey.engine.LastSession
import com.craxiom.networksurvey.survey.engine.LinkFailure
import com.craxiom.networksurvey.survey.engine.SurveyEndedBy
import com.craxiom.networksurvey.survey.engine.SkipReason
import com.craxiom.networksurvey.survey.engine.SurveyRuntimeState
import com.craxiom.networksurvey.survey.engine.SurveyStartResult
import com.craxiom.networksurvey.survey.mdm.RestrictionsSnapshot
import com.craxiom.networksurvey.survey.mdm.SurveyMdmKeys
import com.craxiom.networksurvey.survey.mdm.SurveyPolicy
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.survey.model.SurveySetup
import com.craxiom.networksurvey.survey.status.CommunityQueue
import com.craxiom.networksurvey.survey.status.DeliveryQueues
import com.craxiom.networksurvey.survey.status.NsAnalyticsQueue
import com.craxiom.networksurvey.survey.status.StatusAction
import com.craxiom.networksurvey.survey.status.StatusDetail
import com.craxiom.networksurvey.survey.status.StatusLevel
import com.craxiom.networksurvey.survey.status.StatusWord
import com.craxiom.networksurvey.survey.status.WaitReason
import com.craxiom.networksurvey.ui.survey.model.AttentionItemUi
import com.craxiom.networksurvey.ui.survey.model.AttentionPlateUi
import com.craxiom.networksurvey.ui.survey.model.DestinationControl
import com.craxiom.networksurvey.ui.survey.model.DestinationDetail
import com.craxiom.networksurvey.ui.survey.model.DestinationInfo
import com.craxiom.networksurvey.ui.survey.model.ItemLock
import com.craxiom.networksurvey.ui.survey.model.LastSessionUi
import com.craxiom.networksurvey.ui.survey.model.LocationStatus
import com.craxiom.networksurvey.ui.survey.model.OrgLineUi
import com.craxiom.networksurvey.ui.survey.model.RoutingColumn
import com.craxiom.networksurvey.ui.survey.model.SessionLength
import com.craxiom.networksurvey.ui.survey.model.StartBlockReason
import com.craxiom.networksurvey.ui.survey.model.SurveyInputs
import com.craxiom.networksurvey.ui.survey.model.SurveyUiState
import com.craxiom.networksurvey.ui.survey.model.SurveyUiStateMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Survey screen's rule table: what Start needs, what each tile and row shows, how the
 * organization's locks appear, and what the Recording layout and attention plates say.
 */
class SurveyUiStateMapperTest {

    private val allReady = DestinationInfo(
        nsAnalyticsRegistered = true,
        workspaceName = "Acme",
        mqttConfigured = true,
        mqttBrokerLabel = "broker.example.net",
        grpcAddress = "10.0.0.12:2621",
        communityTargets = setOf(RecordType.CELLULAR, RecordType.WIFI),
    )

    private fun inputs(
        types: Set<RecordType> = setOf(RecordType.CELLULAR, RecordType.WIFI),
        destinations: Set<Destination> = setOf(Destination.LOG_FILES),
        info: DestinationInfo = allReady,
        restrictions: Map<String, Any?> = emptyMap(),
        overrideOn: Boolean = false,
        runtime: SurveyRuntimeState = SurveyRuntimeState(),
        hidden: Set<Destination> = emptySet(),
        autoIncludePhoneState: Boolean = true,
        saved: List<SurveySetup> = emptyList(),
        selectedId: String? = null,
    ) = SurveyInputs(
        runtime = runtime.copy(policy = SurveyPolicy.evaluate(RestrictionsSnapshot(restrictions), overrideOn)),
        userSetup = SurveySetup.custom(types, destinations),
        autoIncludePhoneState = autoIncludePhoneState,
        destinationInfo = info,
        location = LocationStatus.Fixed(4),
        nowMs = NOW,
        hiddenDestinations = hidden,
        savedSetups = saved,
        selectedSetupId = selectedId,
    )

    private fun map(
        types: Set<RecordType> = setOf(RecordType.CELLULAR, RecordType.WIFI),
        destinations: Set<Destination> = setOf(Destination.LOG_FILES),
        info: DestinationInfo = allReady,
        restrictions: Map<String, Any?> = emptyMap(),
        overrideOn: Boolean = false,
        runtime: SurveyRuntimeState = SurveyRuntimeState(),
        hidden: Set<Destination> = emptySet(),
        autoIncludePhoneState: Boolean = true,
        saved: List<SurveySetup> = emptyList(),
        selectedId: String? = null,
    ): SurveyUiState = SurveyUiStateMapper.map(
        inputs(types, destinations, info, restrictions, overrideOn, runtime, hidden, autoIncludePhoneState, saved, selectedId)
    )

    // Ready: Start

    @Test
    fun `a complete setup can start and shows no warning`() {
        val state = map()
        assertNull(state.startBlock)
        assertNull(state.sentence)
    }

    @Test
    fun `no record types blocks Start`() {
        assertEquals(StartBlockReason.NO_TYPES, map(types = emptySet()).startBlock)
    }

    @Test
    fun `no destinations blocks Start`() {
        assertEquals(StartBlockReason.NO_DESTINATIONS, map(destinations = emptySet()).startBlock)
    }

    @Test
    fun `NS Analytics alone and not connected blocks Start with its own reason`() {
        val state = map(destinations = setOf(Destination.NS_ANALYTICS), info = allReady.copy(nsAnalyticsRegistered = false))
        assertEquals(StartBlockReason.NS_ANALYTICS_NOT_CONNECTED, state.startBlock)
        val row = state.destinations.first { it.destination == Destination.NS_ANALYTICS }
        assertEquals(DestinationControl.CONNECT, row.control)
        assertFalse(row.on)
        assertEquals(DestinationDetail.NotConnected, row.detail)
    }

    @Test
    fun `NS Analytics not connected does not block Start when Log files is also on`() {
        val state = map(
            destinations = setOf(Destination.NS_ANALYTICS, Destination.LOG_FILES),
            info = allReady.copy(nsAnalyticsRegistered = false),
        )
        assertNull(state.startBlock)
        assertNull(state.sentence)
    }

    @Test
    fun `a setup that delivers nothing anywhere blocks Start`() {
        val state = map(types = setOf(RecordType.CDR), destinations = setOf(Destination.MQTT))
        assertEquals(StartBlockReason.NOTHING_DELIVERED, state.startBlock)
        // The Start bar gives the reason; a warning above it would only repeat it
        assertNull(state.sentence)
    }

    @Test
    fun `a destination that gets nothing is warned about while another one lets Start go ahead`() {
        val state = map(types = setOf(RecordType.CDR), destinations = setOf(Destination.LOG_FILES, Destination.MQTT))
        assertNull(state.startBlock)
        assertEquals(listOf(Destination.MQTT), state.sentence!!.receivesNothing)
        assertTrue(state.sentence!!.notSent.isEmpty())
    }

    @Test
    fun `the warning names both a type sent nowhere and a destination that gets nothing`() {
        // NS Analytics takes GNSS; the community databases take neither type; nothing takes Call records
        val state = map(types = setOf(RecordType.GNSS, RecordType.CDR), destinations = setOf(Destination.NS_ANALYTICS, Destination.COMMUNITY))
        assertNull(state.startBlock)
        assertEquals(listOf(RecordType.CDR), state.sentence!!.notSent)
        assertEquals(listOf(Destination.COMMUNITY), state.sentence!!.receivesNothing)
    }

    @Test
    fun `the organization turning off every destination has its own reason`() {
        val restrictions = Destination.entries.associate { destinationKey(it) to SurveyMdmKeys.VALUE_OFF }
        assertEquals(StartBlockReason.ORGANIZATION_TURNED_OFF_EVERY_DESTINATION, map(restrictions = restrictions).startBlock)
    }

    @Test
    fun `all record types locked off then unlocked leaves an empty user setup that cannot start`() {
        // Seeding from an all-off organization leaves the user layer empty; once the lock lifts,
        // the empty setup must disable Start rather than start nothing.
        assertEquals(StartBlockReason.NO_TYPES, map(types = emptySet(), restrictions = emptyMap()).startBlock)
    }

    // Ready: tiles and rows

    @Test
    fun `phone state is included with Cellular and shown locked`() {
        val phone = map().tiles.first { it.type == RecordType.PHONE_STATE }
        assertTrue(phone.on)
        assertEquals(ItemLock.INCLUDED_WITH_CELLULAR, phone.lock)
    }

    @Test
    fun `phone state is its own choice when the auto include setting is off`() {
        val phone = map(autoIncludePhoneState = false).tiles.first { it.type == RecordType.PHONE_STATE }
        assertFalse(phone.on)
        assertEquals(ItemLock.NONE, phone.lock)
    }

    @Test
    fun `section counts count what is on out of what is shown`() {
        val state = map()
        assertEquals(3, state.typeCount.on)
        assertEquals(6, state.typeCount.total)
        assertEquals(1, state.destinationCount.on)
        assertEquals(5, state.destinationCount.total)
    }

    @Test
    fun `an unconfigured destination offers Set up and never looks on`() {
        val state = map(destinations = setOf(Destination.MQTT, Destination.LOG_FILES), info = allReady.copy(mqttConfigured = false))
        val mqtt = state.destinations.first { it.destination == Destination.MQTT }
        assertEquals(DestinationControl.SET_UP, mqtt.control)
        assertFalse(mqtt.on)
        assertEquals(DestinationDetail.NotSetUp, mqtt.detail)
    }

    @Test
    fun `community spells out which selected types go to which database`() {
        val bothTargets = allReady.copy(openCelliDSelected = true, beaconDbSelected = true)
        val state = map(types = setOf(RecordType.CELLULAR, RecordType.WIFI, RecordType.GNSS), destinations = setOf(Destination.COMMUNITY), info = bothTargets)
        val community = state.destinations.first { it.destination == Destination.COMMUNITY }
        assertEquals(
            DestinationDetail.CommunityRoutes(openCelliD = setOf(RecordType.CELLULAR), beaconDb = setOf(RecordType.CELLULAR, RecordType.WIFI)),
            community.detail,
        )
    }

    @Test
    fun `a stream that takes some of the selected types keeps its own description`() {
        val state = map(types = setOf(RecordType.CELLULAR, RecordType.CDR), destinations = setOf(Destination.NS_ANALYTICS))
        val nsAnalytics = state.destinations.first { it.destination == Destination.NS_ANALYTICS }
        assertEquals(DestinationDetail.Default, nsAnalytics.detail)
    }

    @Test
    fun `a type no switched-on destination takes is flagged on its tile and in the warning`() {
        val state = map(types = setOf(RecordType.CELLULAR, RecordType.CDR), destinations = setOf(Destination.NS_ANALYTICS))

        assertTrue(state.tiles.first { it.type == RecordType.CDR }.notSentAnywhere)
        assertFalse(state.tiles.first { it.type == RecordType.CELLULAR }.notSentAnywhere)
        assertEquals(listOf(RecordType.CDR), state.sentence!!.notSent)
        assertEquals(setOf(RecordType.CDR), state.routing!!.notSentAnywhere)

        val withLogFiles = map(types = setOf(RecordType.CELLULAR, RecordType.CDR), destinations = setOf(Destination.NS_ANALYTICS, Destination.LOG_FILES))
        assertTrue(withLogFiles.tiles.none { it.notSentAnywhere })
        assertNull(withLogFiles.sentence)
    }

    @Test
    fun `nothing is flagged while no destination is on`() {
        val state = map(types = setOf(RecordType.CDR), destinations = emptySet())
        assertTrue(state.tiles.none { it.notSentAnywhere })
        assertNull(state.routing)
    }

    @Test
    fun `the table splits the community databases into what each takes`() {
        val bothTargets = allReady.copy(openCelliDSelected = true, beaconDbSelected = true)
        val table = map(types = setOf(RecordType.CELLULAR, RecordType.WIFI), destinations = setOf(Destination.COMMUNITY), info = bothTargets).routing!!

        assertEquals(listOf(RoutingColumn.OPENCELLID, RoutingColumn.BEACONDB), table.columns)
        assertEquals(setOf(RoutingColumn.OPENCELLID, RoutingColumn.BEACONDB), table.rows.first { it.type == RecordType.CELLULAR }.sentTo)
        assertEquals(setOf(RoutingColumn.BEACONDB), table.rows.first { it.type == RecordType.WIFI }.sentTo)
        assertTrue(RecordType.GNSS in table.unselected)
    }

    @Test
    fun `while recording, a requested type no running destination takes shows as not sent`() {
        val runtime = recording().copy(
            destinations = mapOf(Destination.NS_ANALYTICS to DestinationRuntime(setOf(RecordType.CELLULAR))),
            requested = SurveySetup.custom(setOf(RecordType.CELLULAR, RecordType.CDR), setOf(Destination.NS_ANALYTICS)),
        )
        assertEquals(setOf(RecordType.CDR), map(runtime = runtime).recording!!.notSent)
    }

    @Test
    fun `after a survey, records still on the phone are listed until the queues are empty`() {
        val queues = DeliveryQueues(
            nsAnalytics = NsAnalyticsQueue(pending = 312),
            community = CommunityQueue(pending = 812),
        )
        val info = allReady.copy(nsAnalyticsAutoUpload = false)
        val state = SurveyUiStateMapper.map(inputs(info = info).copy(queues = queues))

        assertEquals(listOf(Destination.NS_ANALYTICS, Destination.COMMUNITY), state.stillToDo.map { it.destination })
        val nsAnalytics = state.stillToDo.first()
        assertEquals(312, nsAnalytics.pending)
        assertEquals(StatusDetail.Waiting(312, WaitReason.AutoUploadOff), nsAnalytics.status.detail)
        assertEquals(StatusAction.UPLOAD_NOW, nsAnalytics.status.action)
        assertEquals(
            StatusDetail.Waiting(812, WaitReason.NextCommunitySurvey),
            state.stillToDo.last().status.detail,
        )

        assertTrue(SurveyUiStateMapper.map(inputs(info = info)).stillToDo.isEmpty())
        assertTrue("Not while recording", SurveyUiStateMapper.map(inputs(info = info, runtime = recording()).copy(queues = queues)).stillToDo.isEmpty())
    }

    @Test
    fun `records for a destination the organization blocks are not offered for upload`() {
        val queues = DeliveryQueues(
            nsAnalytics = NsAnalyticsQueue(pending = 312),
            community = CommunityQueue(pending = 812),
        )
        val info = allReady.copy(nsAnalyticsAutoUpload = false)

        // Any managed configuration blocks the community databases unless the organization allows them
        val managed = inputs(info = info, restrictions = mapOf(MQTT_HOST_KEY to "mqtt.example.com")).copy(queues = queues)
        assertEquals(listOf(Destination.NS_ANALYTICS), SurveyUiStateMapper.map(managed).stillToDo.map { it.destination })

        val blocksNsAnalytics = inputs(
            info = info,
            restrictions = mapOf(
                NetworkSurveyConstants.MDM_PROPERTY_ALLOW_NS_ANALYTICS to false,
                NetworkSurveyConstants.MDM_PROPERTY_ALLOW_EXTERNAL_DATA_UPLOAD to true,
            ),
        ).copy(queues = queues)
        assertEquals(listOf(Destination.COMMUNITY), SurveyUiStateMapper.map(blocksNsAnalytics).stillToDo.map { it.destination })
    }

    @Test
    fun `while recording, a type the organization turned off has no chip`() {
        val restrictions = mapOf(SurveyMdmKeys.SURVEY_GNSS to SurveyMdmKeys.VALUE_OFF)
        val recording = SurveyUiStateMapper.map(inputs(restrictions = restrictions, runtime = recording())).recording!!

        assertEquals(RecordType.entries - RecordType.GNSS, recording.availableTypes)
        assertEquals(RecordType.entries, SurveyUiStateMapper.map(inputs(runtime = recording())).recording!!.availableTypes)
    }

    @Test
    fun `an upload under way shows in the list without Upload now`() {
        val queues = DeliveryQueues(nsAnalytics = NsAnalyticsQueue(pending = 312, uploading = true))
        val item = SurveyUiStateMapper.map(inputs().copy(queues = queues)).stillToDo.single()
        assertEquals(StatusWord.UPLOADING, item.status.word)
        assertNull(item.status.action)
    }

    @Test
    fun `a hidden destination stays listed while it is on, and counts as hidden otherwise`() {
        val hiddenOn = map(destinations = setOf(Destination.GRPC), hidden = setOf(Destination.GRPC))
        assertTrue(hiddenOn.destinations.any { it.destination == Destination.GRPC })
        assertEquals(0, hiddenOn.userHiddenCount)

        val hiddenOff = map(hidden = setOf(Destination.GRPC))
        assertFalse(hiddenOff.destinations.any { it.destination == Destination.GRPC })
        assertEquals(1, hiddenOff.userHiddenCount)
        assertFalse(hiddenOff.editList.first { it.destination == Destination.GRPC }.shown)
    }

    // Organization

    @Test
    fun `locked on items keep their look, locked off items are hidden and counted`() {
        val state = map(
            types = setOf(RecordType.WIFI),
            restrictions = mapOf(
                SurveyMdmKeys.SURVEY_CELLULAR to SurveyMdmKeys.VALUE_ON,
                SurveyMdmKeys.SURVEY_GNSS to SurveyMdmKeys.VALUE_OFF,
            ),
        )
        val cellular = state.tiles.first { it.type == RecordType.CELLULAR }
        assertTrue(cellular.on)
        assertEquals(ItemLock.ORGANIZATION, cellular.lock)
        assertFalse(state.tiles.any { it.type == RecordType.GNSS })
        // GNSS, plus Community: a managed phone blocks external uploads unless the admin allows them.
        assertEquals(OrgLineUi.SetsPartOfSurvey(turnedOffCount = 2), state.orgLine)
        assertTrue(state.stopNeedsConfirmation)
    }

    @Test
    fun `a broker only fleet is managed and Community is hidden by the policy gate`() {
        val state = map(restrictions = mapOf(MQTT_HOST_KEY to "mqtt.example.com"))
        assertFalse(state.destinations.any { it.destination == Destination.COMMUNITY })
        assertTrue(state.orgLine is OrgLineUi.TurnedOff)
    }

    @Test
    fun `the override line wins, and an organization update while overridden asks for Review`() {
        val restrictions = mapOf(SurveyMdmKeys.SURVEY_CELLULAR to SurveyMdmKeys.VALUE_ON)
        assertEquals(OrgLineUi.Overridden, map(restrictions = restrictions, overrideOn = true).orgLine)
        val updated = map(
            restrictions = restrictions,
            overrideOn = true,
            runtime = SurveyRuntimeState(orgUpdatedWhileOverridden = true),
        )
        assertEquals(OrgLineUi.UpdatedWhileOverridden, updated.orgLine)
        assertFalse(updated.stopNeedsConfirmation)
    }

    @Test
    fun `an unmanaged phone has no org line`() {
        val state = map()
        assertNull(state.orgLine)
        assertFalse(state.stopNeedsConfirmation)
    }

    @Test
    fun `every record type locked reads as set by the organization`() {
        val restrictions = RecordType.entries.associate { typeKey(it) to SurveyMdmKeys.VALUE_ON }
        assertTrue(map(restrictions = restrictions).typeCount.allLocked)
    }

    // Ready extras

    @Test
    fun `a last session under a minute shows its seconds, at least one`() {
        assertEquals(LastSessionUi(SessionLength.Seconds(4), 12L), lastSessionLasting(4_200L))
        assertEquals(LastSessionUi(SessionLength.Seconds(1), 12L), lastSessionLasting(100L))
        assertEquals(LastSessionUi(SessionLength.Seconds(59), 12L), lastSessionLasting(59_000L))
    }

    @Test
    fun `a last session of a minute or more rounds to whole minutes`() {
        assertEquals(LastSessionUi(SessionLength.Minutes(1), 12L), lastSessionLasting(60_000L))
        assertEquals(LastSessionUi(SessionLength.Minutes(2), 12L), lastSessionLasting(103_000L))
    }

    private fun lastSessionLasting(durationMs: Long): LastSessionUi? =
        map(runtime = SurveyRuntimeState(lastSession = LastSession(durationMs, 12L, NOW - 5_000L))).lastSession

    // Recording

    @Test
    fun `recording shows what actually runs, not the stored setup`() {
        val state = map(types = setOf(RecordType.GNSS), destinations = setOf(Destination.GRPC), runtime = recording())
        val recording = state.recording!!
        assertEquals(listOf(RecordType.CELLULAR, RecordType.WIFI), recording.types)
        assertEquals(listOf(Destination.LOG_FILES, Destination.MQTT), recording.destinations.map { it.destination })
        assertEquals(60_000L, recording.elapsedMs)
        assertEquals("NS-1", recording.missionId)
        assertNull(state.startBlock)
        assertNull(state.lastSession)
    }

    @Test
    fun `a community only survey counts what was saved for upload, not every record processed`() {
        val communityOnly = recording().copy(
            destinations = mapOf(
                Destination.COMMUNITY to DestinationRuntime(setOf(RecordType.CELLULAR, RecordType.WIFI)),
                // A stream that gave up receives nothing, so Community is the only one still running
                Destination.MQTT to DestinationRuntime(
                    setOf(RecordType.CELLULAR, RecordType.PHONE_STATE),
                    link = StreamLink(LinkState.FAILED),
                ),
            ),
            missionSessionActive = false,
        )
        val recording = SurveyUiStateMapper.map(
            inputs(runtime = communityOnly).copy(
                counts = mapOf(RecordType.CELLULAR to 400L, RecordType.PHONE_STATE to 9L, RecordType.WIFI to 900L),
                lastRecordTimeMs = NOW,
                communityCounts = mapOf(RecordType.CELLULAR to 3L, RecordType.WIFI to 40L),
                communityLastRecordTimeMs = NOW - 120_000L,
            )
        ).recording!!

        assertTrue(recording.countsSavedForUpload)
        assertEquals(43L, recording.totalRecords)
        assertEquals(mapOf(RecordType.CELLULAR to 3L, RecordType.WIFI to 40L), recording.counts)
        assertEquals(120L, recording.secondsSinceLastRecord)
        assertNull(recording.destinations.first { it.destination == Destination.COMMUNITY }.savedThisSurvey)
    }

    @Test
    fun `alongside other destinations, the community row carries the saved count`() {
        val mixed = recording().copy(
            destinations = mapOf(
                Destination.LOG_FILES to DestinationRuntime(setOf(RecordType.CELLULAR, RecordType.WIFI)),
                Destination.COMMUNITY to DestinationRuntime(setOf(RecordType.CELLULAR, RecordType.WIFI)),
            ),
        )
        val recording = SurveyUiStateMapper.map(
            inputs(runtime = mixed).copy(
                counts = mapOf(RecordType.CELLULAR to 400L, RecordType.WIFI to 900L),
                communityCounts = mapOf(RecordType.CELLULAR to 3L, RecordType.WIFI to 40L),
            )
        ).recording!!

        assertFalse(recording.countsSavedForUpload)
        assertEquals(1_300L, recording.totalRecords)
        assertEquals(43L, recording.destinations.first { it.destination == Destination.COMMUNITY }.savedThisSurvey)
        assertNull(recording.destinations.first { it.destination == Destination.LOG_FILES }.savedThisSurvey)
    }

    @Test
    fun `live rows say what is happening to each destination's records`() {
        fun mqtt(link: StreamLink) = recording().copy(
            destinations = mapOf(Destination.MQTT to DestinationRuntime(setOf(RecordType.CELLULAR), link = link))
        )
        val justStarted = mqtt(StreamLink(LinkState.CONNECTING, sinceMs = NOW - 5_000L, waiting = 40))
        assertEquals(StatusWord.CONNECTING, map(runtime = justStarted).recording!!.destinations.single().status.word)

        val dropped = mqtt(StreamLink(LinkState.CONNECTED, dropping = true))
        assertEquals(StatusWord.DROPPING, map(runtime = dropped).recording!!.destinations.single().status.word)

        val paused = map(runtime = recording().copy(pausedForBattery = true, batteryLevel = 14, batteryThresholdPercent = 15))
        assertTrue(paused.recording!!.destinations.all { it.status.word == StatusWord.PAUSED })
        assertEquals(AttentionPlateUi.BatteryPaused(14, 15), paused.plates.first())
    }

    @Test
    fun `a stream that can't connect turns red and raises the attention plate`() {
        val unreachable = recording().copy(
            destinations = mapOf(
                Destination.LOG_FILES to DestinationRuntime(setOf(RecordType.CELLULAR)),
                Destination.MQTT to DestinationRuntime(
                    setOf(RecordType.CELLULAR),
                    link = StreamLink(LinkState.CONNECTING, sinceMs = NOW - 25_000L, waiting = 340),
                ),
            )
        )
        val state = map(runtime = unreachable)
        val mqtt = state.recording!!.destinations.first { it.destination == Destination.MQTT }.status

        assertEquals(StatusWord.CANT_CONNECT, mqtt.word)
        assertEquals(StatusLevel.NOT_ARRIVING, mqtt.level)
        assertEquals(
            AttentionPlateUi.NeedsAttention(listOf(AttentionItemUi.Delivery(Destination.MQTT, mqtt))),
            state.plates.single(),
        )
    }

    @Test
    fun `NS Analytics says records wait on the phone when automatic upload is off`() {
        val runtime = recording().copy(
            destinations = mapOf(Destination.NS_ANALYTICS to DestinationRuntime(setOf(RecordType.CELLULAR)))
        )
        val inputs = inputs(runtime = runtime, info = allReady.copy(nsAnalyticsAutoUpload = false))
            .copy(queues = DeliveryQueues(nsAnalytics = NsAnalyticsQueue(pending = 312)))

        val status = SurveyUiStateMapper.map(inputs).recording!!.destinations.single().status
        assertEquals(StatusWord.NOT_UPLOADING, status.word)
        assertEquals(StatusDetail.Waiting(312, WaitReason.AutoUploadOff), status.detail)
        assertEquals(StatusAction.UPLOAD_NOW, status.action)
    }

    @Test
    fun `a survey that ended on its own says why on Ready`() {
        val ended = SurveyRuntimeState(endedBy = SurveyEndedBy(Destination.MQTT, LinkFailure.SIGN_IN_REJECTED))
        val state = map(runtime = ended)
        assertNull(state.recording)
        assertEquals(AttentionPlateUi.SurveyEnded(Destination.MQTT, LinkFailure.SIGN_IN_REJECTED), state.plates.single())
    }

    @Test
    fun `a survey that ended on invalid broker settings says so on Ready`() {
        val ended = SurveyRuntimeState(endedBy = SurveyEndedBy(Destination.MQTT, LinkFailure.INVALID_SETTINGS))
        val state = map(runtime = ended)
        assertEquals(AttentionPlateUi.SurveyEnded(Destination.MQTT, LinkFailure.INVALID_SETTINGS), state.plates.single())
    }

    @Test
    fun `dropping names who still receives, and a pause hides the stall plate`() {
        val dropping = map(runtime = recording().copy(mqttDropping = true))
        assertEquals(AttentionPlateUi.MqttDropping(listOf(Destination.LOG_FILES)), dropping.plates.single())

        val stalled = map(runtime = recording().copy(stalledMinutes = 5))
        assertEquals(AttentionPlateUi.Stalled(5), stalled.plates.single())
        val pausedAndStalled = map(runtime = recording().copy(stalledMinutes = 5, pausedForBackpressure = true))
        assertEquals(listOf(AttentionPlateUi.MqttOnlyPaused()), pausedAndStalled.plates)
    }

    @Test
    fun `the battery warns within five percent of the pause threshold, and only when it is on`() {
        fun plates(level: Int, threshold: Int) =
            map(runtime = recording().copy(batteryLevel = level, batteryThresholdPercent = threshold)).plates

        assertEquals(listOf(AttentionPlateUi.BatteryLow(20, 15)), plates(level = 20, threshold = 15))
        assertTrue(plates(level = 21, threshold = 15).isEmpty())
        assertTrue(plates(level = 3, threshold = 0).isEmpty())
        assertTrue(plates(level = -1, threshold = 15).isEmpty())

        val paused = map(runtime = recording().copy(pausedForBattery = true, batteryLevel = 15, batteryThresholdPercent = 15))
        assertEquals(listOf(AttentionPlateUi.BatteryPaused(15, 15)), paused.plates)

        // A threshold the organization sets is not offered for change
        val managed = map(
            runtime = recording().copy(batteryLevel = 18, batteryThresholdPercent = 15),
            info = allReady.copy(batteryThresholdSetByOrganization = true),
        )
        assertEquals(listOf(AttentionPlateUi.BatteryLow(18, 15, thresholdSetByOrganization = true)), managed.plates)
    }

    @Test
    fun `Wi-Fi with its radio and scanning off stays in the survey and says why nothing arrives`() {
        val wifiOff = SurveyUiStateMapper.map(inputs(runtime = recording()).copy(wifiOff = true))
        assertEquals(
            listOf(AttentionPlateUi.NeedsAttention(listOf(AttentionItemUi.Type(RecordType.WIFI, SkipReason.RADIO_OFF)))),
            wifiOff.plates,
        )

        val noWifi = recording().copy(destinations = mapOf(Destination.LOG_FILES to DestinationRuntime(setOf(RecordType.CELLULAR))))
        assertTrue(SurveyUiStateMapper.map(inputs(runtime = noWifi).copy(wifiOff = true)).plates.isEmpty())
    }

    @Test
    fun `a queue limit the organization sets is not offered for change`() {
        val paused = recording().copy(pausedForBackpressure = true)
        val managed = map(runtime = paused, info = allReady.copy(queueLimitSetByOrganization = true))
        assertEquals(listOf(AttentionPlateUi.MqttOnlyPaused(limitSetByOrganization = true)), managed.plates)
    }

    @Test
    fun `a partial start lists what still needs attention and drops items once fixed`() {
        val result = SurveyStartResult(
            destinations = mapOf(
                Destination.LOG_FILES to DestinationResult.Started(setOf(RecordType.CELLULAR)),
                Destination.GRPC to DestinationResult.Skipped(SkipReason.NOT_CONFIGURED),
            ),
            recordTypeProblems = mapOf(RecordType.WIFI to SkipReason.RADIO_OFF),
            recording = true,
        )
        val running = recording().copy(
            destinations = mapOf(Destination.LOG_FILES to DestinationRuntime(setOf(RecordType.CELLULAR))),
            lastResult = result,
        )
        val state = map(
            types = setOf(RecordType.CELLULAR, RecordType.WIFI),
            destinations = setOf(Destination.LOG_FILES, Destination.GRPC),
            runtime = running,
        )
        val items = (state.plates.single() as AttentionPlateUi.NeedsAttention).items
        assertEquals(
            listOf(AttentionItemUi.Type(RecordType.WIFI, SkipReason.RADIO_OFF), AttentionItemUi.Dest(Destination.GRPC, SkipReason.NOT_CONFIGURED)),
            items,
        )

        // Wi-Fi was turned on and added back: only gRPC is left.
        val fixed = running.copy(
            destinations = mapOf(Destination.LOG_FILES to DestinationRuntime(setOf(RecordType.CELLULAR, RecordType.WIFI)))
        )
        val after = map(
            types = setOf(RecordType.CELLULAR, RecordType.WIFI),
            destinations = setOf(Destination.LOG_FILES, Destination.GRPC),
            runtime = fixed,
        )
        assertEquals(
            listOf(AttentionItemUi.Dest(Destination.GRPC, SkipReason.NOT_CONFIGURED)),
            (after.plates.single() as AttentionPlateUi.NeedsAttention).items,
        )
    }

    @Test
    fun `everything locked means the running survey cannot be changed`() {
        val restrictions = RecordType.entries.associate { typeKey(it) to SurveyMdmKeys.VALUE_ON } +
            Destination.entries.associate { destinationKey(it) to SurveyMdmKeys.VALUE_ON }
        assertFalse(map(restrictions = restrictions, runtime = recording()).recording!!.canChange)
        assertTrue(map(runtime = recording()).recording!!.canChange)
    }

    @Test
    fun `stop warns that the organization restarts the survey at boot`() {
        val state = map(restrictions = mapOf(SurveyMdmKeys.AUTO_START_SURVEY to SurveyMdmKeys.VALUE_ON))
        assertTrue(state.stopNeedsConfirmation)
        assertTrue(state.stopRestartsAtBoot)
    }

    // Saved setups

    @Test
    fun `the chip matching the selection is selected, preferring the one last picked`() {
        val walk = saved("walk", setOf(RecordType.CELLULAR, RecordType.WIFI), setOf(Destination.LOG_FILES))
        val twin = saved("twin", setOf(RecordType.CELLULAR, RecordType.WIFI), setOf(Destination.LOG_FILES))
        val drive = saved("drive", setOf(RecordType.GNSS), setOf(Destination.LOG_FILES))

        assertEquals("walk", map(saved = listOf(drive, walk, twin)).savedSetups!!.selected!!.id)
        assertEquals("twin", map(saved = listOf(drive, walk, twin), selectedId = "twin").savedSetups!!.selected!!.id)
        // A picked setup whose content no longer matches gives way to one that does
        assertEquals("walk", map(saved = listOf(drive, walk), selectedId = "drive").savedSetups!!.selected!!.id)
        assertNull(map(types = setOf(RecordType.BLUETOOTH), saved = listOf(drive, walk)).savedSetups!!.selected)
    }

    @Test
    fun `saving needs something to record and somewhere to send it`() {
        assertTrue(map().savedSetups!!.canSave)
        assertFalse(map(types = emptySet()).savedSetups!!.canSave)
        assertFalse(map(destinations = emptySet()).savedSetups!!.canSave)
    }

    @Test
    fun `saved setups hide only when the organization sets every record type and destination`() {
        val everything = RecordType.entries.associate { typeKey(it) to SurveyMdmKeys.VALUE_OFF } +
            Destination.entries.associate { destinationKey(it) to SurveyMdmKeys.VALUE_ON }
        assertNull(map(restrictions = everything).savedSetups)
        assertNotNull(map(restrictions = everything, overrideOn = true).savedSetups)
        assertNotNull(map(restrictions = mapOf(typeKey(RecordType.CELLULAR) to SurveyMdmKeys.VALUE_ON)).savedSetups)
        assertNotNull(map(restrictions = mapOf(MQTT_HOST_KEY to "broker.example.net")).savedSetups)
    }

    private fun saved(id: String, types: Set<RecordType>, destinations: Set<Destination>) =
        SurveySetup(id, id, types, destinations)

    private fun recording() = SurveyRuntimeState(
        destinations = mapOf(
            Destination.LOG_FILES to DestinationRuntime(setOf(RecordType.CELLULAR, RecordType.WIFI)),
            Destination.MQTT to DestinationRuntime(setOf(RecordType.CELLULAR, RecordType.WIFI)),
        ),
        missionId = "NS-1",
        missionSessionActive = true,
        sessionStartTimeMs = NOW - 60_000L,
    )

    private fun typeKey(type: RecordType) = "survey_${type.wireName}"

    private fun destinationKey(destination: Destination) = "destination_${destination.wireName}"

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val MQTT_HOST_KEY = "mqtt_connection_host"
    }
}
