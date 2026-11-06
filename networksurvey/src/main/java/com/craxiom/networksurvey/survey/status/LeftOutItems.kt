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

    /**
     * The record types the NS Scout [scoutName] gives ([types]: Wi-Fi, Wi-Fi clients and Raw
     * frames, those the survey runs, in that order) aren't being recorded because of [outage].
     * They stay in the survey and record again as soon as the Scout is back. [alsoBluetooth] when
     * Bluetooth being off also leaves out Bluetooth: one item says all of them.
     */
    data class Scout(
        val scoutName: String,
        val outage: ScoutOutage,
        val alsoBluetooth: Boolean = false,
        val types: List<RecordType> = listOf(RecordType.WIFI),
    ) : LeftOutItem
}

/** Why Wi-Fi from the NS Scout isn't being recorded right now. */
enum class ScoutOutage {
    /** Still not connected after the connect grace; the Scout keeps a backlog while it can. */
    OUT_OF_RANGE,

    /** The phone needs Bluetooth to reach the Scout. */
    BLUETOOTH_OFF,

    /** The Nearby devices permission (BLUETOOTH_CONNECT) is missing. */
    PERMISSION_MISSING,

    /** The Scout speaks an older protocol than this app. */
    NEEDS_FIRMWARE_UPDATE,

    /** The Scout speaks a newer protocol than this app. */
    APP_UPDATE_NEEDED,

    /** The Scout or Android lost the pairing (or it was refused); the user has to pair again. */
    PAIRING_LOST,

    /** The Scout answers but won't finish connecting; unplugging it for 5 seconds is the fix. */
    CANT_CONNECT,

    /** A firmware update holds the Scout; it records again once the update ends. */
    UPDATING_FIRMWARE,
}

/**
 * The NS Scout as the survey's Wi-Fi source.
 *
 * @property scoutName The Scout's name, "NS-ABCD".
 * @property outage Why Wi-Fi from it isn't being recorded, null while it is (or while the
 * survey's 30 second connect grace runs).
 */
data class ScoutWifiStatus(val scoutName: String, val outage: ScoutOutage?)

/**
 * What the running survey asked for and isn't getting, whoever started it (the Survey screen, a
 * boot, another app or the organization). The Survey screen's attention plate and the survey
 * notification both list these, so a survey started without the screen says what it left out too.
 */
object LeftOutItems {

    /**
     * @param runtime The running survey, with the result of its latest start, change or retry.
     * @param wifiOff True when Wi-Fi and Wi-Fi scanning are both off, so Wi-Fi, which stays in the
     *                survey, gets no records until one of them is turned on. Ignored while the
     *                Scout is the Wi-Fi source: the phone's Wi-Fi radio isn't used then.
     * @param scout   The Scout when it is the Wi-Fi source, null when the phone is.
     * @return The items, Wi-Fi first, or nothing when no survey is running.
     */
    @JvmOverloads
    fun of(runtime: SurveyRuntimeState, wifiOff: Boolean, scout: ScoutWifiStatus? = null): List<LeftOutItem> {
        if (!runtime.isRecording) return emptyList()
        val runningTypes = runtime.recordTypes
        val wifiRunning = RecordType.WIFI in runningTypes
        val scoutTypes = SCOUT_TYPES.filter { it in runningTypes }
        val phoneWifi = LeftOutItem.Type(RecordType.WIFI, SkipReason.RADIO_OFF).takeIf { scout == null && wifiOff && wifiRunning }
        val outage = scout?.outage?.takeIf { scoutTypes.isNotEmpty() }
        val result = runtime.lastResult

        // An item drops off as soon as it runs; the result is kept until the next request
        val types = result?.recordTypeProblems.orEmpty()
            .filter { (type, reason) -> type !in runningTypes && reason.isFixable() }
            .map { (type, reason) -> LeftOutItem.Type(type, reason) }
        val destinations = result?.destinations.orEmpty()
            .mapNotNull { (destination, outcome) -> (outcome as? DestinationResult.Skipped)?.let { destination to it.reason } }
            .filter { (destination, reason) -> destination !in runtime.destinations && reason.isFixable() }
            .map { (destination, reason) -> LeftOutItem.Dest(destination, reason) }

        // Bluetooth off leaves out the Scout's types and Bluetooth: one item, one fix. Bluetooth is
        // either left out at the start or running with its radio turned off since.
        val bluetoothOff = types.firstOrNull { it.type == RecordType.BLUETOOTH && it.reason == SkipReason.RADIO_OFF }
        val merge = outage == ScoutOutage.BLUETOOTH_OFF && (bluetoothOff != null || RecordType.BLUETOOTH in runningTypes)
        val scoutItem = outage?.let { LeftOutItem.Scout(scout.scoutName, it, alsoBluetooth = merge, types = scoutTypes) }
        return listOfNotNull(phoneWifi, scoutItem) + types.filter { !merge || it != bluetoothOff } + destinations
    }

    /** The record types that come from the Scout while it is the Wi-Fi source, in the order they are named. */
    private val SCOUT_TYPES = RecordType.entries.filter { it == RecordType.WIFI || it.needsScout }

    /**
     * Organization blocks and empty destinations have nothing to act on, so they are never listed.
     * Neither is a Scout-only type without the Scout as the Wi-Fi source: the source is locked
     * while recording, and the type's tile already says it needs the Scout. Nor Raw frames without
     * Log files, which the tile also says. Nor a Scout-only type another app asked for: only the
     * person using the phone or the organization can turn it on, and the survey isn't short of
     * anything they chose.
     */
    private fun SkipReason.isFixable(): Boolean = this != SkipReason.BLOCKED_BY_POLICY &&
        this != SkipReason.TURNED_OFF_BY_ORGANIZATION && this != SkipReason.RECEIVES_NOTHING &&
        this != SkipReason.NEEDS_SCOUT && this != SkipReason.NEEDS_LOG_FILES &&
        this != SkipReason.NOT_ALLOWED_FROM_INTENT
}
