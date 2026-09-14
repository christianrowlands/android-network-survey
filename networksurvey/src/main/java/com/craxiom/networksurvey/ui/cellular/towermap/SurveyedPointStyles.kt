package com.craxiom.networksurvey.ui.cellular.towermap

import com.craxiom.networksurvey.ui.cellular.model.SurveyedPointColorMode
import com.craxiom.networksurvey.ui.cellular.model.SurveyedPointFeatures
import com.craxiom.networksurvey.util.SignalBuckets
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.expressions.Expression.color
import org.maplibre.android.style.expressions.Expression.eq
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.match
import org.maplibre.android.style.expressions.Expression.switchCase

/**
 * Builds the circle color expression for each color mode. Every expression reads a property
 * every feature carries, so changing modes never touches the feature data.
 */
object SurveyedPointStyles {

    /**
     * @param plmns the providers present in the current data; the provider expression is a
     * match over exactly those keys, like the tower icons.
     */
    fun circleColor(mode: SurveyedPointColorMode, plmns: Set<String>): Expression = when (mode) {
        SurveyedPointColorMode.SIGNAL -> match(
            get(SurveyedPointFeatures.PROP_BUCKET),
            *(SignalBuckets.VERY_WEAK..SignalBuckets.STRONG).flatMap { bucket ->
                listOf(literal(bucket), color(SurveyedPointPalette.bucket(bucket)))
            }.toTypedArray(),
            color(SurveyedPointPalette.UNKNOWN)
        )

        SurveyedPointColorMode.TECHNOLOGY -> match(
            get(SurveyedPointFeatures.PROP_TECH),
            *SurveyedPointPalette.TECHNOLOGIES.flatMap { tech ->
                listOf(literal(tech), color(SurveyedPointPalette.tech(tech)))
            }.toTypedArray(),
            color(SurveyedPointPalette.UNKNOWN)
        )

        SurveyedPointColorMode.PROVIDER -> if (plmns.isEmpty()) {
            color(SurveyedPointPalette.UNKNOWN)
        } else {
            match(
                get(SurveyedPointFeatures.PROP_PLMN),
                *plmns.flatMap { plmn ->
                    listOf(
                        literal(plmn),
                        color(SurveyedPointPalette.plmn(plmn))
                    )
                }.toTypedArray(),
                color(SurveyedPointPalette.UNKNOWN)
            )
        }

        SurveyedPointColorMode.SENT -> switchCase(
            eq(get(SurveyedPointFeatures.PROP_UPLOADED), literal(true)),
            color(SurveyedPointPalette.SENT),
            color(SurveyedPointPalette.PENDING)
        )

        SurveyedPointColorMode.CELL -> hashed(SurveyedPointFeatures.PROP_CELL_COLOR)
        SurveyedPointColorMode.AREA -> hashed(SurveyedPointFeatures.PROP_AREA_COLOR)
    }

    private fun hashed(property: String): Expression = match(
        get(property),
        *(0 until SurveyedPointFeatures.HASH_PALETTE_SIZE).flatMap { index ->
            listOf(literal(index), color(SurveyedPointPalette.hashed(index)))
        }.toTypedArray(),
        color(SurveyedPointPalette.UNKNOWN)
    )
}
