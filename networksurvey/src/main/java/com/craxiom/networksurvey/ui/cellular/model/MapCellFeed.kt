package com.craxiom.networksurvey.ui.cellular.model

import android.location.Location
import com.craxiom.networksurvey.model.CellularRecordWrapper
import com.craxiom.networksurvey.model.Plmn

/**
 * What the Map tab feeds the map: the serving cells as they are scanned, the SIM to show, the
 * starting filters, and the first location to center on. [TowerMapLibreViewModel] implements it;
 * tests use a fake.
 */
interface MapCellFeed {
    /** A cellular scan for one SIM; the map takes its serving cell. */
    fun onCellularBatchResults(cellularBatchResults: List<CellularRecordWrapper?>?, subscriptionId: Int)

    /** The SIMs changed; forget every SIM's serving cell. */
    fun resetSimCount()

    /** Shows the serving cell of [subscriptionId]. */
    fun setSelectedSimSubscriptionId(subscriptionId: Int)

    /** Shows the towers of [towerSource]. */
    fun setTowerSource(towerSource: TowerSource)

    /** Shows the towers of one technology, a [com.craxiom.networksurvey.model.CellularProtocol] name. */
    fun setSelectedRadioType(radioType: String, isManualSelection: Boolean = false)

    /**
     * Shows the towers of one operator.
     *
     * @param isManualSelection True when the user set it in Filters; the filter then stops
     * following the serving cell until the SIM shown or the SIMs change.
     */
    fun setPlmnFilter(plmn: Plmn, isManualSelection: Boolean = false)

    /**
     * Centers the map on [location] once.
     *
     * @return True once the map has been centered, so the caller can stop listening.
     */
    fun setMapCenterLocation(location: Location): Boolean
}
