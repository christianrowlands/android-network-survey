package com.craxiom.networksurvey.ui.cellular.towermap

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.craxiom.networksurvey.ui.cellular.model.SurveyedPointFeatures
import com.craxiom.networksurvey.ui.theme.WifiTokens
import com.craxiom.networksurvey.util.PlmnColorMapper
import com.craxiom.networksurvey.util.SignalBuckets

/**
 * Every color the surveyed places layer and its legend use, in one place so they cannot drift.
 * Values are ARGB ints because MapLibre expressions take them directly; the Compose side wraps
 * them in [Color].
 */
object SurveyedPointPalette {
    /** Gray for rows written before a value was recorded, or with nothing to show. */
    val UNKNOWN: Int = Color(0xFF9E9E9E).toArgb()

    /** Teal for a place still waiting to upload; the same teal faded once sent. */
    val PENDING: Int = Color(0xFF00BCD4).toArgb()
    val SENT: Int = Color(0xFF00BCD4).copy(alpha = 0.35f).toArgb()

    /** Signal bucket colors indexed by [SignalBuckets] value (0 unknown, 5 strong). */
    val BUCKETS: IntArray = intArrayOf(
        UNKNOWN,
        WifiTokens.SignalVeryWeak.toArgb(),
        WifiTokens.SignalWeak.toArgb(),
        WifiTokens.SignalFair.toArgb(),
        WifiTokens.SignalGood.toArgb(),
        WifiTokens.SignalStrong.toArgb(),
    )

    private val TECH_NR: Int = Color(0xFFA855F7).toArgb()
    private val TECH_LTE: Int = Color(0xFF009688).toArgb()
    private val TECH_UMTS: Int = Color(0xFF2196F3).toArgb()
    private val TECH_GSM: Int = Color(0xFFF97316).toArgb()
    private val TECH_CDMA: Int = Color(0xFF795548).toArgb()

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
