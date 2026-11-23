package com.craxiom.networksurvey.ui.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

/** The features the zone layers draw: a circle and a label per saved zone, the circle being placed last. */
class PrivacyZoneMapLayersTest {

    @Test
    fun `a saved zone draws a circle and a label with its id and name`() {
        val features = PrivacyZoneMapLayers.features(listOf(PrivacyZoneShape("a", "Home", 38.9, -77.0, 250))).features()!!

        assertEquals(2, features.size)
        assertTrue(features[0].geometry() is Polygon)
        assertTrue(features[1].geometry() is Point)
        features.forEach {
            assertEquals("a", it.getStringProperty(PrivacyZoneMapLayers.PROP_ID))
            assertEquals("Home", it.getStringProperty("name"))
            assertFalse(it.getBooleanProperty("editing"))
        }
    }

    @Test
    fun `the circle being placed has no label and draws over the saved zones`() {
        val features = PrivacyZoneMapLayers.features(
            listOf(
                PrivacyZoneShape("draft", "", 38.9, -77.0, 300, editing = true),
                PrivacyZoneShape("a", "Home", 38.91, -77.0, 250),
            )
        ).features()!!

        assertEquals(3, features.size)
        val last = features.last()
        assertTrue(last.getBooleanProperty("editing"))
        assertTrue(last.geometry() is Polygon)
    }

    @Test
    fun `no zones draws nothing`() {
        assertTrue(PrivacyZoneMapLayers.features(emptyList()).features()!!.isEmpty())
    }
}
