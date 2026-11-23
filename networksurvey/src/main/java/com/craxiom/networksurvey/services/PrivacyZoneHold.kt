package com.craxiom.networksurvey.services

import com.craxiom.messaging.DeviceStatus
import com.craxiom.messaging.DeviceStatusData
import com.craxiom.networksurvey.privacy.PrivacyZone
import com.craxiom.networksurvey.privacy.PrivacyZoneGate
import com.craxiom.networksurvey.privacy.PrivacyZoneRules
import com.craxiom.networksurvey.scout.wifi.PhoneFix

/**
 * What the record pipeline holds back because of privacy zones, read from the [PrivacyZoneGate].
 *
 * - [isHolding]: the phone's latest decision. While it is true no record reaches a sink (see
 *   [RecordListeners]).
 * - [holdsPlace]: for a record that carries its own capture-time fix instead of the phone's latest
 *   one (an NS Scout record, possibly heard minutes ago while out of range), its own place counts
 *   too, so a record heard inside a zone stays out even when it arrives after the phone left.
 * - [withoutLocationInZone]: device status keeps going out inside a zone, with every location
 *   field cleared. Outside the hold each of its three locations (primary, GNSS, network) is still
 *   checked on its own, because the GNSS and network ones are each provider's last fix, kept
 *   indefinitely, so they can still be a fix taken inside a zone after the primary one left it.
 */
class PrivacyZoneHold(private val gate: PrivacyZoneGate) {

    /** @return True while the phone is in a privacy zone, so no record may reach a sink. */
    fun isHolding(): Boolean = gate.isPausedForZone()

    /**
     * @param accuracyMeters The fix's accuracy; null or not positive when unknown.
     * @return True while the phone is in a zone, or when the record's own fix is accurate enough to
     * decide (500 m or better) and inside a zone in force.
     */
    fun holdsPlace(latitude: Double, longitude: Double, accuracyMeters: Float?): Boolean {
        if (isHolding()) return true
        if (accuracyMeters == null || accuracyMeters <= 0f || accuracyMeters > PrivacyZoneRules.MAX_FIX_ACCURACY_METERS) return false
        if (isNoLocation(latitude, longitude)) return false
        val zones = gate.activeZones()
        return zones.isNotEmpty() && PrivacyZoneRules.zoneContaining(zones, latitude, longitude, accuracyMeters) != null
    }

    /** @return [holdsPlace] for an NS Scout capture placed by [fix] (null when no fix was near it). */
    fun holdsFix(fix: PhoneFix?): Boolean =
        if (fix == null) isHolding() else holdsPlace(fix.latitude, fix.longitude, fix.accuracy)

    /**
     * While holding, clears every location in [status]. Otherwise clears each location block
     * (primary, GNSS, network) whose position may be inside a zone in force, by the fail-closed
     * rule ([PrivacyZoneRules.zoneContaining]) with no accuracy limit: unlike [holdsPlace], a
     * coarse fix whose circle touches a zone is cleared too, since clearing a location loses
     * nothing but that location.
     *
     * @return [status] as is when nothing needs clearing, otherwise a copy with those blocks cleared.
     */
    fun withoutLocationInZone(status: DeviceStatus): DeviceStatus {
        if (isHolding()) return withoutLocation(status)
        val zones = gate.activeZones()
        if (zones.isEmpty()) return status

        val data = status.data
        val inZone = LocationBlock.entries.filter { it.isInZone(data, zones) }
        if (inZone.isEmpty()) return status
        return withoutBlocks(status, inZone)
    }

    /**
     * One location in a device status: how to read its position and accuracy, and the fields to
     * clear (position, altitude, accuracy, age, and for the primary one speed and the mock flag).
     */
    private enum class LocationBlock(
        private val latitude: (DeviceStatusData) -> Double,
        private val longitude: (DeviceStatusData) -> Double,
        private val accuracy: (DeviceStatusData) -> Int,
        val clear: (DeviceStatusData.Builder) -> Unit,
    ) {
        PRIMARY(
            { it.latitude }, { it.longitude }, { it.accuracy },
            {
                it.clearLatitude().clearLongitude().clearAltitude().clearAccuracy().clearLocationAge().clearSpeed()
                    .clearMockLocation()
            },
        ),
        GNSS(
            { it.gnssLatitude }, { it.gnssLongitude }, { it.gnssAccuracy },
            { it.clearGnssLatitude().clearGnssLongitude().clearGnssAltitude().clearGnssAccuracy().clearGnssAge() },
        ),
        NETWORK(
            { it.networkLatitude }, { it.networkLongitude }, { it.networkAccuracy },
            {
                it.clearNetworkLatitude().clearNetworkLongitude().clearNetworkAltitude().clearNetworkAccuracy()
                    .clearNetworkAge()
            },
        );

        /** @return True if this block has a position that, widened by its accuracy, may be inside a zone. */
        fun isInZone(data: DeviceStatusData, zones: List<PrivacyZone>): Boolean {
            val lat = latitude(data)
            val lon = longitude(data)
            if (isNoLocation(lat, lon)) return false
            // A missing or non-positive accuracy counts as zero
            val accuracyMeters = accuracy(data).coerceAtLeast(0).toFloat()
            return PrivacyZoneRules.zoneContaining(zones, lat, lon, accuracyMeters) != null
        }
    }

    companion object {
        /**
         * @return [status] with its primary, GNSS and network locations cleared (position, altitude,
         * accuracy, age, speed and the mock flag), everything else kept.
         */
        @JvmStatic
        fun withoutLocation(status: DeviceStatus): DeviceStatus = withoutBlocks(status, LocationBlock.entries)

        private fun withoutBlocks(status: DeviceStatus, blocks: List<LocationBlock>): DeviceStatus {
            val data: DeviceStatusData.Builder = status.data.toBuilder()
            blocks.forEach { it.clear(data) }
            return status.toBuilder().setData(data).build()
        }

        /** 0, 0 is how a record or block without a location reads. */
        private fun isNoLocation(latitude: Double, longitude: Double): Boolean = latitude == 0.0 && longitude == 0.0
    }
}
