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
}
