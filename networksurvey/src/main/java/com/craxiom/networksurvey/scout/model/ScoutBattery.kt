package com.craxiom.networksurvey.scout.model

import com.craxiom.networksurvey.board.v1.BoardStatus
import kotlin.math.roundToInt

/**
 * Scout battery charge from the reported cell voltage (one LiPo cell), with the same piecewise
 * linear curve as the iOS app and docs/hardware.md: 4.20 V full, about 3.80 V half, 3.30 V empty.
 * Boards powered over USB report no voltage.
 */
object ScoutBattery {
    /** At or below this charge the Out of range state also says the battery may be empty. */
    const val LOW_PERCENT = 10

    private val CURVE_MV = intArrayOf(3300, 3600, 3700, 3750, 3800, 3900, 4000, 4100, 4200)
    private val CURVE_PERCENT = intArrayOf(0, 10, 25, 40, 50, 65, 80, 92, 100)
    private const val FULL_PERCENT = 100

    /** @return The charge, 0 to 100, for a cell at [millivolts]. */
    @JvmStatic
    fun percent(millivolts: Int): Int {
        if (millivolts <= CURVE_MV.first()) return 0
        for (index in 1 until CURVE_MV.size) {
            if (millivolts <= CURVE_MV[index]) {
                val low = CURVE_MV[index - 1]
                val fraction = (millivolts - low).toDouble() / (CURVE_MV[index] - low)
                val lowPercent = CURVE_PERCENT[index - 1]
                return (lowPercent + fraction * (CURVE_PERCENT[index] - lowPercent)).roundToInt()
            }
        }
        return FULL_PERCENT
    }

    /** @return The charge from [status], or null when the board reports no battery (USB powered). */
    @JvmStatic
    fun percent(status: BoardStatus?): Int? =
        if (status != null && status.hasBatteryMv()) percent(status.batteryMv) else null
}
