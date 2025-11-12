package com.craxiom.networksurvey.ui.cellular.model

import com.craxiom.networksurvey.model.CellularRecordWrapper
import java.io.Serializable

data class ServingCellInfo(
    val servingCell: CellularRecordWrapper?,
    val subscriptionId: Int,
    val timestamp: Long = System.currentTimeMillis()
) : Serializable {
    companion object {
        const val KEY = "servingCellInfo"
    }
}
