package com.craxiom.networksurvey.util

import androidx.compose.ui.graphics.Color

/**
 * Deterministically maps any (MCC, MNC) pair to one of 16 curated Material Design 600-level
 * colors using a hash. The same provider always gets the same color across restarts and
 * installations.
 *
 * The palette uses 16 colors chosen for perceptual distinctness and good contrast on both
 * light and dark backgrounds.
 */
object PlmnColorMapper {

    private const val PALETTE_SIZE = 16

    private val PALETTE = arrayOf(
        Color(0xFFE53935), // Red 600
        Color(0xFFD81B60), // Pink 600
        Color(0xFF8E24AA), // Purple 600
        Color(0xFF5E35B1), // Deep Purple 600
        Color(0xFF3949AB), // Indigo 600
        Color(0xFF1E88E5), // Blue 600
        Color(0xFF039BE5), // Light Blue 600
        Color(0xFF00ACC1), // Cyan 600
        Color(0xFF00897B), // Teal 600
        Color(0xFF43A047), // Green 600
        Color(0xFF7CB342), // Light Green 600
        Color(0xFF546E7A), // Blue Grey 600
        Color(0xFF6D4C41), // Brown 600
        Color(0xFFFFB300), // Amber 600
        Color(0xFFFB8C00), // Orange 600
        Color(0xFFF4511E), // Deep Orange 600
    )

    /**
     * Returns the palette index for the given MCC/MNC combination.
     */
    fun getColorIndex(mcc: Int, mnc: Int): Int {
        return (mcc * 31 + mnc).and(0x7FFFFFFF) % PALETTE_SIZE
    }

    /**
     * Returns the Compose [Color] for the given MCC/MNC combination.
     */
    fun getColor(mcc: Int, mnc: Int): Color {
        return PALETTE[getColorIndex(mcc, mnc)]
    }

    /**
     * Returns the ARGB int color for the given MCC/MNC combination, suitable for
     * Canvas or MapLibre usage.
     */
    fun getColorArgb(mcc: Int, mnc: Int): Int {
        val c = PALETTE[getColorIndex(mcc, mnc)]
        return android.graphics.Color.argb(
            (c.alpha * 255).toInt(),
            (c.red * 255).toInt(),
            (c.green * 255).toInt(),
            (c.blue * 255).toInt()
        )
    }
}
