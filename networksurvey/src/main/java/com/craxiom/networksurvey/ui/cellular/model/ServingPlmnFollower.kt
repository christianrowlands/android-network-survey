package com.craxiom.networksurvey.ui.cellular.model

import com.craxiom.networksurvey.model.CellularRecordWrapper
import com.craxiom.networksurvey.model.Plmn

/**
 * Decides when the map's operator (PLMN) filter follows the serving cell. It follows every scan of
 * the SIM the map shows until the user sets an operator in Filters; switching SIMs or a SIM change
 * hands the filter back to the serving cell. The technology filter follows the same rule in
 * [TowerMapLibreViewModel].
 */
internal class ServingPlmnFollower {

    /** True once the user set the operator in Filters, until the SIM shown or the SIMs change. */
    var pickedByUser = false
        private set

    /** The user set the operator in Filters; stop following the serving cell. */
    fun onUserPick() {
        pickedByUser = true
    }

    /** The SIM shown or the SIMs changed; follow the serving cell again. */
    fun reset() {
        pickedByUser = false
    }

    /**
     * @param servingCell The serving cell of a scan of [subscriptionId], or null when it had none.
     * @param selectedSim The SIM the map shows, or null before one is chosen (then any SIM counts).
     * @return The operator to filter the towers by, or null to leave the filter alone: the user set
     * it, the scan is of another SIM, or the cell does not report its operator.
     */
    fun plmnFor(servingCell: CellularRecordWrapper?, subscriptionId: Int, selectedSim: Int?): Plmn? {
        if (pickedByUser) return null
        if (selectedSim != null && subscriptionId != selectedSim) return null
        return servingCell?.plmn?.takeIf { it.isSet() }
    }
}
