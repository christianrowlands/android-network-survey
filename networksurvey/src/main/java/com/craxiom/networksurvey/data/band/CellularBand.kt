package com.craxiom.networksurvey.data.band

/**
 * Which wireless technology an operating band belongs to.
 */
enum class BandTechnology { LTE, NR }

/**
 * Duplex arrangement of an operating band. SDL and SUL are downlink only and uplink only
 * respectively, so bands carrying those modes have a range on just one side.
 */
enum class DuplexMode { FDD, TDD, SDL, SUL }

/**
 * Coarse spectrum grouping used to colour the band list, so that scrolling it reads as spectrum
 * rather than as rows of numbers. Derived from the band's own frequency rather than stored.
 */
enum class SpectrumGroup { LOW, MID, C_BAND, HIGH, MMWAVE }

/**
 * One cellular operating band, as defined by 3GPP and rendered by the band browser.
 *
 * Instances come from the generated `cellular_bands.json` asset. See
 * `tools/bandtable/generate_band_reference.py` and the README beside it for how that asset is
 * produced and which specification tables each field comes from.
 *
 * @property resolvesLiveCells whether this band may be used to label a cell the device is
 *   currently seeing. Some bands are real, and belong in a reference a user browses, but must
 *   never label a live cell because doing so would make a widely deployed band unresolvable.
 *   [referenceOnlyReason] says which band would be harmed.
 */
data class CellularBand(
    val technology: BandTechnology,
    val number: Int,
    val name: String?,
    val duplex: DuplexMode?,
    val downlinkMhz: ClosedFloatingPointRange<Double>?,
    val uplinkMhz: ClosedFloatingPointRange<Double>?,
    val downlinkChannels: IntRange?,
    val uplinkChannels: IntRange?,
    val bandwidthsMhz: List<Double>,
    val subcarrierSpacingsKhz: List<Int>,
    val isFr2: Boolean,
    val resolvesLiveCells: Boolean,
    val referenceOnlyReason: String?,
    /**
     * The bands that cover this one completely, when a channel number can never identify it on
     * its own. Empty for every band that resolves somewhere in its range.
     */
    val shadowedBy: List<Int> = emptyList(),
) {
    /** True when no channel number can ever identify this band by itself. */
    val isFullyShadowed: Boolean
        get() = shadowedBy.isNotEmpty()

    /** The 3GPP designator, for example `n78` or `B13`. */
    val designator: String
        get() = if (technology == BandTechnology.NR) "n$number" else "B$number"

    /**
     * The range this band is primarily identified by. Downlink for everything that has one, and
     * uplink for SUL bands, which have no downlink at all. Keeping this in the model rather than
     * branching in the UI means a row can bind to one value and a test can assert it is present.
     */
    val primaryMhz: ClosedFloatingPointRange<Double>?
        get() = downlinkMhz ?: uplinkMhz

    /** The channel range matching [primaryMhz]. */
    val primaryChannels: IntRange?
        get() = if (downlinkMhz != null || uplinkMhz == null) downlinkChannels else uplinkChannels

    /** Total width of the band in MHz, or null when it has no known range. */
    val widthMhz: Double?
        get() = primaryMhz?.let { it.endInclusive - it.start }

    /** The widest channel bandwidth the band supports, used as a sort key. */
    val widestBandwidthMhz: Double?
        get() = bandwidthsMhz.maxOrNull()

    val spectrumGroup: SpectrumGroup
        get() {
            val low = primaryMhz?.start ?: return SpectrumGroup.MID
            return when {
                low < 1000 -> SpectrumGroup.LOW
                low < 3000 -> SpectrumGroup.MID
                // C-band proper stops around 4.2 GHz. Above that sit the 5 GHz and 6 GHz
                // unlicensed bands, which are not C-band and should not be coloured as it.
                low < 4200 -> SpectrumGroup.C_BAND
                low < 24000 -> SpectrumGroup.HIGH
                else -> SpectrumGroup.MMWAVE
            }
        }

    /**
     * True when this band contains [channel] on the range the band is primarily identified by.
     */
    fun containsChannel(channel: Int): Boolean = primaryChannels?.contains(channel) == true

    /** True when [frequencyMhz] falls in either the downlink or the uplink range. */
    fun containsFrequency(frequencyMhz: Double): Boolean =
        downlinkMhz?.contains(frequencyMhz) == true || uplinkMhz?.contains(frequencyMhz) == true
}
