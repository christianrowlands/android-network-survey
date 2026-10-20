package com.craxiom.networksurvey.ui.cellular.model

import com.craxiom.messaging.LteRecord
import com.craxiom.messaging.LteRecordData
import com.craxiom.networksurvey.model.CellularProtocol
import com.craxiom.networksurvey.model.CellularRecordWrapper
import com.craxiom.networksurvey.model.Plmn
import com.craxiom.networksurvey.ui.map.CellRecords
import com.google.protobuf.BoolValue
import com.google.protobuf.Int32Value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The map's operator (PLMN) filter follows the serving cell of the SIM it shows, until the user
 * sets one in Filters.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ServingPlmnFollowerTest {

    private lateinit var viewModel: TowerMapLibreViewModel

    @Before
    fun setUp() {
        // Never advanced, so the API key request and the tower queries the filter starts never run
        Dispatchers.setMain(StandardTestDispatcher())
        viewModel = TowerMapLibreViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun theFirstScan_setsTheServingOperator() {
        assertFalse(viewModel.plmnFilter.value.isSet())

        viewModel.onCellularBatchResults(listOf(CellRecords.lte()), SIM_1)

        assertEquals(LTE_OPERATOR, viewModel.plmnFilter.value)
    }

    @Test
    fun aLaterScan_onAnotherOperator_movesTheFilter() {
        viewModel.setSelectedSimSubscriptionId(SIM_1)
        viewModel.onCellularBatchResults(listOf(CellRecords.lte()), SIM_1)

        viewModel.onCellularBatchResults(listOf(CellRecords.gsm()), SIM_1)

        assertEquals(GSM_OPERATOR, viewModel.plmnFilter.value)
    }

    @Test
    fun anOperatorSetInFilters_staysPut() {
        viewModel.setSelectedSimSubscriptionId(SIM_1)
        viewModel.onCellularBatchResults(listOf(CellRecords.lte()), SIM_1)

        viewModel.setPlmnFilter(OTHER_OPERATOR, isManualSelection = true)
        viewModel.onCellularBatchResults(listOf(CellRecords.gsm()), SIM_1)

        assertEquals(OTHER_OPERATOR, viewModel.plmnFilter.value)
    }

    @Test
    fun allOperators_setInFilters_staysPut() {
        viewModel.onCellularBatchResults(listOf(CellRecords.lte()), SIM_1)

        viewModel.setPlmnFilter(Plmn(0, 0), isManualSelection = true)
        viewModel.onCellularBatchResults(listOf(CellRecords.gsm()), SIM_1)

        assertFalse(viewModel.plmnFilter.value.isSet())
    }

    @Test
    fun aScanOfTheOtherSim_leavesTheFilter() {
        viewModel.setSelectedSimSubscriptionId(SIM_1)
        viewModel.onCellularBatchResults(listOf(CellRecords.lte()), SIM_1)

        viewModel.onCellularBatchResults(listOf(CellRecords.gsm()), SIM_2)

        assertEquals(LTE_OPERATOR, viewModel.plmnFilter.value)
    }

    @Test
    fun switchingSims_followsTheNewSimsOperator_evenAfterAPick() {
        viewModel.setSelectedSimSubscriptionId(SIM_1)
        viewModel.onCellularBatchResults(listOf(CellRecords.lte()), SIM_1)
        viewModel.onCellularBatchResults(listOf(CellRecords.gsm()), SIM_2)
        viewModel.setPlmnFilter(OTHER_OPERATOR, isManualSelection = true)

        viewModel.setSelectedSimSubscriptionId(SIM_2)
        assertEquals(GSM_OPERATOR, viewModel.plmnFilter.value)

        // And keeps following it
        viewModel.onCellularBatchResults(listOf(CellRecords.lte()), SIM_2)
        assertEquals(LTE_OPERATOR, viewModel.plmnFilter.value)
    }

    @Test
    fun aSimChange_followsTheServingCellAgain() {
        viewModel.setSelectedSimSubscriptionId(SIM_1)
        viewModel.setPlmnFilter(OTHER_OPERATOR, isManualSelection = true)

        viewModel.resetSimCount()
        viewModel.onCellularBatchResults(listOf(CellRecords.lte()), SIM_1)

        assertEquals(LTE_OPERATOR, viewModel.plmnFilter.value)
    }

    @Test
    fun aCellWithoutAnOperator_leavesTheFilter() {
        viewModel.onCellularBatchResults(listOf(CellRecords.lte()), SIM_1)

        viewModel.onCellularBatchResults(listOf(lteWithoutOperator()), SIM_1)

        assertEquals(LTE_OPERATOR, viewModel.plmnFilter.value)
    }

    /** A serving LTE cell that reports no MCC or MNC, as some modems do. */
    private fun lteWithoutOperator(): CellularRecordWrapper {
        val data = LteRecordData.newBuilder()
            .setTac(Int32Value.of(1)).setEci(Int32Value.of(2))
            .setServingCell(BoolValue.of(true))
        return CellularRecordWrapper(CellularProtocol.LTE, LteRecord.newBuilder().setData(data).build())
    }

    private companion object {
        const val SIM_1 = 1
        const val SIM_2 = 2

        /** The operators of [CellRecords.lte] and [CellRecords.gsm]. */
        val LTE_OPERATOR = Plmn(310, 260)
        val GSM_OPERATOR = Plmn(234, 10)
        val OTHER_OPERATOR = Plmn(311, 480)
    }
}
