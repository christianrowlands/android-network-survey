package com.craxiom.networksurvey.ui.cellular.model

import com.craxiom.networksurvey.data.api.Tower
import com.craxiom.networksurvey.util.CellularUtils

/**
 * A [Tower] plus its precomputed map id. Two wrappers are equal when they describe the same
 * cell from the same data source, regardless of the observation statistics (samples, range,
 * timestamps) that change between fetches. That lets a refreshed copy replace the old one in
 * a set rather than sitting next to it as a duplicate.
 */
class TowerWrapper(val tower: Tower) {
    val towerId: String = CellularUtils.getTowerId(tower)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TowerWrapper) return false
        return towerId == other.towerId && tower.source == other.tower.source
    }

    override fun hashCode(): Int = 31 * towerId.hashCode() + tower.source.hashCode()

    override fun toString(): String = "TowerWrapper($towerId, ${tower.source})"
}
