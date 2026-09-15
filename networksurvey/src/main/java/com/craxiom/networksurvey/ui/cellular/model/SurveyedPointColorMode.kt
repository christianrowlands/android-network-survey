package com.craxiom.networksurvey.ui.cellular.model

import com.craxiom.networksurvey.logging.db.dao.DominantCategory
import com.craxiom.networksurvey.logging.db.model.SurveyedPointEntity
import org.maplibre.geojson.Feature

/**
 * What the hue of a survey point means. [cellularOnly] modes have no meaning for Wi-Fi or
 * Bluetooth points; [category] names the grouping the zoomed-out view needs, or null when the
 * plain lattice query already carries the value (signal, sent status).
 */
enum class SurveyedPointColorMode(val cellularOnly: Boolean, val category: DominantCategory?) {
    SIGNAL(false, null),
    TECHNOLOGY(true, DominantCategory.TECHNOLOGY),
    PROVIDER(true, DominantCategory.PROVIDER),
    SENT(false, null),
    CELL(true, DominantCategory.CELL),
    AREA(true, DominantCategory.AREA);

    /** True for the modes listed under the "Advanced" heading in the options sheet. */
    val advanced: Boolean get() = this == CELL || this == AREA
}

/** Which kind of survey point is drawn; one at a time because every kind shares the same spots. */
enum class SurveyedPointKind(val mask: Int) {
    CELLULAR(SurveyedPointEntity.OBSERVED_CELLULAR),
    WIFI(SurveyedPointEntity.OBSERVED_WIFI),
    BLUETOOTH(SurveyedPointEntity.OBSERVED_BLUETOOTH);

    companion object {
        fun fromMask(mask: Int): SurveyedPointKind? = entries.firstOrNull { it.mask == mask }
    }
}

/** The "When surveyed" filter. [THIS_SURVEY] is only offered when an NS Analytics survey has run. */
enum class SurveyedPointTimeFilter(val windowMs: Long) {
    ANY(0),
    LAST_HOUR(60L * 60 * 1000),
    LAST_7_DAYS(7L * 24 * 60 * 60 * 1000),
    THIS_SURVEY(0),
}

/** The "Collected for" filter, a mask over the {@code SOURCE_} constants. */
enum class SurveyedPointSourceFilter(val mask: Int) {
    BOTH(SurveyedPointEntity.SOURCE_ANY),
    COMMUNITY(SurveyedPointEntity.SOURCE_COMMUNITY),
    NS_ANALYTICS(SurveyedPointEntity.SOURCE_NS_ANALYTICS),
}

/** What the user tapped on the survey points layer. */
sealed class SurveyedPointTap {
    /** One or more individual points under the tap. */
    data class Points(val ids: List<Long>) : SurveyedPointTap()

    /** One zoomed-out lattice cell summarizing [count] points. */
    data class Cell(val latKey: Long, val lonKey: Long, val step: Double, val count: Int) :
        SurveyedPointTap()

    companion object {
        /** Interprets the rendered features under a tap, or null when nothing usable was hit. */
        fun fromFeatures(features: List<Feature>): SurveyedPointTap? {
            if (features.isEmpty()) return null
            val first = features.first()
            if (first.getBooleanProperty(SurveyedPointFeatures.PROP_COARSE) == true) {
                return Cell(
                    latKey = first.getNumberProperty(SurveyedPointFeatures.PROP_LAT_KEY).toLong(),
                    lonKey = first.getNumberProperty(SurveyedPointFeatures.PROP_LON_KEY).toLong(),
                    step = first.getNumberProperty(SurveyedPointFeatures.PROP_STEP).toDouble(),
                    count = first.getNumberProperty(SurveyedPointFeatures.PROP_COUNT).toInt(),
                )
            }
            val ids = features.mapNotNull { f ->
                f.takeIf { it.hasProperty(SurveyedPointFeatures.PROP_ID) }
                    ?.getNumberProperty(SurveyedPointFeatures.PROP_ID)?.toLong()
            }.distinct()
            return if (ids.isEmpty()) null else Points(ids)
        }
    }
}

/**
 * The rows behind a tap, resolved from the database. [aggregateCount] is set for a lattice cell
 * tap and is the cell's full count, which can exceed the number of [points] loaded.
 */
data class SurveyedPointSelection(val points: List<SurveyedPointEntity>, val aggregateCount: Int?)

/** One entry of a data-driven legend (provider, cell, or area in view). */
data class SurveyedPointLegendKey(val key: String, val label: String?, val count: Int)
