package com.craxiom.networksurvey.ui.map

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.privacy.PrivacyZone
import com.craxiom.networksurvey.privacy.PrivacyZoneStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** The Map tab's privacy zones layer: which zones it draws, its saved switch, and the tap sheet. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MapPrivacyZonesViewModelTest {

    private lateinit var store: PrivacyZoneStore
    private lateinit var preferences: SharedPreferences
    private var allowed = true

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val app = RuntimeEnvironment.getApplication()
        val zonePreferences = app.getSharedPreferences("map_zones_test", Context.MODE_PRIVATE)
        zonePreferences.edit(commit = true) { clear() }
        store = PrivacyZoneStore(zonePreferences)
        preferences = app.getSharedPreferences("map_zones_settings_test", Context.MODE_PRIVATE)
        preferences.edit(commit = true) { clear() }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = MapPrivacyZonesViewModel(RuntimeEnvironment.getApplication(), store, preferences) { allowed }

    @Test
    fun theLayer_drawsEverySavedZone_andIsOnByDefault() {
        store.save(PrivacyZone("a", "Home", 38.9, -77.0))
        val viewModel = viewModel()
        store.save(PrivacyZone("b", "Office", 39.0, -77.1))

        assertEquals(listOf("a", "b"), viewModel.zones.value.map { it.id })
        assertTrue(viewModel.show.value)
    }

    @Test
    fun theSwitch_isSaved() {
        viewModel().setShow(false)

        assertFalse(preferences.getBoolean(NetworkSurveyConstants.PROPERTY_SHOW_PRIVACY_ZONES, true))
        assertFalse(viewModel().show.value)
    }

    @Test
    fun turnedOffByTheOrganization_noZonesAreDrawn_untilItAllowsThemAgain() {
        store.save(PrivacyZone("a", "Home", 38.9, -77.0))
        allowed = false
        val viewModel = viewModel()
        assertTrue(viewModel.zones.value.isEmpty())

        allowed = true
        viewModel.refresh()
        assertEquals(1, viewModel.zones.value.size)
    }

    @Test
    fun aTap_opensTheZonesSheet_whichClosesWhenTheZoneGoes() {
        store.save(PrivacyZone("a", "Home", 38.9, -77.0))
        val viewModel = viewModel()

        viewModel.select("a")
        assertEquals("a", viewModel.selected.value?.id)
        viewModel.dismiss()
        assertNull(viewModel.selected.value)

        viewModel.select("a")
        store.remove("a")
        assertNull(viewModel.selected.value)
    }

    @Test
    fun theSheet_closesWhenTheOrganizationTurnsZonesOff() {
        store.save(PrivacyZone("a", "Home", 38.9, -77.0))
        val viewModel = viewModel()
        viewModel.select("a")

        allowed = false
        viewModel.refresh()

        assertNull(viewModel.selected.value)
    }
}
