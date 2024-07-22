package com.craxiom.networksurvey.ui.cellular.model

import com.craxiom.networksurvey.ui.cellular.Tower
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.infowindow.InfoWindow
import org.osmdroid.views.overlay.infowindow.MarkerInfoWindow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Displays the information window for a TowerMarker that includes details about the tower such as
 * the range and number of samples used in the calculation.
 */
class TowerMarkerInfoWindow(
    layoutResId: Int,
    mapView: MapView,
    private val towerMarker: TowerMarker,
    private val tower: Tower
) :
    MarkerInfoWindow(layoutResId, mapView) {

    override fun onOpen(item: Any?) {
        // Close all other info windows before opening this one
        InfoWindow.closeAllInfoWindowsOn(mapView)

        towerMarker.snippet = getTowerSnippet(tower)
        towerMarker.subDescription = getTowerSubDescription(tower)

        super.onOpen(item)
    }

    private fun getTowerSnippet(tower: Tower): String {
        return if ("BTSearch" == tower.source) {
            """
            <b>Protocol:</b> ${tower.radio}
        """.trimIndent()
        } else {
            """
            <b>Protocol:</b> ${tower.radio}<br>
            <b>Range:</b> ${tower.range} meters<br>
            <b>Samples:</b> ${tower.samples}
        """.trimIndent()
        }
    }

    private fun getTowerSubDescription(tower: Tower): String {
        return if ("BTSearch" == tower.source) {
            """
                    <b>Updated:</b> ${formatDate(tower.updatedAt)}<br>
                    <b>Source:</b> ${tower.source}
                """.trimIndent()
        } else { // Tower source is "OpenCelliD"
            """
                    <b>Changeable:</b> ${if (tower.changeable == 1) "Yes" else "No"}<br>
                    <b>Created:</b> ${formatDateTime(tower.createdAt)}<br>
                    <b>Updated:</b> ${formatDateTime(tower.updatedAt)}<br>
                    <b>Source:</b> ${tower.source}
            """.trimIndent()
        }
    }

    private fun formatDateTime(timestamp: Long): String {
        val date = Date(timestamp * 1000) // Convert to milliseconds
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(date)
    }

    private fun formatDate(timestamp: Long): String {
        val date = Date(timestamp * 1000) // Convert to milliseconds
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(date)
    }
}