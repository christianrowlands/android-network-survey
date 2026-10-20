package com.craxiom.networksurvey.ui.cellular.details.model

import com.craxiom.networksurvey.services.controller.CellularController
import com.craxiom.networksurvey.ui.signals.ViewedSim
import org.junit.Assert.assertEquals
import org.junit.Test

/** The SIM tabs and the subscription id their scans arrive with. */
class CellularPagesTest {

    @Test
    fun noSim_isOneTabOnTheDefaultSubscription() {
        assertEquals(
            listOf(CellularPage(CellularController.DEFAULT_SUBSCRIPTION_ID, ViewedSim.NO_SIM)),
            CellularPages.from(emptyList()),
        )
    }

    @Test
    fun oneSim_scansTheDefaultSubscription_butNamesTheRealSim() {
        assertEquals(
            listOf(CellularPage(CellularController.DEFAULT_SUBSCRIPTION_ID, ViewedSim(1, 7, "Mint"))),
            CellularPages.from(listOf(SimInfo(1, 7, "Mint"))),
        )
    }

    @Test
    fun twoSims_areATabEach() {
        assertEquals(
            listOf(CellularPage(7, ViewedSim(1, 7, "Mint")), CellularPage(9, ViewedSim(2, 9, null))),
            CellularPages.from(listOf(SimInfo(1, 7, "Mint"), SimInfo(2, 9, null))),
        )
    }
}
