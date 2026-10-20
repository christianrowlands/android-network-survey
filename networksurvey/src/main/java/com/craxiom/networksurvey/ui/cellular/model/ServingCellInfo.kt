package com.craxiom.networksurvey.ui.cellular.model

import com.craxiom.networksurvey.model.CellularRecordWrapper
import java.io.Serializable

/** The latest serving cell of one SIM, which the map uses for its initial filters and overlay. */
data class ServingCellInfo(
    val servingCell: CellularRecordWrapper?,
    val subscriptionId: Int,
    val timestamp: Long = System.currentTimeMillis()
) : Serializable
