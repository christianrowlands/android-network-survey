package com.craxiom.networksurvey.ui.cellular.model

import com.craxiom.networksurvey.logging.db.dao.DominantCategory
import com.craxiom.networksurvey.logging.db.model.SurveyedPointCategoryCell
import com.craxiom.networksurvey.logging.db.model.SurveyedPointCell
import com.craxiom.networksurvey.logging.db.model.SurveyedPointEntity
import com.craxiom.networksurvey.ui.cellular.model.SurveyedPointFeatures.UNKNOWN_COLOR_INDEX
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point

/**
 * Builds the GeoJSON features of the survey points layer. Every feature carries the property
 * for every color mode, so switching modes only changes the layer's style expression.
 */
object SurveyedPointFeatures {
    const val PROP_ID = "id"
    const val PROP_KIND = "kind"
    const val PROP_UPLOADED = "uploaded"
    const val PROP_SORT = "sortKey"
    const val PROP_TECH = "tech"
    const val PROP_PLMN = "plmn"
    const val PROP_BUCKET = "bucket"
    const val PROP_CELL_COLOR = "cellColor"
    const val PROP_AREA_COLOR = "areaColor"
    const val PROP_SOURCE = "source"
    const val PROP_COARSE = "coarse"
    const val PROP_LAT_KEY = "latKey"
    const val PROP_LON_KEY = "lonKey"
    const val PROP_STEP = "step"
    const val PROP_COUNT = "count"

    const val TECH_NR = "NR"
    const val TECH_LTE = "LTE"
    const val TECH_UMTS = "UMTS"
    const val TECH_GSM = "GSM"
    const val TECH_CDMA = "CDMA"
    const val TECH_UNKNOWN = "UNKNOWN"

    /** Size of the palette hashed cell and area colors index into. */
    const val HASH_PALETTE_SIZE = 16

    /** The index meaning "no identity, draw as unknown". */
    const val UNKNOWN_COLOR_INDEX = -1

    /** Points newer than this draw above older ones so the live walk stays on top. */
    private const val RECENT_WINDOW_MS = 12L * 60 * 60 * 1000

    /** Technology name for a point; a 5G NSA point (LTE anchor plus NR leg) counts as NR. */
    fun techOf(protocol: Int, nrScg: Int): String = when {
        nrScg == 1 || protocol == SurveyedPointEntity.PROTOCOL_NR -> TECH_NR
        protocol == SurveyedPointEntity.PROTOCOL_LTE -> TECH_LTE
        protocol == SurveyedPointEntity.PROTOCOL_UMTS -> TECH_UMTS
        protocol == SurveyedPointEntity.PROTOCOL_GSM -> TECH_GSM
        protocol == SurveyedPointEntity.PROTOCOL_CDMA -> TECH_CDMA
        else -> TECH_UNKNOWN
    }

    /** Technology name for a coarse technology category (the protocol rank as text). */
    fun techOfCategory(category: String?): String = techOf(category?.toIntOrNull() ?: 0, 0)

    /** Stable palette index for an identity string, or [UNKNOWN_COLOR_INDEX] for none. */
    fun colorIndex(key: String?): Int =
        key?.let { (it.hashCode() and 0x7FFFFFFF) % HASH_PALETTE_SIZE } ?: UNKNOWN_COLOR_INDEX

    /** The area identity ("plmn-area") of a point, or null when unknown. */
    fun areaKey(plmn: String?, area: Int): String? = plmn?.let { "$it-$area" }

    fun fromPoint(point: SurveyedPointEntity, now: Long): Feature {
        val uploaded = point.uploadedMask != 0
        return Feature.fromGeometry(Point.fromLngLat(point.longitude, point.latitude)).apply {
            addNumberProperty(PROP_ID, point.id)
            addNumberProperty(PROP_KIND, point.observedMask)
            addBooleanProperty(PROP_UPLOADED, uploaded)
            addNumberProperty(
                PROP_SORT,
                sortKey(recent = now - point.time < RECENT_WINDOW_MS, uploaded = uploaded)
            )
            addStringProperty(PROP_TECH, techOf(point.protocol, point.nrScg))
            point.plmn?.let { addStringProperty(PROP_PLMN, it) }
            addNumberProperty(PROP_BUCKET, point.signalBucket)
            addNumberProperty(PROP_CELL_COLOR, colorIndex(point.cellId))
            addNumberProperty(PROP_AREA_COLOR, colorIndex(areaKey(point.plmn, point.area)))
            addNumberProperty(PROP_SOURCE, point.source)
            addBooleanProperty(PROP_COARSE, false)
        }
    }

    /** A lattice cell from the plain coarse query: signal and sent status only. */
    fun fromCell(cell: SurveyedPointCell, step: Double, now: Long): Feature =
        coarseFeature(
            cell.latKey,
            cell.lonKey,
            cell.latitude,
            cell.longitude,
            cell.count,
            cell.minUploadedMask,
            cell.bestBucket,
            cell.lastTime,
            step,
            now
        )

    /** A lattice cell reduced to its dominant category, mapped into that mode's property. */
    fun fromCategoryCell(
        cell: SurveyedPointCategoryCell,
        category: DominantCategory,
        step: Double,
        now: Long
    ): Feature =
        coarseFeature(
            cell.latKey,
            cell.lonKey,
            cell.latitude,
            cell.longitude,
            cell.cellCount,
            cell.minUploadedMask,
            cell.bestBucket,
            cell.lastTime,
            step,
            now
        )
            .apply {
                when (category) {
                    DominantCategory.TECHNOLOGY -> addStringProperty(
                        PROP_TECH,
                        techOfCategory(cell.category)
                    )

                    DominantCategory.PROVIDER -> cell.category?.let {
                        addStringProperty(
                            PROP_PLMN,
                            it
                        )
                    }

                    DominantCategory.CELL -> addNumberProperty(
                        PROP_CELL_COLOR,
                        colorIndex(cell.category)
                    )

                    DominantCategory.AREA -> addNumberProperty(
                        PROP_AREA_COLOR,
                        colorIndex(cell.category)
                    )
                }
            }

    private fun coarseFeature(
        latKey: Long, lonKey: Long, latitude: Double, longitude: Double, count: Int,
        minUploadedMask: Int, bestBucket: Int, lastTime: Long, step: Double, now: Long,
    ): Feature {
        val uploaded = minUploadedMask != 0
        return Feature.fromGeometry(Point.fromLngLat(longitude, latitude)).apply {
            addBooleanProperty(PROP_COARSE, true)
            addNumberProperty(PROP_LAT_KEY, latKey)
            addNumberProperty(PROP_LON_KEY, lonKey)
            addNumberProperty(PROP_STEP, step)
            addNumberProperty(PROP_COUNT, count)
            addBooleanProperty(PROP_UPLOADED, uploaded)
            addNumberProperty(
                PROP_SORT,
                sortKey(recent = now - lastTime < RECENT_WINDOW_MS, uploaded = uploaded)
            )
            addNumberProperty(PROP_BUCKET, bestBucket)
            addStringProperty(PROP_TECH, TECH_UNKNOWN)
            addNumberProperty(PROP_CELL_COLOR, UNKNOWN_COLOR_INDEX)
            addNumberProperty(PROP_AREA_COLOR, UNKNOWN_COLOR_INDEX)
        }
    }

    private fun sortKey(recent: Boolean, uploaded: Boolean): Int =
        (if (recent) 2 else 0) + (if (uploaded) 0 else 1)
}
