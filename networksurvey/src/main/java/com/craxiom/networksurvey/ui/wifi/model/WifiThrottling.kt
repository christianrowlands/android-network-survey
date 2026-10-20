package com.craxiom.networksurvey.ui.wifi.model

import android.os.Build

/**
 * A warning that Android throttles Wi-Fi scans, so the list updates less often than the scan
 * interval asks for.
 *
 * @property android9 Android 9's fixed throttle (two scans a minute), which no setting turns off.
 * @property developerOptionsOn Whether the throttle setting can be reached (developer options are on);
 * when off, the warning also says how to turn them on and "Open" goes to About phone.
 */
data class ThrottleWarning(val android9: Boolean, val developerOptionsOn: Boolean)

/** When the Wi-Fi list warns about scan throttling. Pure, so it is tested on the JVM. */
object WifiThrottling {
    /** Scans further apart than this many scan intervals count as throttled (Android 9 and 10). */
    const val GAP_FACTOR = 3

    /**
     * @param scanGapMs The time since the previous scan, or null for the first scan after a start.
     * @return The warning to show, or null. Android 11+ reads the throttle setting directly; Android 9
     * and 10 cannot, so they warn when developer options are off (the setting is out of reach) or the
     * scans arrive much slower than the interval. Android 8 does not throttle.
     */
    fun warning(sdk: Int, inputs: WifiThrottleInputs, scanGapMs: Long?, scanRateMs: Long): ThrottleWarning? {
        if (inputs.ignored || sdk < Build.VERSION_CODES.P) return null
        if (sdk >= Build.VERSION_CODES.R) {
            return if (inputs.throttleEnabled) ThrottleWarning(android9 = false, inputs.developerOptionsOn) else null
        }
        if (scanGapMs == null) return null
        val throttled = !inputs.developerOptionsOn || scanGapMs > scanRateMs * GAP_FACTOR
        return if (throttled) ThrottleWarning(android9 = sdk == Build.VERSION_CODES.P, inputs.developerOptionsOn) else null
    }
}
