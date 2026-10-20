package com.craxiom.networksurvey.gpstest.ui.sky

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.craxiom.networksurvey.gpstest.model.GnssType
import com.craxiom.networksurvey.gpstest.model.SbasType
import com.craxiom.networksurvey.gpstest.ui.GnssSystem
import com.craxiom.networksurvey.gpstest.ui.GnssSystems

/**
 * One row of the sky legend: the system's mark, its flag and its name.
 */
data class SkyLegendRow(val shape: SatelliteShape, @DrawableRes val flag: Int, @StringRes val name: Int)

/**
 * The tables the legend sheet is drawn from. The marks come from [SkyProjection.shape] and the flags
 * and names from [GnssSystems], so the legend cannot disagree with the plot or the Status page.
 */
object SkyLegend {

    /** The navigation constellations, in the order the legend lists them. */
    val constellations: List<Pair<GnssType, SkyLegendRow>> =
        GnssSystems.constellations.map { (type, system) -> type to row(type, system) }

    /** The augmentation systems; every one is drawn with the SBAS mark. */
    val augmentations: List<Pair<SbasType, SkyLegendRow>> =
        GnssSystems.augmentations.map { (type, system) -> type to row(GnssType.SBAS, system) }

    private fun row(type: GnssType, system: GnssSystem) =
        SkyLegendRow(SkyProjection.shape(type), system.flag, system.legendName)
}
