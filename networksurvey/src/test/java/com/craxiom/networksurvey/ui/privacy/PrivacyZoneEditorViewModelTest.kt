package com.craxiom.networksurvey.ui.privacy

import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.SavedStateHandle
import com.craxiom.networksurvey.privacy.PrivacyZone
import com.craxiom.networksurvey.privacy.PrivacyZoneSource
import com.craxiom.networksurvey.privacy.PrivacyZoneStore
import com.craxiom.networksurvey.ui.common.MapPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
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

/** The zone editor: new and saved zones, the map's center, saving, deleting, and the waiting count. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PrivacyZoneEditorViewModelTest {

    private lateinit var store: PrivacyZoneStore
    private lateinit var dispatcher: TestDispatcher
    private val counted = mutableListOf<Triple<Double, Double, Int>>()
    private val counter = WaitingUploadCounter { lat, lon, radius ->
        counted += Triple(lat, lon, radius)
        radius / 10
    }

    @Before
    fun setUp() {
        dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val preferences = RuntimeEnvironment.getApplication().getSharedPreferences("privacy_editor_test", Context.MODE_PRIVATE)
        preferences.edit(commit = true) { clear() }
        store = PrivacyZoneStore(preferences)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(zoneId: String? = null, lastKnown: MapPoint? = null) = PrivacyZoneEditorViewModel(
        RuntimeEnvironment.getApplication(),
        SavedStateHandle(zoneId?.let { mapOf(PrivacyZoneEditorViewModel.ZONE_ID_ARG to it) } ?: emptyMap()),
        store,
        counter,
        { lastKnown },
        dispatcher,
    )

    @Test
    fun aNewZone_startsAtTheLastKnownLocation_withTheDefaultRadius() {
        val viewModel = viewModel(lastKnown = HERE)

        val form = viewModel.form.value
        assertTrue(form.isNew)
        assertEquals(HERE, form.center)
        assertEquals(PrivacyZone.DEFAULT_RADIUS_METERS, form.radiusMeters)
        assertTrue(form.centeredOnFix)
        assertFalse(viewModel.unavailable)
    }

    @Test
    fun aNewZone_withNoLocation_takesTheMapsPlace_thenMovesToTheFirstFix() {
        val viewModel = viewModel()
        assertNull(viewModel.form.value.center)

        viewModel.seedCenter(THERE)
        assertEquals(THERE, viewModel.form.value.center)

        assertEquals(HERE, viewModel.takeFirstFix(HERE))
        assertEquals(HERE, viewModel.form.value.center)
        // Only the first fix moves the map
        assertNull(viewModel.takeFirstFix(THERE))
    }

    @Test
    fun aNewZone_theUserMoved_isNotMovedByALateFix() {
        val viewModel = viewModel()
        viewModel.onCenterChanged(THERE, byUser = true)

        assertNull(viewModel.takeFirstFix(HERE))
        assertEquals(THERE, viewModel.form.value.center)
    }

    @Test
    fun aSavedZone_opensWithItsValues_andAFixNeverMovesIt() {
        store.save(PrivacyZone("a", "Home", HERE.latitude, HERE.longitude, 400))
        val viewModel = viewModel(zoneId = "a", lastKnown = THERE)

        val form = viewModel.form.value
        assertFalse(form.isNew)
        assertEquals("Home", form.name)
        assertEquals(HERE, form.center)
        assertEquals(400, form.radiusMeters)
        assertNull(viewModel.takeFirstFix(THERE))
    }

    @Test
    fun save_storesTheZoneAtTheMapsCenter_withTheDefaultNameWhenBlank() {
        val viewModel = viewModel(lastKnown = HERE)
        viewModel.onCenterChanged(THERE, byUser = true)
        viewModel.setRadius(600)
        viewModel.setName("   ")

        viewModel.save("Privacy zone 1")

        assertEquals(PrivacyZoneEditorResult.SAVED, viewModel.result.value)
        val saved = store.zones.value.single()
        assertEquals("Privacy zone 1", saved.name)
        assertEquals(THERE.latitude, saved.latitude, 0.0)
        assertEquals(600, saved.radiusMeters)
        assertEquals(PrivacyZoneSource.USER, saved.source)
    }

    @Test
    fun save_updatesASavedZoneInPlace() {
        store.save(PrivacyZone("a", "Home", HERE.latitude, HERE.longitude, 400))
        store.save(PrivacyZone("b", "Office", THERE.latitude, THERE.longitude))
        val viewModel = viewModel(zoneId = "a")
        viewModel.setName("Flat")

        viewModel.save("unused")

        assertEquals(listOf("Flat", "Office"), store.zones.value.map { it.name })
    }

    @Test
    fun save_withNoPlace_fails_andTheFailureCanBeCleared() {
        val viewModel = viewModel()

        viewModel.save("Privacy zone 1")
        assertEquals(PrivacyZoneEditorResult.FAILED, viewModel.result.value)
        assertTrue(store.zones.value.isEmpty())

        viewModel.consumeFailure()
        assertNull(viewModel.result.value)
    }

    @Test
    fun theRadius_isKeptInRange() {
        val viewModel = viewModel()
        viewModel.setRadius(10)
        assertEquals(PrivacyZone.MIN_RADIUS_METERS, viewModel.form.value.radiusMeters)
        viewModel.setRadius(99_999)
        assertEquals(PrivacyZone.MAX_RADIUS_METERS, viewModel.form.value.radiusMeters)
    }

    @Test
    fun delete_removesTheZone() {
        store.save(PrivacyZone("a", "Home", HERE.latitude, HERE.longitude))
        val viewModel = viewModel(zoneId = "a")

        viewModel.delete()

        assertEquals(PrivacyZoneEditorResult.DELETED, viewModel.result.value)
        assertTrue(store.zones.value.isEmpty())
    }

    @Test
    fun aDeletedZone_orAFullList_isUnavailable() {
        assertTrue(viewModel(zoneId = "gone").unavailable)

        repeat(PrivacyZone.MAX_ZONES) { store.save(PrivacyZone("z$it", "", HERE.latitude, HERE.longitude)) }
        assertTrue(viewModel().unavailable)
        assertFalse(viewModel(zoneId = "z0").unavailable)
    }

    @Test
    fun theOtherZones_leaveOutTheOneBeingEdited() {
        store.save(PrivacyZone("a", "Home", HERE.latitude, HERE.longitude))
        store.save(PrivacyZone("b", "Office", THERE.latitude, THERE.longitude))

        assertEquals(listOf("b"), viewModel(zoneId = "a").otherZones.value.map { it.id })
        assertEquals(listOf("a", "b"), viewModel().otherZones.value.map { it.id })
    }

    @Test
    fun theDefaultName_countsTheNewZone() {
        store.save(PrivacyZone("a", "", HERE.latitude, HERE.longitude))
        assertEquals("Zone 2", viewModel().defaultName(::name))
        assertEquals("Zone 1", viewModel(zoneId = "a").defaultName(::name))
    }

    @Test
    fun theDefaultName_isNeverGivenTwice_afterADelete() {
        store.save(PrivacyZone("a", "Zone 1", HERE.latitude, HERE.longitude))
        store.save(PrivacyZone("b", "Zone 2", THERE.latitude, THERE.longitude))
        store.remove("a")

        // One zone left, but "Zone 2" is taken
        assertEquals("Zone 3", viewModel().defaultName(::name))
    }

    @Test
    fun theUnsavedForm_survivesTheProcessBeingKilled() {
        val handle = SavedStateHandle()
        val first = PrivacyZoneEditorViewModel(RuntimeEnvironment.getApplication(), handle, store, counter, { HERE }, dispatcher)
        first.setName("Cabin")
        first.setRadius(800)
        first.onCenterChanged(THERE, byUser = true)
        first.cameraZoom = 14.5

        // A new view model on the same saved state, with a different last known location
        val restored = PrivacyZoneEditorViewModel(RuntimeEnvironment.getApplication(), handle, store, counter, { HERE }, dispatcher)

        assertEquals(PrivacyZoneEditorForm(isNew = true, name = "Cabin", center = THERE, radiusMeters = 800, centeredOnFix = true), restored.form.value)
        assertEquals(14.5, restored.cameraZoom!!, 0.0)
    }

    @Test
    fun theDeleteConfirmation_usesTheSavedName_notTheTypedOne() {
        store.save(PrivacyZone("a", "Home", HERE.latitude, HERE.longitude))
        val viewModel = viewModel(zoneId = "a")
        viewModel.setName("Typed but not saved")

        assertEquals("Home", viewModel.savedName)
        assertNull(viewModel().savedName)
    }

    private fun name(number: Int) = "Zone $number"

    @Test
    fun theWaitingCount_isCountedOnceTheCircleStopsChanging() = runTest(dispatcher) {
        val viewModel = viewModel(lastKnown = HERE)
        val collector = backgroundScope.launch { viewModel.waitingCount.collect {} }
        runCurrent()

        viewModel.setRadius(300)
        advanceTimeBy(PrivacyZoneEditorViewModel.COUNT_DEBOUNCE_MS / 2)
        viewModel.setRadius(500)
        advanceTimeBy(PrivacyZoneEditorViewModel.COUNT_DEBOUNCE_MS + 1)
        runCurrent()

        assertEquals(listOf(Triple(HERE.latitude, HERE.longitude, 500)), counted)
        assertEquals(50, viewModel.waitingCount.value)
        collector.cancel()
    }

    private companion object {
        val HERE = MapPoint(38.9, -77.0)
        val THERE = MapPoint(39.1, -76.8)
    }
}
