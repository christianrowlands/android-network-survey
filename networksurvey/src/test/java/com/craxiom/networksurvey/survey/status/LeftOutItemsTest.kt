package com.craxiom.networksurvey.survey.status

import com.craxiom.networksurvey.survey.engine.DestinationResult
import com.craxiom.networksurvey.survey.engine.DestinationRuntime
import com.craxiom.networksurvey.survey.engine.SkipReason
import com.craxiom.networksurvey.survey.engine.SurveyRuntimeState
import com.craxiom.networksurvey.survey.engine.SurveyStartResult
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests what a running survey lists as left out, for the Survey screen and the notification.
 */
class LeftOutItemsTest {

    private val result = SurveyStartResult(
        destinations = mapOf(
            Destination.LOG_FILES to DestinationResult.Started(setOf(RecordType.CELLULAR)),
            Destination.MQTT to DestinationResult.Skipped(SkipReason.NOT_CONFIGURED),
            Destination.GRPC to DestinationResult.Skipped(SkipReason.TURNED_OFF_BY_ORGANIZATION),
        ),
        recordTypeProblems = mapOf(RecordType.BLUETOOTH to SkipReason.RADIO_OFF),
        recording = true,
    )

    private fun running(types: Set<RecordType>) = SurveyRuntimeState(
        destinations = mapOf(Destination.LOG_FILES to DestinationRuntime(types)),
        lastResult = result,
    )

    @Test
    fun `lists what was left out and can be fixed, whoever started the survey`() {
        assertEquals(
            listOf(
                LeftOutItem.Type(RecordType.BLUETOOTH, SkipReason.RADIO_OFF),
                LeftOutItem.Dest(Destination.MQTT, SkipReason.NOT_CONFIGURED),
            ),
            LeftOutItems.of(running(setOf(RecordType.CELLULAR)), wifiOff = false),
        )
    }

    @Test
    fun `an item drops off once it runs, and Wi-Fi off is listed while Wi-Fi is in the survey`() {
        val items = LeftOutItems.of(running(setOf(RecordType.CELLULAR, RecordType.WIFI, RecordType.BLUETOOTH)), wifiOff = true)
        assertEquals(
            listOf(
                LeftOutItem.Type(RecordType.WIFI, SkipReason.RADIO_OFF),
                LeftOutItem.Dest(Destination.MQTT, SkipReason.NOT_CONFIGURED),
            ),
            items,
        )
    }

    @Test
    fun `nothing is listed while no survey runs`() {
        assertTrue(LeftOutItems.of(SurveyRuntimeState(lastResult = result), wifiOff = true).isEmpty())
    }

    private val scoutOut = ScoutWifiStatus("NS-ABCD", ScoutOutage.OUT_OF_RANGE)

    @Test
    fun `a Scout outage keeps Wi-Fi in the survey and says why, without the phone Wi-Fi off item`() {
        val items = LeftOutItems.of(running(setOf(RecordType.CELLULAR, RecordType.WIFI, RecordType.BLUETOOTH)), wifiOff = true, scout = scoutOut)

        assertEquals(
            listOf(
                LeftOutItem.Scout("NS-ABCD", ScoutOutage.OUT_OF_RANGE),
                LeftOutItem.Dest(Destination.MQTT, SkipReason.NOT_CONFIGURED),
            ),
            items,
        )
    }

    @Test
    fun `phone Wi-Fi off is never listed while the Scout is the source, even when the Scout is fine`() {
        val items = LeftOutItems.of(running(setOf(RecordType.WIFI, RecordType.BLUETOOTH)), wifiOff = true, scout = ScoutWifiStatus("NS-ABCD", null))

        assertEquals(listOf(LeftOutItem.Dest(Destination.MQTT, SkipReason.NOT_CONFIGURED)), items)
    }

    @Test
    fun `Bluetooth off leaves out Wi-Fi from the Scout and Bluetooth in one merged item`() {
        val items = LeftOutItems.of(
            running(setOf(RecordType.CELLULAR, RecordType.WIFI)),
            wifiOff = false,
            scout = ScoutWifiStatus("NS-ABCD", ScoutOutage.BLUETOOTH_OFF),
        )

        assertEquals(
            listOf(
                LeftOutItem.Scout("NS-ABCD", ScoutOutage.BLUETOOTH_OFF, alsoBluetooth = true),
                LeftOutItem.Dest(Destination.MQTT, SkipReason.NOT_CONFIGURED),
            ),
            items,
        )
    }

    @Test
    fun `Bluetooth turned off mid survey merges every Scout type and Bluetooth into one item`() {
        // Bluetooth started fine, so the start result has no Bluetooth problem; it is still running
        val clean = SurveyStartResult(mapOf(Destination.LOG_FILES to DestinationResult.Started(setOf())), emptyMap(), recording = true)
        val runtime = SurveyRuntimeState(
            destinations = mapOf(
                Destination.LOG_FILES to DestinationRuntime(
                    setOf(RecordType.WIFI, RecordType.BLUETOOTH, RecordType.WIFI_CLIENTS, RecordType.WIFI_RAW),
                ),
            ),
            lastResult = clean,
        )

        val items = LeftOutItems.of(runtime, wifiOff = false, scout = ScoutWifiStatus("NS-ABCD", ScoutOutage.BLUETOOTH_OFF))

        assertEquals(
            listOf(
                LeftOutItem.Scout(
                    "NS-ABCD", ScoutOutage.BLUETOOTH_OFF, alsoBluetooth = true,
                    types = listOf(RecordType.WIFI, RecordType.WIFI_CLIENTS, RecordType.WIFI_RAW),
                ),
            ),
            items,
        )
    }

    @Test
    fun `an outage names only the Scout types the survey runs`() {
        val items = LeftOutItems.of(running(setOf(RecordType.CELLULAR, RecordType.WIFI_CLIENTS)), wifiOff = false, scout = scoutOut)

        assertEquals(listOf(RecordType.WIFI_CLIENTS), (items.first() as LeftOutItem.Scout).types)
    }

    @Test
    fun `a Scout type another app was refused is not listed`() {
        val refused = SurveyRuntimeState(
            destinations = mapOf(Destination.LOG_FILES to DestinationRuntime(setOf(RecordType.WIFI))),
            lastResult = SurveyStartResult(
                destinations = emptyMap(),
                recordTypeProblems = mapOf(RecordType.WIFI_RAW to SkipReason.NOT_ALLOWED_FROM_INTENT),
                recording = true,
            ),
        )

        assertTrue(LeftOutItems.of(refused, wifiOff = false).isEmpty())
    }

    @Test
    fun `a Scout outage is not listed when the survey has no Wi-Fi`() {
        val items = LeftOutItems.of(running(setOf(RecordType.CELLULAR)), wifiOff = false, scout = scoutOut)

        assertTrue(items.none { it is LeftOutItem.Scout })
    }
}
