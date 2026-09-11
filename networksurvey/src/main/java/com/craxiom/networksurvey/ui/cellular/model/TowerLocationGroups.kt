package com.craxiom.networksurvey.ui.cellular.model

/** Counts shown as badges on a tower icon that shares its exact coordinates with others. */
data class LocationBadge(val towerCount: Int, val operatorCount: Int)

/**
 * Groups towers by exact coordinates and picks one tower per crowded location to carry the
 * count badges. Only the first tower of a group is tagged, otherwise every stacked icon would
 * draw its own semi transparent badge on top of the others.
 */
object TowerLocationGroups {

    /**
     * @return badge counts keyed by [TowerWrapper.towerId], only for locations shared by two
     * or more towers
     */
    fun compute(towers: List<TowerWrapper>): Map<String, LocationBadge> {
        val badges = HashMap<String, LocationBadge>()
        towers.groupBy { Pair(it.tower.lat, it.tower.lon) }.values.forEach { group ->
            if (group.size < 2) return@forEach
            val operators = group.mapTo(HashSet()) { "${it.tower.mcc}-${it.tower.mnc}" }.size
            badges[group.first().towerId] = LocationBadge(towerCount = group.size, operatorCount = operators)
        }
        return badges
    }
}
