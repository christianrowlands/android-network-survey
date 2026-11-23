package com.craxiom.networksurvey.privacy

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** The saved zones: round trips, limits, change notifications, and reading damaged or newer data. */
@RunWith(RobolectricTestRunner::class)
class PrivacyZoneStoreTest {

    private lateinit var preferences: SharedPreferences
    private lateinit var store: PrivacyZoneStore

    @Before
    fun setUp() {
        preferences = RuntimeEnvironment.getApplication().getSharedPreferences("privacy_zones_test", Context.MODE_PRIVATE)
        preferences.edit(commit = true) { clear() }
        store = PrivacyZoneStore(preferences)
    }

    @Test
    fun `a new store has no zones`() {
        assertTrue(store.zones.value.isEmpty())
        assertTrue(store.canAdd())
    }

    @Test
    fun `a saved zone survives a new store and keeps its id and source`() {
        val saved = store.save(zone("a", name = "  Home  "))!!
        assertEquals("Home", saved.name)

        val read = PrivacyZoneStore(preferences).zones.value
        assertEquals(listOf(saved), read)
        assertEquals(PrivacyZoneSource.USER, read.single().source)
    }

    @Test
    fun `saving a zone with the same id updates it in place`() {
        store.save(zone("a"))
        store.save(zone("b"))
        store.save(zone("a", name = "Office", radius = 400))
        assertEquals(listOf("a", "b"), store.zones.value.map { it.id })
        assertEquals("Office", store.zone("a")?.name)
        assertEquals(400, store.zone("a")?.radiusMeters)
    }

    @Test
    fun `the radius is limited on save`() {
        assertEquals(PrivacyZone.MIN_RADIUS_METERS, store.save(zone("a", radius = 10))?.radiusMeters)
        assertEquals(PrivacyZone.MAX_RADIUS_METERS, store.save(zone("b", radius = 50_000))?.radiusMeters)
    }

    @Test
    fun `an invalid center or blank id is refused`() {
        assertNull(store.save(zone("a").copy(latitude = 95.0)))
        assertNull(store.save(zone("b").copy(longitude = Double.NaN)))
        assertNull(store.save(zone(" ")))
        assertTrue(store.zones.value.isEmpty())
    }

    @Test
    fun `no more than 20 zones but a full list can still be edited`() {
        repeat(PrivacyZone.MAX_ZONES) { store.save(zone("z$it")) }
        assertFalse(store.canAdd())
        assertNull(store.save(zone("one-too-many")))
        assertEquals(PrivacyZone.MAX_ZONES, store.zones.value.size)
        assertEquals("Renamed", store.save(zone("z3", name = "Renamed"))?.name)
    }

    @Test
    fun `removing a zone emits the new list`() {
        store.save(zone("a"))
        store.save(zone("b"))
        val before = store.zones.value
        assertTrue(store.remove("a"))
        assertFalse(store.remove("missing"))
        assertEquals(listOf("b"), store.zones.value.map { it.id })
        assertTrue(before !== store.zones.value)
        assertEquals(listOf("b"), PrivacyZoneStore(preferences).zones.value.map { it.id })
    }

    @Test
    fun `damaged json reads as no zones`() {
        preferences.edit(commit = true) { putString("zones_json", "{not json") }
        assertTrue(PrivacyZoneStore(preferences).zones.value.isEmpty())
    }

    @Test
    fun `entries from another version are validated, merged by id, and an unknown source reads as user`() {
        val json = """[
            {"id":"org-1","name":"Depot","lat":40.0,"lon":-105.0,"radiusM":300,"source":"ORGANIZATION"},
            {"id":"u-1","name":"Home","lat":41.0,"lon":-105.0,"source":"SOMETHING_NEW","extra":true},
            {"id":"bad","name":"Off globe","lat":120.0,"lon":0.0,"radiusM":300},
            {"name":"No id","lat":40.0,"lon":-105.0},
            {"id":"u-1","name":"Duplicate","lat":42.0,"lon":-105.0},
            null
        ]"""
        preferences.edit(commit = true) { putString("zones_json", json) }

        val read = PrivacyZoneStore(preferences).zones.value
        assertEquals(listOf("org-1", "u-1"), read.map { it.id })
        assertEquals(PrivacyZoneSource.ORGANIZATION, read[0].source)
        assertEquals(PrivacyZoneSource.USER, read[1].source)
        assertEquals("Home", read[1].name)
        assertEquals(PrivacyZone.DEFAULT_RADIUS_METERS, read[1].radiusMeters)
    }

    @Test
    fun `new ids are unique`() {
        assertFalse(PrivacyZoneStore.newId() == PrivacyZoneStore.newId())
    }

    private fun zone(id: String, name: String = id, radius: Int = PrivacyZone.DEFAULT_RADIUS_METERS) =
        PrivacyZone(id = id, name = name, latitude = 40.0, longitude = -105.0, radiusMeters = radius)
}
