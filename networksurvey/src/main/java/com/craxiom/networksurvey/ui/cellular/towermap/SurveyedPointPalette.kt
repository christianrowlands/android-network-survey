package com.craxiom.networksurvey.ui.cellular.towermap

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.craxiom.networksurvey.ui.cellular.model.SurveyedPointFeatures
import com.craxiom.networksurvey.ui.theme.NsPalette
import com.craxiom.networksurvey.util.PlmnColorMapper
import com.craxiom.networksurvey.util.SignalBuckets

/**
 * Every color the survey points layer and its legend use, in one place so they cannot drift.
 * Values are ARGB ints because MapLibre expressions take them directly; the Compose side wraps
 * them in [Color].
 */
object SurveyedPointPalette {
    /** Gray for rows written before a value was recorded, or with nothing to show. */
    val UNKNOWN: Int = Color(0xFF9E9E9E).toArgb()

    /**
     * Amber for a point still waiting to upload, deep teal once it has been sent. Both are fully
     * opaque and a lightness step apart: the old pair was one cyan at two alphas, which composited
     * against the basemap and so read differently on the light and dark maps.
     */
    val PENDING: Int = Color(0xFFF59E0B).toArgb()
    val SENT: Int = Color(0xFF0F766E).toArgb()

    /** Signal bucket colors indexed by [SignalBuckets] value (0 unknown, 5 strong). */
    val BUCKETS: IntArray = intArrayOf(
        UNKNOWN,
        NsPalette.Signal.VeryWeak.toArgb(),
        NsPalette.Signal.Weak.toArgb(),
        NsPalette.Signal.Fair.toArgb(),
        NsPalette.Signal.Good.toArgb(),
        NsPalette.Signal.Strong.toArgb(),
    )

    /**
     * Cellular technology colors, shared with the tower map's serving-cell badge. These are map
     * data colors, the same on the light and dark basemaps.
     */
    val TECH_NR_COLOR = Color(0xFFA855F7)
    val TECH_LTE_COLOR = Color(0xFF009688)
    val TECH_UMTS_COLOR = Color(0xFF2196F3)
    val TECH_GSM_COLOR = Color(0xFFF97316)
    val TECH_CDMA_COLOR = Color(0xFF795548)

    private val TECH_NR: Int = TECH_NR_COLOR.toArgb()
    private val TECH_LTE: Int = TECH_LTE_COLOR.toArgb()
    private val TECH_UMTS: Int = TECH_UMTS_COLOR.toArgb()
    private val TECH_GSM: Int = TECH_GSM_COLOR.toArgb()
    private val TECH_CDMA: Int = TECH_CDMA_COLOR.toArgb()

    /** The technologies with a color, in legend order. */
    val TECHNOLOGIES: List<String> = listOf(
        SurveyedPointFeatures.TECH_NR,
        SurveyedPointFeatures.TECH_LTE,
        SurveyedPointFeatures.TECH_UMTS,
        SurveyedPointFeatures.TECH_GSM,
        SurveyedPointFeatures.TECH_CDMA,
    )

    fun tech(tech: String): Int = when (tech) {
        SurveyedPointFeatures.TECH_NR -> TECH_NR
        SurveyedPointFeatures.TECH_LTE -> TECH_LTE
        SurveyedPointFeatures.TECH_UMTS -> TECH_UMTS
        SurveyedPointFeatures.TECH_GSM -> TECH_GSM
        SurveyedPointFeatures.TECH_CDMA -> TECH_CDMA
        else -> UNKNOWN
    }

    fun bucket(bucket: Int): Int = BUCKETS.getOrElse(bucket) { UNKNOWN }

    /** Color for a hashed cell or area index; [SurveyedPointFeatures.UNKNOWN_COLOR_INDEX] is gray. */
    fun hashed(index: Int): Int =
        if (index < 0 || index >= SurveyedPointFeatures.HASH_PALETTE_SIZE) UNKNOWN else PlmnColorMapper.getColorByIndex(
            index
        ).toArgb()

    /** Provider color for a "mcc-mnc" key, matching the tower icons and honoring overrides. */
    fun plmn(plmn: String): Int {
        val parts = plmn.split("-")
        return if (parts.size == 2) PlmnColorMapper.getColorArgb(parts[0], parts[1]) else UNKNOWN
    }
}
