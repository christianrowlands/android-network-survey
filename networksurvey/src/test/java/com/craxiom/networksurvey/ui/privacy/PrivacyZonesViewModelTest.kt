package com.craxiom.networksurvey.ui.privacy

import android.content.Context
import androidx.core.content.edit
import com.craxiom.networksurvey.privacy.PrivacyZone
import com.craxiom.networksurvey.privacy.PrivacyZoneState
import com.craxiom.networksurvey.privacy.PrivacyZoneStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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

/** The Privacy zones page: the saved zones, the zone the survey is paused in, and the organization's policy. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PrivacyZonesViewModelTest {

    private lateinit var store: PrivacyZoneStore
    private val gate = MutableStateFlow(PrivacyZoneState.UNDECIDED)
    private var allowed = true

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val preferences = RuntimeEnvironment.getApplication().getSharedPreferences("privacy_zones_vm_test", Context.MODE_PRIVATE)
        preferences.edit(commit = true) { clear() }
        store = PrivacyZoneStore(preferences)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = PrivacyZonesViewModel(RuntimeEnvironment.getApplication(), store, gate) { allowed }

    @Test
    fun theSavedZones_showInOrder_andFollowChanges() {
        store.save(zone("a"))
        val viewModel = viewModel()
        store.save(zone("b"))

        assertEquals(listOf("a", "b"), viewModel.state.value.zones.map { it.id })
        assertTrue(viewModel.state.value.canAdd)
    }

    @Test
    fun theZoneTheSurveyIsPausedIn_isMarked() {
        val home = store.save(zone("a", "Home"))!!
        val viewModel = viewModel()

        gate.value = PrivacyZoneState(decided = true, zone = home)

        assertEquals("a", viewModel.state.value.insideZoneId)
        assertEquals("Home", viewModel.state.value.insideZone?.name)
    }

    @Test
    fun turnedOffByTheOrganization_nothingCanBeAdded_andNoZoneIsInside() {
        val home = store.save(zone("a"))!!
        gate.value = PrivacyZoneState(decided = true, zone = home)
        allowed = false

        val state = viewModel().state.value

        assertFalse(state.allowed)
        assertFalse(state.canAdd)
        assertNull(state.insideZoneId)
    }

    @Test
    fun refresh_readsThePolicyAgain() {
        val viewModel = viewModel()
        allowed = false

        viewModel.refresh()

        assertFalse(viewModel.state.value.allowed)
    }

    @Test
    fun aFullList_cannotAddMore() {
        repeat(PrivacyZone.MAX_ZONES) { store.save(zone("z$it")) }

        val state = viewModel().state.value

        assertTrue(state.isFull)
        assertFalse(state.canAdd)
    }

    @Test
    fun remove_deletesTheZone() {
        store.save(zone("a"))
        store.save(zone("b"))
        val viewModel = viewModel()

        viewModel.remove("a")

        assertEquals(listOf("b"), viewModel.state.value.zones.map { it.id })
    }

    private fun zone(id: String, name: String = "") = PrivacyZone(id, name, 38.9, -77.0)
}
