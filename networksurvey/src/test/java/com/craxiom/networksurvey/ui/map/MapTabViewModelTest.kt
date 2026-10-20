package com.craxiom.networksurvey.ui.map

import android.location.Location
import com.craxiom.networksurvey.SimChanges
import com.craxiom.networksurvey.model.CellularRecordWrapper
import com.craxiom.networksurvey.model.Plmn
import com.craxiom.networksurvey.model.TowerKey
import com.craxiom.networksurvey.services.NetworkSurveyService
import com.craxiom.networksurvey.ui.activesurvey.NewTowerRegistry
import com.craxiom.networksurvey.ui.cellular.model.MapCellFeed
import com.craxiom.networksurvey.ui.cellular.model.ServingCellInfo
import com.craxiom.networksurvey.ui.cellular.model.TowerSource
import com.craxiom.networksurvey.ui.signals.ViewedSim
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** The Map tab's long-lived state: serving cells, the SIM shown, the camera, and what it feeds the map. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MapTabViewModelTest {

    private lateinit var viewModel: MapTabViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        NewTowerRegistry.endSession()
        viewModel = MapTabViewModel(RuntimeEnvironment.getApplication())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun aBatch_reachesTheMapAndTheLatestServingCell() {
        val feed = FakeFeed()
        viewModel.attach(feed, seed = null)

        viewModel.onCellularBatch(listOf(CellRecords.lte()), 1)

        assertEquals(1, feed.batches.size)
        assertEquals(1, viewModel.latestServingCell.value?.subscriptionId)
        assertEquals(CellRecords.lte(), viewModel.servingCells.value[1]?.servingCell)
        assertEquals(1, viewModel.selectedSim.value)
    }

    @Test
    fun aBatchWithNoServingCell_dropsThatSimsCell() {
        viewModel.onCellularBatch(listOf(CellRecords.lte()), 1)
        viewModel.onCellularBatch(listOf(CellRecords.lte(serving = false)), 1)

        assertTrue(viewModel.servingCells.value.isEmpty())
    }

    @Test
    fun anEmptyScan_keepsTheSimsCell() {
        val feed = FakeFeed()
        viewModel.attach(feed, seed = null)
        viewModel.onCellularBatch(listOf(CellRecords.lte()), 1)

        viewModel.onCellularBatch(emptyList(), 1)

        assertEquals(1, viewModel.selectedSim.value)
        assertEquals(1, viewModel.servingCells.value.size)
        assertEquals(1, feed.batches.size)
    }

    @Test
    fun aSimChangeWhileTheMapIsHidden_resetsTheMapOnItsReturn() {
        val feed = FakeFeed()
        viewModel.attach(feed, seed = null)
        viewModel.detach(feed)

        SimChanges.notifyChanged()
        assertEquals(0, feed.resets)

        viewModel.attach(feed, seed = null)
        assertEquals(1, feed.resets)
        viewModel.detach(feed)
        viewModel.attach(feed, seed = null)
        assertEquals(1, feed.resets)
    }

    @Test
    fun theSimPicked_isKeptAcrossARebind() {
        val service = mock<NetworkSurveyService>()
        viewModel.onServiceChanged(service)
        viewModel.onCellularBatch(listOf(CellRecords.lte()), 1)
        viewModel.onCellularBatch(listOf(CellRecords.nr()), 2)
        viewModel.selectSim(2)

        viewModel.onServiceChanged(null)
        viewModel.onServiceChanged(service)

        assertEquals(2, viewModel.selectedSim.value)
        verify(service).unregisterCellularSurveyRecordListener(viewModel)
    }

    @Test
    fun theCellularMapAction_switchesTheSimEvenBeforeItHasACell() {
        viewModel.onCellularBatch(listOf(CellRecords.lte()), 1)
        viewModel.selectSim(1)

        viewModel.requestSim(ViewedSim(slot = 2, subscriptionId = 2, carrier = null))
        // No cell for SIM 2 yet, so the card stays on SIM 1 until one arrives
        assertEquals(1, viewModel.selectedSim.value)

        viewModel.onCellularBatch(listOf(CellRecords.nr()), 2)
        assertEquals(2, viewModel.selectedSim.value)
    }

    @Test
    fun theCellularSim_onlySeedsTheFirstVisit() {
        viewModel.onCellularBatch(listOf(CellRecords.lte()), 1)
        viewModel.onCellularBatch(listOf(CellRecords.nr()), 2)

        viewModel.seedSim(ViewedSim(slot = 2, subscriptionId = 2, carrier = null))
        viewModel.seedSim(ViewedSim(slot = 1, subscriptionId = 1, carrier = null))
        viewModel.seedSim(ViewedSim.NO_SIM)

        assertEquals(2, viewModel.selectedSim.value)
    }

    @Test
    fun aNewMap_isSeededButAReturningOneKeepsItsFilters() {
        val seed = ServingCellInfo(CellRecords.lte(), 1)
        val feed = FakeFeed()

        viewModel.attach(feed, seed)
        assertEquals(listOf("LTE"), feed.radioTypes)
        assertEquals(1, feed.plmns.size)
        assertEquals(1, feed.towerSources.size)

        viewModel.detach(feed)
        viewModel.attach(feed, seed)
        assertEquals(listOf("LTE"), feed.radioTypes)
        // The serving cell is replayed on every visit, so the overlay shows at once
        assertEquals(2, feed.batches.size)

        val recreated = FakeFeed()
        viewModel.attach(recreated, seed)
        assertEquals(listOf("LTE"), recreated.radioTypes)
    }

    @Test
    fun detachingAnOlderMap_leavesTheNewOneAttached() {
        val old = FakeFeed()
        val current = FakeFeed()
        viewModel.attach(old, null)
        viewModel.attach(current, null)

        viewModel.detach(old)
        viewModel.onCellularBatch(listOf(CellRecords.lte()), 1)

        assertEquals(1, current.batches.size)
        assertTrue(old.batches.isEmpty())
    }

    @Test
    fun theCamera_isKeptBetweenVisits() {
        assertNull(viewModel.initialCamera())

        val camera = MapCamera(latitude = 39.1, longitude = -94.6, zoom = 15.5, bearing = 30.0)
        viewModel.saveCamera(camera)

        assertSame(camera, viewModel.initialCamera())
    }

    @Test
    fun theLocationListener_isOnlyRegisteredWhileBoundAndNotYetCentered() {
        val service = mock<NetworkSurveyService>()
        viewModel.onServiceChanged(service)
        verify(service).registerLocationListener(any())

        viewModel.onServiceChanged(null)
        verify(service).unregisterLocationListener(any())

        val returning = mock<NetworkSurveyService>()
        viewModel.saveCamera(MapCamera(1.0, 2.0, 14.0))
        viewModel.onServiceChanged(returning)
        verify(returning, never()).registerLocationListener(any())
    }

    @Test
    fun newTowers_followTheRegistry() {
        val tower = TowerKey.from(CellRecords.lte())!!
        NewTowerRegistry.startSession()
        NewTowerRegistry.add(tower)

        assertEquals(setOf(tower), viewModel.newTowers.value)
        NewTowerRegistry.endSession()
    }

    /** Records what the view model feeds the map. */
    private class FakeFeed : MapCellFeed {
        val batches = mutableListOf<Pair<List<CellularRecordWrapper?>?, Int>>()
        val radioTypes = mutableListOf<String>()
        val plmns = mutableListOf<Plmn>()
        val towerSources = mutableListOf<TowerSource>()
        val selectedSims = mutableListOf<Int>()
        var resets = 0

        override fun onCellularBatchResults(cellularBatchResults: List<CellularRecordWrapper?>?, subscriptionId: Int) {
            batches += cellularBatchResults to subscriptionId
        }

        override fun resetSimCount() {
            resets++
        }

        override fun setSelectedSimSubscriptionId(subscriptionId: Int) {
            selectedSims += subscriptionId
        }

        override fun setTowerSource(towerSource: TowerSource) {
            towerSources += towerSource
        }

        override fun setSelectedRadioType(radioType: String, isManualSelection: Boolean) {
            radioTypes += radioType
        }

        override fun setPlmnFilter(plmn: Plmn, isManualSelection: Boolean) {
            plmns += plmn
        }

        override fun setMapCenterLocation(location: Location): Boolean = false
    }
}
