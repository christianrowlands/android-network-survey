package com.craxiom.networksurvey.ui.cellular.model

import android.graphics.PorterDuff
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.cellular.Tower
import com.craxiom.networksurvey.util.CellularUtils
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class TowerMarker(private val mapView: MapView, tower: Tower) : Marker(mapView) {
    var cgiId: String

    init {
        val towerDrawable =
            AppCompatResources.getDrawable(mapView.context, R.drawable.ic_cellular)
        towerDrawable!!.colorFilter =
            BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                R.color.colorPrimary, BlendModeCompat.SRC_ATOP
            )
        setPosition(GeoPoint(tower.lat, tower.lon))
        setAnchor(ANCHOR_CENTER, ANCHOR_BOTTOM)
        icon = towerDrawable
        title = getTitleString(tower)
        cgiId = CellularUtils.getTowerId(tower)
        setPanToView(false)

        // TODO towerMarker.infoWindow = InfoWindow(view, mapView)

        // TODO Set an onClick listener to display the tower details
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

    fun setServingCell(isServingCell: Boolean) {
        val towerDrawable =
            AppCompatResources.getDrawable(mapView.context,R.drawable.ic_cell_tower)
        val color = ContextCompat.getColor(
            mapView.context,
            if (isServingCell) R.color.colorPrimary else R.color.colorAccent
        )

        val wrappedDrawable = DrawableCompat.wrap(towerDrawable!!)
        DrawableCompat.setTint(wrappedDrawable, color)
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
