package com.craxiom.networksurvey.ui.cellular.towermap

internal val DefaultMapSymbolManagerSettings = MapSymbolManagerSettings()

/**
 * Data class for UI-related settings on the map.
 *
 * Note: Should not be a data class if in need of maintaining binary compatibility
 * on future changes. See: https://jakewharton.com/public-api-challenges-in-kotlin/
 */
data class MapSymbolManagerSettings(
    val iconAllowOverlap: Boolean = true,
    val iconIgnorePlacement: Boolean = true,
    val textAllowOverlap: Boolean = true,
    val textIgnorePlacement: Boolean = true
)
