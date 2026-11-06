package com.craxiom.networksurvey.scout.wifi

import com.craxiom.networksurvey.model.WifiRecordWrapper
import java.util.concurrent.atomic.AtomicInteger

/**
 * Builds the beacon records of the NS Scout's aggregation windows for the record processor, so each
 * record takes one number whichever sink gets it (see [ScoutRecordNumbers]): a closed window for
 * the sinks, an open window for a sink that is leaving, and a live view snapshot that takes none.
 *
 * @param counter The Wi-Fi record number sequence, shared with the phone's own scans.
 * @param deviceId This phone's device serial number.
 * @param missionId The Mission ID to stamp, empty when no mission runs.
 * @param excluded Whether an SSID is on the user's exclusion list.
 */
class ScoutBeaconRecordBuilder(
    counter: AtomicInteger,
    private val deviceId: String,
    private val missionId: () -> String,
    private val excluded: (String) -> Boolean,
) {
    private val numbers = ScoutRecordNumbers(counter)

    /** @return The records of a closed window, for every sink. */
    fun closed(beacons: List<ScoutBeacon>): List<WifiRecordWrapper> = build(beacons) { numbers.forClose(it.slot) }

    /** @return The records an open window holds so far, for a sink that is about to leave. */
    fun peeked(beacons: List<ScoutBeacon>): List<WifiRecordWrapper> = build(beacons) { numbers.forPeek(it.slot) }

    /** @return The records of a live view snapshot, which take no record numbers. */
    fun live(beacons: List<ScoutBeacon>): List<WifiRecordWrapper> = build(beacons) { 0 }

    private fun build(beacons: List<ScoutBeacon>, recordNumber: (ScoutBeacon) -> Int): List<WifiRecordWrapper> {
        val mission = missionId()
        return beacons.mapNotNull { ScoutBeaconRecords.build(it, deviceId, mission, recordNumber(it), excluded) }
    }
}
