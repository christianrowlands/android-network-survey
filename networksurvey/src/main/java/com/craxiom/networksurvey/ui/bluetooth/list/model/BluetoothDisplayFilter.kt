package com.craxiom.networksurvey.ui.bluetooth.list.model

import androidx.compose.runtime.Immutable

/**
 * Display-only filter for the Bluetooth list. It changes what is visible on screen and never what
 * is logged, streamed or uploaded.
 *
 * Like the Wi-Fi filter it is ephemeral: it lives in the activity-scoped view model, so it
 * survives sub-tab switches but resets when the app restarts. A filter that silently hid devices
 * across launches would read as a broken scan.
 *
 * @property query Matched, trimmed and case-insensitive, against the name, the address (also with
 *                 the ":" and "-" separators removed from both sides) and the company.
 * @property namedOnly Hides devices that have not broadcast a name.
 * @property trackersOnly Shows only devices tagged as Find My trackers.
 */
@Immutable
data class BluetoothDisplayFilter(
    val query: String = "",
    val namedOnly: Boolean = false,
    val trackersOnly: Boolean = false,
) {
    /** True when the filter would hide anything. */
    val isActive: Boolean get() = query.isNotBlank() || namedOnly || trackersOnly

    /** @return True when [device] passes the filter. */
    fun matches(device: BluetoothDeviceDisplay): Boolean {
        if (namedOnly && !device.hasName) return false
        if (trackersOnly && device.tracker == null) return false

        val trimmed = query.trim()
        if (trimmed.isEmpty()) return true
        if (device.name.contains(trimmed, ignoreCase = true)) return true
        if (device.companyName.contains(trimmed, ignoreCase = true)) return true
        if (device.address.contains(trimmed, ignoreCase = true)) return true

        val bareQuery = stripSeparators(trimmed)
        return bareQuery.isNotEmpty() && stripSeparators(device.address).contains(bareQuery, ignoreCase = true)
    }

    private fun stripSeparators(value: String): String = value.replace(":", "").replace("-", "")
}
