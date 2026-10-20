package com.craxiom.networksurvey.ui.cellular.details.model

import androidx.compose.runtime.Immutable
import com.craxiom.networksurvey.data.band.BandTechnology
import com.craxiom.networksurvey.util.CellularUtils

/**
 * A Band value split for its grid cell, so a long band name never wraps the band number: the band
 * on the value line ("n41") and its name on a short line under it ("TD 2600+").
 *
 * The field's value keeps the combined text ("n41 (TD 2600+)") for copying and TalkBack.
 *
 * @param band The text on the value line.
 * @param name The band's name for the line under it, or null when there is none to show.
 */
@Immutable
data class BandDisplay(val band: String, val name: String?) {

    companion object {
        /**
         * @return The split of a Band [field], or null when it has no value. Only a single band
         * with a known name is split; several candidate bands ("n48 / n77 / n78") or an unnamed band
         * stay as they are.
         */
        fun of(field: CellField): BandDisplay? {
            val value = field.value ?: return null
            val target = field.bandTarget
            val number = target?.bandNumbers?.singleOrNull() ?: return BandDisplay(value, null)
            val split = when (target.technology) {
                BandTechnology.NR -> CellularUtils.getNrBandName(number)?.let { BandDisplay("n$number", it) }
                BandTechnology.LTE -> CellularUtils.getLteBandName(number)?.let { BandDisplay(number.toString(), it) }
            }
            return split ?: BandDisplay(value, null)
        }
    }
}
