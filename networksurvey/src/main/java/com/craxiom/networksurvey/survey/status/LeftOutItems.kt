package com.craxiom.networksurvey.survey.status

import com.craxiom.networksurvey.survey.engine.DestinationResult
import com.craxiom.networksurvey.survey.engine.SkipReason
import com.craxiom.networksurvey.survey.engine.SurveyRuntimeState
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType

/** Something the running survey asked for that it isn't getting, and why. */
sealed interface LeftOutItem {
    data class Type(val type: RecordType, val reason: SkipReason) : LeftOutItem
    data class Dest(val destination: Destination, val reason: SkipReason) : LeftOutItem
}

/**
 * What the running survey asked for and isn't getting, whoever started it (the Survey screen, a
 * boot, another app or the organization). The Survey screen's attention plate and the survey
 * notification both list these, so a survey started without the screen says what it left out too.
 */
object LeftOutItems {

    /**
     * @param runtime The running survey, with the result of its latest start, change or retry.
     * @param wifiOff True when Wi-Fi and Wi-Fi scanning are both off, so Wi-Fi, which stays in the
     *                survey, gets no records until one of them is turned on.
     * @return The items, Wi-Fi off first, or nothing when no survey is running.
     */
    fun of(runtime: SurveyRuntimeState, wifiOff: Boolean): List<LeftOutItem> {
        if (!runtime.isRecording) return emptyList()
        val runningTypes = runtime.recordTypes
        val wifi = LeftOutItem.Type(RecordType.WIFI, SkipReason.RADIO_OFF).takeIf { wifiOff && RecordType.WIFI in runningTypes }
        val result = runtime.lastResult ?: return listOfNotNull(wifi)

        // An item drops off as soon as it runs; the result is kept until the next request
        val types = result.recordTypeProblems
            .filter { (type, reason) -> type !in runningTypes && reason.isFixable() }
            .map { (type, reason) -> LeftOutItem.Type(type, reason) }
        val destinations = result.destinations
            .mapNotNull { (destination, outcome) -> (outcome as? DestinationResult.Skipped)?.let { destination to it.reason } }
            .filter { (destination, reason) -> destination !in runtime.destinations && reason.isFixable() }
            .map { (destination, reason) -> LeftOutItem.Dest(destination, reason) }
        return listOfNotNull(wifi) + types + destinations
    }

    /** Organization blocks and empty destinations have nothing to act on, so they are never listed. */
    private fun SkipReason.isFixable(): Boolean = this != SkipReason.BLOCKED_BY_POLICY &&
        this != SkipReason.TURNED_OFF_BY_ORGANIZATION && this != SkipReason.RECEIVES_NOTHING
}
