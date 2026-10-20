package com.craxiom.networksurvey.gpstest.ui.status

import com.craxiom.networksurvey.gpstest.model.SatelliteStatus
import com.craxiom.networksurvey.gpstest.util.MathUtils

/**
 * The radio bands the AGC and C/N0 trends are grouped by. Signals of different constellations on
 * one frequency (GPS L1, Galileo E1, BeiDou B1C) pass through the same part of the receiver, so a
 * jammer on that frequency shows on all of them together; they share a group.
 *
 * Titles are fixed rather than built from the carrier labels: the labels name GLONASS's own bands
 * "L1" and "L2", which would read as GPS. The titles are signal names, the same in every language.
 *
 * @param title The group's name, or null for the two groups titled from string resources.
 * @param lowMhz The lowest carrier frequency in the group.
 * @param highMhz The highest.
 */
enum class GnssBandGroup(val title: String?, private val lowMhz: Double, private val highMhz: Double) {
    /** GPS L1, Galileo E1, BeiDou B1C, QZSS L1, SBAS L1 and GLONASS L1OC, at 1575.42 MHz. */
    L1("L1 · E1 · B1C", 1574.42, 1576.42),

    /** GLONASS L1 FDMA, one frequency per channel from 1598.0625 to 1605.375 MHz. */
    GLONASS_L1("GLONASS L1", 1597.5, 1606.5),

    /** BeiDou B1I at 1561.098 MHz. */
    B1I("B1I", 1560.098, 1562.098),

    /** GPS L5, Galileo E5a, BeiDou B2a, QZSS L5 and NavIC L5, at 1176.45 MHz. */
    L5("L5 · E5a · B2a", 1175.45, 1177.45),

    /** Galileo E5b and BeiDou B2b/B2I at 1207.14 MHz. */
    E5B("E5b · B2b", 1206.14, 1208.14),

    /** GPS L2 at 1227.60 MHz. */
    L2("L2", 1226.6, 1228.6),

    /** GLONASS L2 FDMA, one frequency per channel from 1242.9375 to 1248.625 MHz. */
    GLONASS_L2("GLONASS L2", 1242.5, 1249.5),

    /** Galileo E6 and QZSS L6 at 1278.75 MHz. */
    E6("E6 · L6", 1277.75, 1279.75),

    /** BeiDou B3I at 1268.52 MHz. */
    B3I("B3I", 1267.52, 1269.52),

    /** A carrier in none of the bands above. */
    OTHER(null, 0.0, 0.0),

    /** The phone gives no carrier frequencies, so every signal is in one group. */
    ALL_SIGNALS(null, 0.0, 0.0);

    companion object {
        private val BANDS = entries.filter { it.title != null }

        /** @return The group of [status]'s carrier. */
        fun of(status: SatelliteStatus): GnssBandGroup {
            if (!status.hasCarrierFrequency) return ALL_SIGNALS
            return ofMhz(MathUtils.toMhz(status.carrierFrequencyHz))
        }

        /** @return The group of a carrier at [mhz]. */
        fun ofMhz(mhz: Double): GnssBandGroup = BANDS.firstOrNull { mhz in it.lowMhz..it.highMhz } ?: OTHER
    }
}
