package com.craxiom.networksurvey.ui.cellular.model

import android.graphics.PorterDuff
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.DrawableCompat
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.cellular.Tower
import com.craxiom.networksurvey.ui.theme.ColorServingCell
import com.craxiom.networksurvey.ui.theme.ColorTower
import com.craxiom.networksurvey.util.CellularUtils
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.infowindow.MarkerInfoWindow

class TowerMarker(private val mapView: MapView, val tower: Tower) : Marker(mapView) {
    var cgiId: String

    init {
        val towerDrawable =
            AppCompatResources.getDrawable(mapView.context, R.drawable.ic_cell_tower_map)
        position = GeoPoint(tower.lat, tower.lon)
        setAnchor(ANCHOR_CENTER, ANCHOR_BOTTOM)
        icon = towerDrawable
        title = getTitleString(tower)
        cgiId = CellularUtils.getTowerId(tower)
        setPanToView(false)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TowerMarker

        if (title != other.title) return false
        if (position != other.position) return false

        return true
    }

    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + position.hashCode()
        return result
    }

    override fun setInfoWindow(infoWindow: MarkerInfoWindow?) {
        // Do nothing
    }

    override fun showInfoWindow() {
        if (mInfoWindow == null) {
            mInfoWindow = TowerMarkerInfoWindow(R.layout.bonuspack_bubble, mapView, this, tower)
        }

        super.showInfoWindow()
    }

    fun destroy() {
        mapView.overlays.remove(this)
        this.onDestroy()
    }

    fun setServingCell(isServingCell: Boolean) {
        val towerDrawable =
            AppCompatResources.getDrawable(
                mapView.context,
                if (isServingCell) R.drawable.ic_cell_tower_48 else R.drawable.ic_cell_tower_map
            )
        val color = if (isServingCell) ColorServingCell else ColorTower

        val wrappedDrawable = DrawableCompat.wrap(towerDrawable!!)
        DrawableCompat.setTint(wrappedDrawable, color.toArgb())
        DrawableCompat.setTintMode(wrappedDrawable, PorterDuff.Mode.SRC_IN)

        icon = wrappedDrawable
    }

    /**
     * Returns a string representation of the Cell Global Identifier (CGI) for the given tower.
     */
    private fun getTitleString(tower: Tower): String {
        return "${tower.radio}: ${tower.mcc}/${tower.mnc}/${tower.area}/${tower.cid}"
    }
}
