package com.craxiom.networksurvey.data.band

/**
 * The band or bands a cell is reporting, captured so that tapping the Band field on the cellular
 * details screen can open the reference for exactly those bands.
 *
 * This carries numbers rather than the formatted string the screen shows. Parsing the display text
 * back into band numbers would work today only because this app generates that text itself, and
 * would break silently the first time the format changed.
 *
 * @property bandNumbers every band the cell could be on, in the order they are displayed. More
 *   than one is normal for NR, where a quarter of the channel space falls in several bands.
 */
data class BandTapTarget(
    val technology: BandTechnology,
    val bandNumbers: List<Int>,
) {
    val isEmpty: Boolean get() = bandNumbers.isEmpty()

    companion object {
        /** Convenience for the Java callers on the details screen. */
        @JvmStatic
        fun of(technology: BandTechnology, vararg bands: Int) =
            BandTapTarget(technology, bands.toList())
    }
}
