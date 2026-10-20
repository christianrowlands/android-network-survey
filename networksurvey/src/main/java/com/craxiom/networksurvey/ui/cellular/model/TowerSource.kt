package com.craxiom.networksurvey.ui.cellular.model

import androidx.annotation.StringRes
import com.craxiom.networksurvey.R

/**
 * Represents the possible sources of cell towers from the NS tower service.
 */
enum class TowerSource(val apiName: String, @StringRes val displayName: Int) {
    OpenCelliD("OpenCelliD", R.string.tower_source_opencellid),
    BTSearch("BTSearch", R.string.tower_source_btsearch),
    //BeaconDB("BeaconDB"),
    ;
}
