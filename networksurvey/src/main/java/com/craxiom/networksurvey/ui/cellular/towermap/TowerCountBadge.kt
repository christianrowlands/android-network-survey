package com.craxiom.networksurvey.ui.cellular.towermap

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.currentComposer
import com.craxiom.networksurvey.ui.cellular.model.TowerWrapper
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconOffset
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import kotlin.math.min
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt

private const val COUNT_BADGE_LAYER_KEY = "tower-count-badge-layer"
private const val COUNT_BADGE_SOURCE_KEY = "tower-count-badge-source"
private const val BADGE_IMAGE_PREFIX = "badge-"
private const val BADGE_ICON_PROPERTY = "badgeIcon"
private const val MAX_BADGE_COUNT = 9

/**
 * A MapNode that renders a count badge on top of tower icons when 2+ towers share the same
 * exact coordinates. Uses programmatically generated bitmap images instead of text rendering,
 * ensuring the badges work on all map tile sources regardless of glyph availability.
 */
internal class TowerCountBadgeNode(
    private val style: Style,
    private val sourceId: String,
    private val layerId: String,
    initialTowers: List<TowerWrapper>,
) : MapNode {
    private val source = GeoJsonSource(sourceId, FeatureCollection.fromFeatures(emptyArray()))
    private val layer = SymbolLayer(layerId, sourceId)

    init {
        registerBadgeImages(style)
        style.addSource(source)

        style.addLayer(
            layer.withProperties(
                iconImage(get(BADGE_ICON_PROPERTY)),
                iconAllowOverlap(true),
                iconIgnorePlacement(true),
                iconOffset(arrayOf(12f, -12f))
            )
        )

        updateData(initialTowers)
    }

    /**
     * Groups towers by exact (lat, lon), filters to locations with 2+ towers,
     * and creates one icon feature per group.
     */
    fun updateData(towers: List<TowerWrapper>) {
        val grouped = towers.groupBy { Pair(it.tower.lat, it.tower.lon) }
        val features = grouped
            .filter { it.value.size >= 2 }
            .map { (coords, group) ->
                val count = min(group.size, MAX_BADGE_COUNT)
                val imageKey = if (group.size > MAX_BADGE_COUNT) {
                    "${BADGE_IMAGE_PREFIX}${MAX_BADGE_COUNT}+"
                } else {
                    "$BADGE_IMAGE_PREFIX$count"
                }
                Feature.fromGeometry(Point.fromLngLat(coords.second, coords.first)).apply {
                    addStringProperty(BADGE_ICON_PROPERTY, imageKey)
                }
            }
        source.setGeoJson(FeatureCollection.fromFeatures(features.toTypedArray()))
    }

    override fun onRemoved() {
        try {
            style.removeLayer(layerId)
        } catch (_: Exception) {
        }
        try {
            style.removeSource(sourceId)
        } catch (_: Exception) {
        }
    }

    override fun onCleared() = onRemoved()

    companion object {
        /**
         * Generates badge bitmaps for counts 2-9 and "9+" and registers them with the map style.
         * Each badge is a small filled circle with a centered number, rendered as a bitmap image
         * so it is completely independent of the map style's glyph/font sources.
         */
        private fun registerBadgeImages(style: Style) {
            for (count in 2..MAX_BADGE_COUNT) {
                val key = "$BADGE_IMAGE_PREFIX$count"
                if (style.getImage(key) == null) {
                    style.addImage(key, createBadgeBitmap(count.toString()))
                }
            }
            val overflowKey = "${BADGE_IMAGE_PREFIX}${MAX_BADGE_COUNT}+"
            if (style.getImage(overflowKey) == null) {
                style.addImage(overflowKey, createBadgeBitmap("${MAX_BADGE_COUNT}+"))
            }
        }

        private fun createBadgeBitmap(label: String): Bitmap {
            val sizePx = 40
            val bitmap = createBitmap(sizePx, sizePx)
            val canvas = Canvas(bitmap)

            val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = "#CC333333".toColorInt()
                style = Paint.Style.FILL
            }

            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                typeface = Typeface.DEFAULT_BOLD
                textSize = if (label.length > 1) 18f else 20f
                textAlign = Paint.Align.CENTER
            }

            val cx = sizePx / 2f
            val cy = sizePx / 2f
            canvas.drawCircle(cx, cy, sizePx / 2f - 1f, circlePaint)

            val textY = cy - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(label, cx, textY, textPaint)

            return bitmap
        }
    }
}

/**
 * Composable that renders count badges on the map for locations where multiple towers share
 * exact coordinates. Only displays badges for locations with 2 or more towers.
 */
@Composable
fun TowerCountBadge(
    towerWrapperList: List<TowerWrapper>,
) {
    val mapApplier = currentComposer.applier as MapApplier
    val style = mapApplier.style

    ComposeNode<TowerCountBadgeNode, MapApplier>(
        factory = {
            TowerCountBadgeNode(
                style = style,
                sourceId = COUNT_BADGE_SOURCE_KEY,
                layerId = COUNT_BADGE_LAYER_KEY,
                initialTowers = towerWrapperList,
            )
        },
        update = {
            set(towerWrapperList) { newTowers ->
                updateData(newTowers)
            }
        }
    )
}
