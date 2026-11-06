package com.craxiom.networksurvey.scout.raw

import com.craxiom.networksurvey.board.v1.Band
import com.craxiom.networksurvey.board.v1.FramePhy
import com.craxiom.networksurvey.board.v1.RawFrame

/**
 * A radiotap header made from a Scout's receive metadata, a byte for byte port of the iOS app's
 * `Radiotap`. The Scout sends frames without one (protocol.md section 9), so the app builds it:
 * Flags (FCS at end), the legacy Rate, Channel (frequency and band/modulation flags), the dBm
 * antenna signal, the dBm noise when known, and MCS for HT frames. Fields are aligned to their size,
 * counting from the start of the header.
 */
object Radiotap {
    private const val FIXED_LENGTH = 8

    private const val PRESENT_FLAGS = 1
    private const val PRESENT_RATE = 2
    private const val PRESENT_CHANNEL = 3
    private const val PRESENT_ANTENNA_SIGNAL = 5
    private const val PRESENT_ANTENNA_NOISE = 6
    private const val PRESENT_MCS = 19

    private const val FLAG_FCS_AT_END = 0x10
    private const val CHANNEL_CCK = 0x0020
    private const val CHANNEL_OFDM = 0x0040
    private const val CHANNEL_2GHZ = 0x0080
    private const val CHANNEL_5GHZ = 0x0100

    /** A frequency above this without a band is a 5 GHz (or 6 GHz) channel. */
    private const val FIVE_GHZ_FLOOR_MHZ = 5000

    /** MCS known: the index is valid. */
    private const val MCS_KNOWN_INDEX = 0x02

    /** The 802.11b rates (1, 2, 5.5 and 11 Mb/s in 500 kb/s units), which use CCK. */
    private val CCK_RATES = setOf(2, 4, 11, 22)

    /** @return The radiotap header for [frame]. */
    fun header(frame: RawFrame): ByteArray {
        var present = 0
        val fields = LittleEndianBytes()
        fun align(size: Int) {
            while ((FIXED_LENGTH + fields.size) % size != 0) fields.u8(0)
        }

        present = present or (1 shl PRESENT_FLAGS)
        fields.u8(if (frame.fcsIncluded) FLAG_FCS_AT_END else 0)

        val legacy = frame.phy == FramePhy.FRAME_PHY_LEGACY || frame.phy == FramePhy.FRAME_PHY_UNSPECIFIED
        val legacyRate = if (legacy) frame.rate500Kbps else 0
        if (legacyRate != 0) {
            present = present or (1 shl PRESENT_RATE)
            fields.u8(legacyRate)
        }

        if (frame.frequencyMhz != 0) {
            present = present or (1 shl PRESENT_CHANNEL)
            align(Short.SIZE_BYTES)
            fields.u16(frame.frequencyMhz)
            var flags = when (frame.band) {
                Band.BAND_GHZ_5, Band.BAND_GHZ_6 -> CHANNEL_5GHZ or CHANNEL_OFDM
                else -> CHANNEL_2GHZ or (if (legacyRate in CCK_RATES) CHANNEL_CCK else CHANNEL_OFDM)
            }
            if (frame.band == Band.BAND_UNSPECIFIED && frame.frequencyMhz > FIVE_GHZ_FLOOR_MHZ) {
                flags = CHANNEL_5GHZ or CHANNEL_OFDM
            }
            fields.u16(flags)
        }

        present = present or (1 shl PRESENT_ANTENNA_SIGNAL)
        fields.u8(clampToByte(frame.rssi))

        if (frame.hasNoiseFloorDbm()) {
            present = present or (1 shl PRESENT_ANTENNA_NOISE)
            fields.u8(clampToByte(frame.noiseFloorDbm))
        }

        if (frame.phy == FramePhy.FRAME_PHY_HT && frame.hasMcs()) {
            present = present or (1 shl PRESENT_MCS)
            fields.u8(MCS_KNOWN_INDEX).u8(0).u8(frame.mcs)
        }

        val body = fields.toByteArray()
        return LittleEndianBytes()
            .u8(0) // version
            .u8(0) // pad
            .u16(FIXED_LENGTH + body.size)
            .u32(present)
            .bytes(body)
            .toByteArray()
    }

    /** The signed dBm value as one byte, clamped to -128..127 as Swift's `Int8(clamping:)` does. */
    private fun clampToByte(value: Int): Int = value.coerceIn(Byte.MIN_VALUE.toInt(), Byte.MAX_VALUE.toInt())
}
