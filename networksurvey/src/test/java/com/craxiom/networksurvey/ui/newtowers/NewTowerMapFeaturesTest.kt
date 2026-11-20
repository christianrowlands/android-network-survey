package com.craxiom.networksurvey.ui.newtowers

import com.craxiom.networksurvey.logging.db.model.NewTowerFindEntity
import com.craxiom.networksurvey.ui.common.MapPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** What the New towers map draws from the saved finds: markers, legend, framing and layer features. */
class NewTowerMapFeaturesTest {

    @Test
    fun findsWithoutAUsableLocation_areLeftOff() {
        val markers = markers(
            find(1, lat = 38.9, lon = -77.0),
            find(2, lat = null, lon = -77.0),
            find(3, lat = 38.9, lon = null),
            find(4, lat = Double.NaN, lon = -77.0),
            find(5, lat = 95.0, lon = -77.0),
        )
        assertEquals(listOf(1L), markers.map { it.id })
    }

    @Test
    fun aMarker_isWhereYouWere_withTheProviderColorAndTechnology() {
        val marker = markers(find(1, lat = 38.9, lon = -77.0, radio = "NR")).single()

        assertEquals(MapPoint(38.9, -77.0), marker.sighting)
        assertEquals("NR", marker.technology)
        assertEquals(COLOR, marker.colorArgb)
        assertNull(marker.tower)
    }

    @Test
    fun theTower_isPlacedOnlyOnceTheDatabaseHasItWithAPosition() {
        val markers = markers(
            find(1).apply { inDatabase = true; towerLatitude = 39.0; towerLongitude = -77.1 },
            find(2).apply { inDatabase = true },
            // A position without the in-database flag is never trusted
            find(3).apply { towerLatitude = 39.0; towerLongitude = -77.1 },
        )
        assertEquals(listOf(MapPoint(39.0, -77.1), null, null), markers.map { it.tower })
    }

    @Test
    fun theLegend_explainsTheTowerGlyph_onlyWhenATowerIsDrawn() {
        val none = markers(find(1), find(2))
        val legend = newTowersMapLegend(none, findCount = 2)
        assertFalse(legend.showsTower)
        assertTrue(legend.showsUnknownTowerNote)
        assertEquals(0, legend.unplacedCount)

        val mixed = markers(find(1).placed(), find(2))
        assertTrue(newTowersMapLegend(mixed, 2).showsTower)
        assertTrue(newTowersMapLegend(mixed, 2).showsUnknownTowerNote)
    }

    @Test
    fun theLegend_dropsTheUnknownTowerNote_whenEveryTowerIsPlaced() {
        val legend = newTowersMapLegend(markers(find(1).placed(), find(2).placed()), findCount = 2)
        assertTrue(legend.showsTower)
        assertFalse(legend.showsUnknownTowerNote)
    }

    @Test
    fun theLegend_countsTheFindsLeftOffTheMap() {
        val markers = markers(find(1), find(2, lat = null))
        assertEquals(1, newTowersMapLegend(markers, findCount = 2).unplacedCount)
    }

    @Test
    fun theOpeningFrame_holdsEverySightingAndEveryDrawnTower() {
        val markers = markers(find(1, lat = 38.9, lon = -77.0).placed(), find(2, lat = 38.8, lon = -77.2))
        assertEquals(
            listOf(MapPoint(38.9, -77.0), MapPoint(39.5, -76.5), MapPoint(38.8, -77.2)),
            newTowerMapFitPoints(markers),
        )
    }

    @Test
    fun sightingFeatures_drawTheNewestLast_andFlagTheSelectedOne() {
        // Finds arrive newest first
        val features = newTowerSightingFeatures(markers(find(3), find(2), find(1)), selectedId = 2).features()!!

        assertEquals(listOf("1", "2", "3"), features.map { it.getStringProperty(NEW_TOWER_PROP_ID) })
        assertEquals(listOf(false, true, false), features.map { it.getBooleanProperty(NEW_TOWER_PROP_SELECTED) })
        assertEquals("LTE", features.first().getStringProperty(NEW_TOWER_PROP_TECH))
        assertEquals("#1E88E5", features.first().getStringProperty(NEW_TOWER_PROP_COLOR))
        assertEquals(Point.fromLngLat(-77.0, 38.9), features.first().geometry())
    }

    @Test
    fun towerAndLineFeatures_existOnlyForPlacedTowers() {
        val markers = markers(find(1, lat = 38.9, lon = -77.0).placed(), find(2))

        val towers = newTowerTowerFeatures(markers).features()!!
        assertEquals(listOf("1"), towers.map { it.getStringProperty(NEW_TOWER_PROP_ID) })
        assertEquals(Point.fromLngLat(-76.5, 39.5), towers.single().geometry())

        val line = newTowerLineFeatures(markers).features()!!.single().geometry() as LineString
        assertEquals(listOf(Point.fromLngLat(-77.0, 38.9), Point.fromLngLat(-76.5, 39.5)), line.coordinates())
    }

    private fun markers(vararg finds: NewTowerFindEntity) = newTowerMapMarkers(finds.toList()) { _, _ -> COLOR }

    private fun NewTowerFindEntity.placed() = apply {
        inDatabase = true
        towerLatitude = 39.5
        towerLongitude = -76.5
    }

    private fun find(id: Long, lat: Double? = 38.9, lon: Double? = -77.0, radio: String = "LTE") =
        NewTowerFindEntity().apply {
            this.id = id
            mcc = "310"
            mnc = "260"
            area = 12345
            cellId = id
            this.radio = radio
            latitude = lat
            longitude = lon
        }

    private companion object {
        const val COLOR = 0xFF1E88E5.toInt()
    }
}
