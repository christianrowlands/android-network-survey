package com.craxiom.networksurvey.ui.cellular.details.model

import com.craxiom.networksurvey.services.controller.CellularController
import com.craxiom.networksurvey.ui.signals.ViewedSim

/**
 * One active SIM as the subscription manager reports it.
 *
 * @param slot The SIM slot, counted from 1.
 */
data class SimInfo(val slot: Int, val subscriptionId: Int, val name: String?)

/**
 * One SIM tab of the Cellular view.
 *
 * @param recordSubscriptionId The subscription id the scans for this tab arrive with.
 * @param sim                  The SIM the Signals strip and the map are told about.
 */
data class CellularPage(val recordSubscriptionId: Int, val sim: ViewedSim)

/** The SIM tabs of the Cellular view. */
object CellularPages {

    /**
     * With no SIM or one SIM the controller scans the default subscription, so its records carry
     * [CellularController.DEFAULT_SUBSCRIPTION_ID] (which also keeps the slot field off the
     * records); the tab still names the real SIM. With two or more there is a tab per SIM. With no
     * SIM there is still one tab: the phone can camp on a network for emergency calls.
     */
    fun from(sims: List<SimInfo>): List<CellularPage> = when (sims.size) {
        0 -> listOf(CellularPage(CellularController.DEFAULT_SUBSCRIPTION_ID, ViewedSim.NO_SIM))
        1 -> listOf(CellularPage(CellularController.DEFAULT_SUBSCRIPTION_ID, sims[0].toViewedSim()))
        else -> sims.map { CellularPage(it.subscriptionId, it.toViewedSim()) }
    }

    private fun SimInfo.toViewedSim() = ViewedSim(slot, subscriptionId, name)
}
