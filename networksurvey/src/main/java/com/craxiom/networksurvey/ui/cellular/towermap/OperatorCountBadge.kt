package com.craxiom.networksurvey.ui.cellular.towermap

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.currentComposer
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
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

private const val OPERATOR_BADGE_LAYER_KEY = "operator-count-badge-layer"
private const val OPERATOR_BADGE_SOURCE_KEY = "operator-count-badge-source"
private const val OP_BADGE_IMAGE_PREFIX = "op-badge-"
private const val OP_BADGE_ICON_PROPERTY = "opBadgeIcon"
private const val MAX_OPERATOR_COUNT = 9

/**
 * A MapNode that renders an operator count badge on tower icons when 2+ distinct operators
 * (unique MCC-MNC combinations) share the same exact coordinates. Uses a blue circle badge
 * positioned at the bottom-right of the tower icon.
 */
internal class OperatorCountBadgeNode(
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
                iconImage(get(OP_BADGE_ICON_PROPERTY)),
                iconAllowOverlap(true),
                iconIgnorePlacement(true),
                iconOffset(arrayOf(12f, 12f))
            )
        )

        updateData(initialTowers)
    }

    /**
     * Groups towers by exact (lat, lon), counts unique operators (MCC-MNC) per group,
     * and creates one icon feature per group where 2+ operators are present.
     */
    fun updateData(towers: List<TowerWrapper>) {
        val grouped = towers.groupBy { Pair(it.tower.lat, it.tower.lon) }
        val features = grouped
            .mapNotNull { (coords, group) ->
                val uniqueOperators = group
                    .map { "${it.tower.mcc}-${it.tower.mnc}" }
                    .toSet()
                    .size
                if (uniqueOperators < 2) return@mapNotNull null

                val count = min(uniqueOperators, MAX_OPERATOR_COUNT)
                val imageKey = if (uniqueOperators > MAX_OPERATOR_COUNT) {
                    "${OP_BADGE_IMAGE_PREFIX}${MAX_OPERATOR_COUNT}+"
                } else {
                    "$OP_BADGE_IMAGE_PREFIX$count"
                }
                Feature.fromGeometry(Point.fromLngLat(coords.second, coords.first)).apply {
                    addStringProperty(OP_BADGE_ICON_PROPERTY, imageKey)
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
         * Generates blue badge bitmaps for counts 2-9 and "9+" and registers them with the map
         * style. Each badge is a small filled blue circle with a centered number.
         */
        private fun registerBadgeImages(style: Style) {
            for (count in 2..MAX_OPERATOR_COUNT) {
                val key = "$OP_BADGE_IMAGE_PREFIX$count"
                if (style.getImage(key) == null) {
                    style.addImage(key, createBadgeBitmap(count.toString()))
                }
            }
            val overflowKey = "${OP_BADGE_IMAGE_PREFIX}${MAX_OPERATOR_COUNT}+"
            if (style.getImage(overflowKey) == null) {
                style.addImage(overflowKey, createBadgeBitmap("${MAX_OPERATOR_COUNT}+"))
            }
        }

        private fun createBadgeBitmap(label: String): Bitmap {
            val sizePx = 40
            val bitmap = createBitmap(sizePx, sizePx)
            val canvas = Canvas(bitmap)

            val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = "#CC1565C0".toColorInt()
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
 * Composable that renders operator count badges on the map for locations where multiple distinct
 * operators (unique MCC-MNC combinations) share exact coordinates. Only displays badges for
 * locations with 2 or more unique operators.
 */
@Composable
fun OperatorCountBadge(
    towerWrapperList: List<TowerWrapper>,
) {
    val mapApplier = currentComposer.applier as MapApplier
    val style = mapApplier.style

    ComposeNode<OperatorCountBadgeNode, MapApplier>(
        factory = {
            OperatorCountBadgeNode(
                style = style,
                sourceId = OPERATOR_BADGE_SOURCE_KEY,
                layerId = OPERATOR_BADGE_LAYER_KEY,
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
