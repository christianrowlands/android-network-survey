package com.craxiom.networksurvey.data.band

import com.craxiom.networksurvey.util.CellularUtils
import com.craxiom.networksurvey.util.band.LteBandTable

/**
 * How a typed value was read. A bare number is a valid band number, a valid EARFCN, a valid
 * NARFCN and a valid frequency all at once, so the search never picks one silently: it reports
 * every reading that matches and labels each one.
 */
enum class BandReading { NAME, BAND_NUMBER, EARFCN, NARFCN, FREQUENCY }

/**
 * One way of reading the query, together with the bands that reading matches.
 *
 * @property frequencyMhz the frequency the channel number works out to, for the channel readings.
 *   This is always known even when no band matches, which is the point: a NARFCN always yields a
 *   frequency through the raster formula even where it cannot identify a band.
 */
data class BandMatchGroup(
    val reading: BandReading,
    val bands: List<CellularBand>,
    val value: String,
    val frequencyMhz: Double? = null,
)

/**
 * Interprets what a user typed into the band browser's search field.
 *
 * The rule throughout is that the user is never asked to declare what their number is, and the
 * app never guesses on their behalf. Every valid reading is returned, most specific first, so the
 * answer is honest about the ambiguity rather than hiding it.
 */
object BandSearch {

    /** Highest EARFCN 3GPP defines, from TS 36.101 Table 5.7.3-1. */
    const val MAX_EARFCN = 262143

    /** Highest NARFCN on the NR global raster, from TS 38.104 Table 5.4.2.1-1. */
    const val MAX_NARFCN = 3279165

    /** An explicit designator such as `n78` or `B13`, which fixes the reading with no guessing. */
    private val DESIGNATOR = Regex("""^([nb])\s*(\d{1,3})$""", RegexOption.IGNORE_CASE)
    private val FREQUENCY = Regex("""^(\d+(?:\.\d+)?)\s*(?:mhz|m)$""", RegexOption.IGNORE_CASE)
    private val GHZ = Regex("""^(\d+(?:\.\d+)?)\s*ghz$""", RegexOption.IGNORE_CASE)

    /**
     * Returns the readings that match [query], ordered most specific first. An empty query, or one
     * that matches nothing, returns an empty list and the caller shows the full catalogue.
     *
     * @param forcedReading when the user has pinned a reading, only that one is attempted for a
     *   bare number. Explicit suffixes and designators are still honoured literally.
     */
    fun interpret(
        query: String,
        bands: List<CellularBand>,
        forcedReading: BandReading? = null,
    ): List<BandMatchGroup> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        DESIGNATOR.matchEntire(trimmed)?.let { match ->
            val technology =
                if (match.groupValues[1].lowercase() == "n") BandTechnology.NR else BandTechnology.LTE
            val number = match.groupValues[2].toInt()
            val hits = bands.filter { it.technology == technology && it.number == number }
            return if (hits.isEmpty()) emptyList()
            else listOf(BandMatchGroup(BandReading.BAND_NUMBER, hits, trimmed))
        }

        (FREQUENCY.matchEntire(trimmed) ?: GHZ.matchEntire(trimmed))?.let { match ->
            val scale = if (GHZ.matches(trimmed)) 1000.0 else 1.0
            val mhz = match.groupValues[1].toDouble() * scale
            return listOf(frequencyGroup(mhz, bands, trimmed))
        }

        val groups = mutableListOf<BandMatchGroup>()
        val number = trimmed.toIntOrNull()

        if (number != null) {
            val readings = forcedReading?.let { listOf(it) }
                ?: listOf(BandReading.BAND_NUMBER, BandReading.EARFCN,
                    BandReading.NARFCN, BandReading.FREQUENCY)
            for (reading in readings) {
                groupFor(reading, number, trimmed, bands)?.let { groups.add(it) }
            }
        }

        if (forcedReading == null || number == null) {
            nameGroup(trimmed, bands)?.let { groups.add(0, it) }
        }
        return groups
    }

    private fun groupFor(
        reading: BandReading,
        number: Int,
        raw: String,
        bands: List<CellularBand>,
    ): BandMatchGroup? = when (reading) {
        BandReading.BAND_NUMBER -> bands.filter { it.number == number }
            .takeIf { it.isNotEmpty() }
            ?.let { BandMatchGroup(reading, it, raw) }

        BandReading.EARFCN -> if (number !in 0..MAX_EARFCN) null else {
            val hits = bands.filter {
                it.technology == BandTechnology.LTE && it.containsChannel(number)
            }
            BandMatchGroup(reading, hits, raw, earfcnToMhz(number, hits.firstOrNull()))
        }

        BandReading.NARFCN -> if (number !in 0..MAX_NARFCN) null else {
            val hits = bands.filter {
                it.technology == BandTechnology.NR && it.containsChannel(number)
            }
            val mhz = CellularUtils.narfcnToFrequencyMhz(number).takeIf { it >= 0 }
            if (hits.isEmpty() && mhz == null) null
            else BandMatchGroup(reading, hits, raw, mhz)
        }

        // No takeIf here on purpose. A frequency that lands in no band is an answer worth
        // showing, and the explicit-unit path above already behaves that way, so suppressing it
        // would make a pinned reading disagree with typing "5000 MHz".
        BandReading.FREQUENCY -> frequencyGroup(number.toDouble(), bands, raw)

        BandReading.NAME -> null
    }

    private fun frequencyGroup(mhz: Double, bands: List<CellularBand>, raw: String) =
        BandMatchGroup(
            BandReading.FREQUENCY,
            bands.filter { it.containsFrequency(mhz) },
            raw,
            mhz,
        )

    private fun nameGroup(query: String, bands: List<CellularBand>): BandMatchGroup? {
        val needle = query.lowercase()
        if (needle.length < 2) return null
        val hits = bands.filter { band ->
            band.name?.lowercase()?.contains(needle) == true ||
                    band.designator.lowercase() == needle ||
                    band.duplex?.name?.lowercase() == needle
        }
        return hits.takeIf { it.isNotEmpty() }
            ?.let { BandMatchGroup(BandReading.NAME, it, query) }
    }

    /**
     * Converts an LTE EARFCN to its downlink frequency.
     *
     * Delegates to [LteBandTable], which owns the per-band constants, so the browser and the
     * calculator cannot produce different frequencies for the same channel. The band argument is
     * kept for call site clarity but the lookup does not need it.
     */
    fun earfcnToMhz(earfcn: Int, band: CellularBand?): Double? {
        if (band == null) return null
        return LteBandTable.downlinkEarfcnToFrequencyMhz(earfcn).takeIf { it >= 0 }
    }
}
