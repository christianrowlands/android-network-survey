package com.craxiom.networksurvey.gpstest.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.gpstest.model.GnssType
import com.craxiom.networksurvey.gpstest.model.SbasType

/**
 * A satellite system as the GNSS view names it: its flag, its short name ("GPS", "WAAS") for the
 * Status rows, and its long name ("NAVSTAR GPS (USA)") for the sky legend.
 */
data class GnssSystem(
    @DrawableRes val flag: Int,
    @StringRes val shortName: Int,
    @StringRes val legendName: Int,
)

/**
 * The one table of satellite systems, read by the sky legend and the Status page's rows, so the
 * two cannot disagree about a flag or a name.
 */
object GnssSystems {

    /** The navigation constellations, in the order the legend lists them. */
    val constellations: List<Pair<GnssType, GnssSystem>> = listOf(
        GnssType.NAVSTAR to GnssSystem(R.drawable.ic_flag_usa, R.string.gps_content_description, R.string.sky_legend_shape_navstar),
        GnssType.GLONASS to GnssSystem(R.drawable.ic_flag_russia, R.string.glonass_content_description, R.string.sky_legend_shape_glonass),
        GnssType.GALILEO to GnssSystem(R.drawable.ic_flag_european_union, R.string.galileo_content_description, R.string.sky_legend_shape_galileo),
        GnssType.BEIDOU to GnssSystem(R.drawable.ic_flag_china, R.string.beidou_content_description, R.string.sky_legend_shape_beidou),
        GnssType.QZSS to GnssSystem(R.drawable.ic_flag_japan, R.string.qzss_content_description, R.string.sky_legend_shape_qzss),
        GnssType.IRNSS to GnssSystem(R.drawable.ic_flag_india, R.string.irnss_content_description, R.string.sky_legend_shape_irnss),
    )

    /** The augmentation systems, in the order the legend lists them. */
    val augmentations: List<Pair<SbasType, GnssSystem>> = listOf(
        SbasType.WAAS to GnssSystem(R.drawable.ic_flag_usa, R.string.waas_content_description, R.string.sky_legend_shape_waas),
        SbasType.EGNOS to GnssSystem(R.drawable.ic_flag_european_union, R.string.egnos_content_description, R.string.sky_legend_shape_egnos),
        SbasType.GAGAN to GnssSystem(R.drawable.ic_flag_india, R.string.gagan_content_description, R.string.sky_legend_shape_gagan),
        SbasType.MSAS to GnssSystem(R.drawable.ic_flag_japan, R.string.msas_content_description, R.string.sky_legend_shape_msas),
        SbasType.SDCM to GnssSystem(R.drawable.ic_flag_russia, R.string.sdcm_content_description, R.string.sky_legend_shape_sdcm),
        SbasType.SNAS to GnssSystem(R.drawable.ic_flag_china, R.string.snas_content_description, R.string.sky_legend_shape_snas),
        SbasType.SACCSA to GnssSystem(R.drawable.ic_flag_icao, R.string.saccsa_content_description, R.string.sky_legend_shape_saccsa),
        SbasType.SOUTHPAN to GnssSystem(R.drawable.ic_flag_southpan, R.string.southpan_content_description, R.string.sky_legend_shape_southpan),
    )

    private val byConstellation = constellations.toMap()
    private val byAugmentation = augmentations.toMap()

    /**
     * @return The system a signal comes from: its constellation, or for SBAS its augmentation
     * system; null when unknown.
     */
    fun of(type: GnssType, sbasType: SbasType?): GnssSystem? =
        if (type == GnssType.SBAS) sbasType?.let { byAugmentation[it] } else byConstellation[type]
}
