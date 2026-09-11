package com.craxiom.networksurvey.ui.cellular.towermap

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import org.maplibre.android.maps.Style
import kotlin.math.min

/**
 * Generates and registers the small numbered circle bitmaps used as count badges on tower
 * icons. Bitmaps are used instead of text rendering so the badges work on every map tile
 * source regardless of glyph availability. Two families exist: a dark badge for the number of
 * towers sharing a location, and a blue badge for the number of distinct operators there.
 */
internal object BadgeImages {
    private const val COUNT_PREFIX = "badge-"
    private const val OPERATOR_PREFIX = "op-badge-"
    private const val MAX_BADGE_COUNT = 9
    private const val OVERFLOW_LABEL = "$MAX_BADGE_COUNT+"
    private const val SIZE_PX = 40
    private const val COUNT_COLOR = "#CC333333"
    private const val OPERATOR_COLOR = "#CC1565C0"

    /** Style image key for a tower count badge. */
    fun countKey(count: Int): String = key(COUNT_PREFIX, count)

    /** Style image key for an operator count badge. */
    fun operatorKey(count: Int): String = key(OPERATOR_PREFIX, count)

    /** Registers every badge bitmap with the style, skipping any that are already present. */
    fun register(style: Style) {
        registerFamily(style, COUNT_PREFIX, COUNT_COLOR.toColorInt())
        registerFamily(style, OPERATOR_PREFIX, OPERATOR_COLOR.toColorInt())
    }

    private fun key(prefix: String, count: Int): String =
        if (count > MAX_BADGE_COUNT) prefix + OVERFLOW_LABEL else prefix + min(count, MAX_BADGE_COUNT)

    private fun registerFamily(style: Style, prefix: String, color: Int) {
        for (count in 2..MAX_BADGE_COUNT) {
            val imageKey = prefix + count
            if (style.getImage(imageKey) == null) {
                style.addImage(imageKey, createBadgeBitmap(count.toString(), color))
            }
        }
        val overflowKey = prefix + OVERFLOW_LABEL
        if (style.getImage(overflowKey) == null) {
            style.addImage(overflowKey, createBadgeBitmap(OVERFLOW_LABEL, color))
        }
    }

    private fun createBadgeBitmap(label: String, color: Int): Bitmap {
        val bitmap = createBitmap(SIZE_PX, SIZE_PX)
        val canvas = Canvas(bitmap)

        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            typeface = Typeface.DEFAULT_BOLD
            textSize = if (label.length > 1) 18f else 20f
            textAlign = Paint.Align.CENTER
        }

        val cx = SIZE_PX / 2f
        val cy = SIZE_PX / 2f
        canvas.drawCircle(cx, cy, SIZE_PX / 2f - 1f, circlePaint)
        val textY = cy - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(label, cx, textY, textPaint)
        return bitmap
    }
}
