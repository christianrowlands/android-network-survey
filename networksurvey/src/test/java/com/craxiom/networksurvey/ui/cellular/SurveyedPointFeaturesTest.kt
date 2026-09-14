package com.craxiom.networksurvey.ui.cellular

import com.craxiom.networksurvey.logging.db.dao.DominantCategory
import com.craxiom.networksurvey.logging.db.model.SurveyedPointCategoryCell
import com.craxiom.networksurvey.logging.db.model.SurveyedPointEntity
import com.craxiom.networksurvey.ui.cellular.model.SurveyedPointFeatures
import com.craxiom.networksurvey.util.SignalBuckets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the properties emitted on each map feature: stable hashed colors for cells and areas,
 * unknown handling, technology strings, and draw order.
 */
class SurveyedPointFeaturesTest {

    private fun point(
        protocol: Int = SurveyedPointEntity.PROTOCOL_LTE,
        nrScg: Int = 0,
        plmn: String? = "310-260",
        area: Int = 12345,
        cellId: String? = "310-260-12345-67890123",
        uploaded: Int = 0,
        time: Long = 1_000_000,
        bucket: Int = SignalBuckets.GOOD,
    ) = SurveyedPointEntity().also {
        it.id = 7
        it.latitude = 38.0
        it.longitude = -77.0
        it.time = time
        it.observedMask = SurveyedPointEntity.OBSERVED_CELLULAR
        it.uploadedMask = uploaded
        it.protocol = protocol
        it.nrScg = nrScg
        it.plmn = plmn
        it.area = area
        it.cellId = cellId
        it.signalBucket = bucket
    }

    @Test
    fun technologyStringCountsNsaAsNr() {
        assertEquals("LTE", SurveyedPointFeatures.techOf(SurveyedPointEntity.PROTOCOL_LTE, 0))
        assertEquals("NR", SurveyedPointFeatures.techOf(SurveyedPointEntity.PROTOCOL_LTE, 1))
        assertEquals("NR", SurveyedPointFeatures.techOf(SurveyedPointEntity.PROTOCOL_NR, 0))
        assertEquals("GSM", SurveyedPointFeatures.techOf(SurveyedPointEntity.PROTOCOL_GSM, 0))
        assertEquals("UNKNOWN", SurveyedPointFeatures.techOf(SurveyedPointEntity.PROTOCOL_NONE, 0))
    }

    @Test
    fun hashedColorsAreStableAndUnknownForNull() {
        val a = SurveyedPointFeatures.colorIndex("310-260-12345-67890123")
        assertEquals(a, SurveyedPointFeatures.colorIndex("310-260-12345-67890123"))
        assertTrue(a in 0 until SurveyedPointFeatures.HASH_PALETTE_SIZE)
        assertNotEquals(a, SurveyedPointFeatures.colorIndex("310-260-12345-67890124"))
        assertEquals(
            SurveyedPointFeatures.UNKNOWN_COLOR_INDEX,
            SurveyedPointFeatures.colorIndex(null)
        )
    }

    @Test
    fun pointFeatureCarriesEveryModeProperty() {
        val feature = SurveyedPointFeatures.fromPoint(point(nrScg = 1), now = 2_000_000)

        assertEquals(7L, feature.getNumberProperty(SurveyedPointFeatures.PROP_ID).toLong())
        assertEquals("NR", feature.getStringProperty(SurveyedPointFeatures.PROP_TECH))
        assertEquals("310-260", feature.getStringProperty(SurveyedPointFeatures.PROP_PLMN))
        assertEquals(
            SignalBuckets.GOOD,
            feature.getNumberProperty(SurveyedPointFeatures.PROP_BUCKET).toInt()
        )
        assertEquals(
            SurveyedPointFeatures.colorIndex("310-260-12345-67890123"),
            feature.getNumberProperty(SurveyedPointFeatures.PROP_CELL_COLOR).toInt()
        )
        assertEquals(
            SurveyedPointFeatures.colorIndex("310-260-12345"),
            feature.getNumberProperty(SurveyedPointFeatures.PROP_AREA_COLOR).toInt()
        )
        assertEquals(false, feature.getBooleanProperty(SurveyedPointFeatures.PROP_UPLOADED))
        assertEquals(false, feature.getBooleanProperty(SurveyedPointFeatures.PROP_COARSE))
    }

    @Test
    fun unknownIdentityHashesToUnknownNotToAColor() {
        val feature = SurveyedPointFeatures.fromPoint(
            point(plmn = null, cellId = null, protocol = 0),
            now = 0
        )

        assertEquals(
            SurveyedPointFeatures.UNKNOWN_COLOR_INDEX,
            feature.getNumberProperty(SurveyedPointFeatures.PROP_CELL_COLOR).toInt()
        )
        assertEquals(
            SurveyedPointFeatures.UNKNOWN_COLOR_INDEX,
            feature.getNumberProperty(SurveyedPointFeatures.PROP_AREA_COLOR).toInt()
        )
        assertEquals("UNKNOWN", feature.getStringProperty(SurveyedPointFeatures.PROP_TECH))
    }

    @Test
    fun recentPendingPointsDrawAboveOldUploadedOnes() {
        val now = 100_000_000_000L
        val recentPending =
            SurveyedPointFeatures.fromPoint(point(time = now - 1000, uploaded = 0), now)
        val recentSent =
            SurveyedPointFeatures.fromPoint(point(time = now - 1000, uploaded = 3), now)
        val oldPending = SurveyedPointFeatures.fromPoint(point(time = 1, uploaded = 0), now)
        val oldSent = SurveyedPointFeatures.fromPoint(point(time = 1, uploaded = 3), now)

        fun sort(f: org.maplibre.geojson.Feature) =
            f.getNumberProperty(SurveyedPointFeatures.PROP_SORT).toInt()
        assertTrue(sort(recentPending) > sort(recentSent))
        assertTrue(sort(recentSent) > sort(oldPending))
        assertTrue(sort(oldPending) > sort(oldSent))
    }

    @Test
    fun categoryCellFeatureMapsItsCategoryIntoTheRightProperty() {
        fun cell(category: String?) = SurveyedPointCategoryCell(
            latKey = 5,
            lonKey = 6,
            latitude = 38.0,
            longitude = -77.0,
            count = 4,
            category = category,
            minUploadedMask = 0,
            bestBucket = SignalBuckets.FAIR,
            lastTime = 1,
            cellCount = 9
        )

        val tech = SurveyedPointFeatures.fromCategoryCell(
            cell("5"),
            DominantCategory.TECHNOLOGY,
            step = 0.01,
            now = 0
        )
        assertEquals("NR", tech.getStringProperty(SurveyedPointFeatures.PROP_TECH))
        assertEquals(true, tech.getBooleanProperty(SurveyedPointFeatures.PROP_COARSE))
        assertEquals(9, tech.getNumberProperty(SurveyedPointFeatures.PROP_COUNT).toInt())
        assertEquals(0.01, tech.getNumberProperty(SurveyedPointFeatures.PROP_STEP).toDouble(), 0.0)

        val provider = SurveyedPointFeatures.fromCategoryCell(
            cell("310-260"),
            DominantCategory.PROVIDER,
            0.01,
            0
        )
        assertEquals("310-260", provider.getStringProperty(SurveyedPointFeatures.PROP_PLMN))

        val area = SurveyedPointFeatures.fromCategoryCell(
            cell("310-260-12345"),
            DominantCategory.AREA,
            0.01,
            0
        )
        assertEquals(
            SurveyedPointFeatures.colorIndex("310-260-12345"),
            area.getNumberProperty(SurveyedPointFeatures.PROP_AREA_COLOR).toInt()
        )

        val unknown =
            SurveyedPointFeatures.fromCategoryCell(cell(null), DominantCategory.CELL, 0.01, 0)
        assertEquals(
            SurveyedPointFeatures.UNKNOWN_COLOR_INDEX,
            unknown.getNumberProperty(SurveyedPointFeatures.PROP_CELL_COLOR).toInt()
        )
    }
}
