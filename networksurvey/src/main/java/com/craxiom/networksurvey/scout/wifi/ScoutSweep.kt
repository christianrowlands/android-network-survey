package com.craxiom.networksurvey.scout.wifi

import com.craxiom.networksurvey.board.v1.Band

/**
 * How long a Scout takes to hop every channel once, for the record window (at most one record per
 * access point per window, window = the longer of the Wi-Fi scan interval and one sweep) and for
 * the live list (every access point heard within the last sweep).
 *
 * The board's default dwell is 250 ms per 2.4 GHz channel and 150 ms per 5 GHz channel, and it
 * may double the dwell on busy channels (docs/protocol.md section 6), so the estimate assumes the
 * longer dwell: about 14 s for an ESP32-C5 hopping every channel.
 */
object ScoutSweep {
    const val DEFAULT_DWELL_24_MS = 250L
    const val DEFAULT_DWELL_5_MS = 150L

    /** The board may lengthen a busy channel's dwell up to this many times the base dwell. */
    const val BUSY_DWELL_FACTOR = 2L

    /** 2.4 GHz channels 1 to 13, which the board hops when no list is given. */
    const val CHANNELS_24 = 13

    /** The 5 GHz channels an ESP32-C5 hops (UNII-1 to UNII-3, 36 to 165). */
    const val CHANNELS_5 = 25

    /** Channel numbers up to this are 2.4 GHz. */
    private const val LAST_24_CHANNEL = 14

    /** Used before anything is known about the board: a 2.4 and 5 GHz sweep. */
    val FALLBACK_MS: Long = estimateMs(emptyList(), listOf(Band.BAND_GHZ_2_4_VALUE, Band.BAND_GHZ_5_VALUE))

    /**
     * @param channels The channels the board said it hops (`ConfigureResult.channels`), or empty
     *                 when not known yet.
     * @param bands    The board's bands (`Band` numbers), used when [channels] is empty.
     * @return The time for one sweep in milliseconds, assuming every dwell is lengthened.
     */
    @JvmStatic
    fun estimateMs(channels: List<Int>, bands: List<Int>, dwell24Ms: Long = DEFAULT_DWELL_24_MS, dwell5Ms: Long = DEFAULT_DWELL_5_MS): Long {
        val count24: Int
        val count5: Int
        if (channels.isNotEmpty()) {
            count24 = channels.count { it <= LAST_24_CHANNEL }
            count5 = channels.size - count24
        } else {
            count24 = CHANNELS_24
            count5 = if (Band.BAND_GHZ_5_VALUE in bands) CHANNELS_5 else 0
        }
        return (count24 * dwell24Ms + count5 * dwell5Ms) * BUSY_DWELL_FACTOR
    }
}
