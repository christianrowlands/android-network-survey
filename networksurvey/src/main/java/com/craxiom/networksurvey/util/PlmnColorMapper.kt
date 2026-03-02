package com.craxiom.networksurvey.util

import androidx.compose.ui.graphics.Color
import com.craxiom.networksurvey.util.PlmnColorMapper.PALETTE

/**
 * Deterministically maps any (MCC, MNC) pair to one of 16 curated Material Design 600-level
 * colors using a hash. The same provider always gets the same color across restarts and
 * installations. User-assigned overrides take priority over the hash-based default.
 *
 * The palette uses 16 colors chosen for perceptual distinctness and good contrast on both
 * light and dark backgrounds.
 */
object PlmnColorMapper {

    const val PALETTE_SIZE = 16

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
     * Human-readable names for each palette color, indexed to match [PALETTE].
     */
    val PALETTE_NAMES = arrayOf(
        "Red", "Pink", "Purple", "Deep Purple",
        "Indigo", "Blue", "Light Blue", "Cyan",
        "Teal", "Green", "Light Green", "Blue Grey",
        "Brown", "Amber", "Orange", "Deep Orange"
    )

    // Thread safety: this reference is replaced atomically via @Volatile — the map itself
    // is never mutated in place, so reads on any thread (including MapLibre's render thread)
    // always see a consistent snapshot.
    @Volatile
    private var overrides: Map<String, Int> = emptyMap()

    /**
     * Replaces the current set of user overrides. Called by [PlmnColorOverrideManager] on
     * every mutation and at startup.
     */
    fun refreshOverrides(newOverrides: Map<String, Int>) {
        overrides = newOverrides
    }

    /**
     * Returns the palette index for the given MCC/MNC combination, respecting user overrides.
     */
    fun getColorIndex(mcc: Int, mnc: Int): Int {
        overrides["$mcc-$mnc"]?.let { return it }
        return getDefaultColorIndex(mcc, mnc)
    }

    /**
     * Returns the hash-only palette index, ignoring any user override. Useful for showing
     * "Default: Blue" labels in the UI.
     */
    fun getDefaultColorIndex(mcc: Int, mnc: Int): Int {
        return (mcc * 31 + mnc).and(0x7FFFFFFF) % PALETTE_SIZE
    }

    /**
     * Returns the Compose [Color] at the given palette index (clamped to 0–15).
     */
    fun getColorByIndex(index: Int): Color {
        return PALETTE[index.coerceIn(0, PALETTE_SIZE - 1)]
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
