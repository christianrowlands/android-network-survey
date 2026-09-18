package com.craxiom.networksurvey.ui.cellular.bands

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.data.band.CellularBand
import com.craxiom.networksurvey.data.band.SpectrumGroup
import java.util.Locale
import kotlin.math.abs

/**
 * Shared formatting for the band browser. Kept in one place so the row, the detail sheet and the
 * calculator results render the same numbers identically.
 */
object BandFormatting {

    /**
     * Colours for the leading spectrum bar on each row. Colour is never the only carrier of this
     * information: the group name is also rendered as text in the detail sheet and is available
     * as a sort key, so the list stays readable without colour vision.
     */
    fun spectrumColor(group: SpectrumGroup): Color = when (group) {
        SpectrumGroup.LOW -> Color(0xFF7FB77E)
        SpectrumGroup.MID -> Color(0xFF03A9F4)
        SpectrumGroup.C_BAND -> Color(0xFFE2A03F)
        SpectrumGroup.HIGH -> Color(0xFFE07A5F)
        SpectrumGroup.MMWAVE -> Color(0xFFBB6BD9)
    }

    @Composable
    fun spectrumLabel(group: SpectrumGroup): String = stringResource(
        when (group) {
            SpectrumGroup.LOW -> R.string.band_spectrum_low
            SpectrumGroup.MID -> R.string.band_spectrum_mid
            SpectrumGroup.C_BAND -> R.string.band_spectrum_cband
            SpectrumGroup.HIGH -> R.string.band_spectrum_high
            SpectrumGroup.MMWAVE -> R.string.band_spectrum_mmwave
        }
    )

    /** Drops a trailing ".0" so 2110.0 reads as "2110" but 2483.5 keeps its half. */
    fun mhz(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString()
        else String.format(Locale.US, "%.3f", value).trimEnd('0').trimEnd('.')

    fun range(range: ClosedFloatingPointRange<Double>?): String? =
        range?.let { "${mhz(it.start)} - ${mhz(it.endInclusive)}" }

    fun channels(range: IntRange?): String? = range?.let { "${it.first} - ${it.last}" }

    fun bandwidths(values: List<Double>): String? =
        values.takeIf { it.isNotEmpty() }?.joinToString(", ") { mhz(it) }

    /**
     * The duplex spacing of an FDD band, which is the single number that characterises it. Null
     * for anything that is not paired.
     *
     * Always the magnitude. Twelve bands are reverse duplex, with the downlink below the uplink
     * (B13, B14, B20, B24, B71, B103 and their NR counterparts, plus n105), and a signed
     * subtraction printed those as a negative spacing. 3GPP and the industry both quote B13 as a
     * 31 MHz spacing, not -31. Whether a band is reverse duplex is visible from the two ranges
     * themselves rather than from the sign of this number.
     */
    fun duplexSpacingMhz(band: CellularBand): Double? {
        val downlink = band.downlinkMhz ?: return null
        val uplink = band.uplinkMhz ?: return null
        if (downlink.start == uplink.start) return null
        return abs(downlink.start - uplink.start)
    }
}
