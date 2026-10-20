package com.craxiom.networksurvey.survey.engine

import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType

/** Where a survey start, change or stop came from. */
enum class SurveyOrigin {
    /** The Survey screen (or another in-app control) operated by the person holding the phone. */
    USER,

    /** The Intent API (`START_SURVEY` / `STOP_SURVEY`) from another app. */
    INTENT,

    /** The phone booted with auto start on. */
    BOOT,

    /** The app opened with auto start on. */
    APP_OPEN,

    /** The organization changed the managed configuration, or the user restored it. */
    ORGANIZATION,
}

/** Why a destination or record type was left out of a survey. */
enum class SkipReason {
    /** The destination needs settings first (no MQTT broker, no gRPC server). */
    NOT_CONFIGURED,

    /** NS Analytics is not connected on this phone. */
    NOT_REGISTERED,

    /** A data policy gate (`allow_external_data_upload`, `allow_ns_analytics`) forbids it. */
    BLOCKED_BY_POLICY,

    /** The organization turned it off. */
    TURNED_OFF_BY_ORGANIZATION,

    /** Community databases is selected but neither OpenCelliD nor BeaconDB is. */
    NO_UPLOAD_TARGET,

    /** The destination accepts none of the record types in this survey. */
    RECEIVES_NOTHING,

    /** A permission the record type needs has not been granted. */
    PERMISSION_MISSING,

    /** The radio the record type needs (Wi-Fi, Bluetooth) is off. */
    RADIO_OFF,

    /** The destination tried to start and failed. */
    START_FAILED,
}

/** What happened to one destination when a setup was started, applied or stopped. */
sealed interface DestinationResult {
    /** The destination started and is receiving [types]. */
    data class Started(val types: Set<RecordType>) : DestinationResult

    /** The destination is connecting (MQTT, gRPC) and will receive [types] once connected. */
    data class Pending(val types: Set<RecordType>) : DestinationResult

    /** The destination was already running and now receives [types]. */
    data class Updated(val types: Set<RecordType>) : DestinationResult

    /** The destination was running and was stopped. */
    data object Stopped : DestinationResult

    /** The destination was left out, for [reason]. */
    data class Skipped(val reason: SkipReason) : DestinationResult

    /** The destination is running and nothing about it changed. */
    data object Unchanged : DestinationResult
}

/**
 * The outcome of starting, applying or stopping a survey, per destination, so the screen can say
 * which destination failed and why instead of a single yes or no.
 *
 * @property destinations What happened to each destination the request touched.
 * @property recordTypeProblems Record types left out of the survey and why (for example Wi-Fi with
 *                              the Wi-Fi radio off).
 * @property recording True if, after the request, at least one destination is running or
 *                     connecting. A start where every destination failed leaves this false and
 *                     starts nothing.
 */
data class SurveyStartResult(
    val destinations: Map<Destination, DestinationResult>,
    val recordTypeProblems: Map<RecordType, SkipReason>,
    val recording: Boolean,
) {
    /** The destinations that were left out, and why. */
    val skipped: Map<Destination, SkipReason>
        get() = destinations.mapNotNull { (destination, result) ->
            (result as? DestinationResult.Skipped)?.let { destination to it.reason }
        }.toMap()
}
