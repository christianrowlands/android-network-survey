package com.craxiom.networksurvey.data.oui

/**
 * Classification of an OUI lookup outcome. Drives UI rendering and cache policy.
 *
 * UI-only model. Not persisted in any CSV / protobuf / MQTT / gRPC output.
 */
enum class OuiStatus {
    /** Vendor successfully identified. [OuiResult.vendor] is populated. */
    RESOLVED,

    /** MAC is locally administered (WiFi randomization, BLE RPA). Detected client-side; never sent. */
    LAA,

    /** No vendor matches this prefix. Server returned `reason=no_match`. */
    UNKNOWN,

    /**
     * Server explicitly returned the literal `"Private"` vendor string. The IEEE-registered
     * vendor requested privacy. Distinct from UNKNOWN.
     */
    PRIVATE,

    /**
     * Server returned `reason=registered_to_ieee_subregistry`. The 24-bit prefix delegates to
     * smaller MA-M/MA-S sub-blocks; we can't disambiguate from just the prefix. UI calls this
     * "Shared vendor block."
     */
    SHARED_VENDOR_BLOCK,

    /** The `oui_lookup_enabled` preference is off. No network call was made. */
    LOOKUP_DISABLED,

    /** The network is unreachable or the user is in Data-Saver mode. Short-lived; retried on reconnect. */
    OFFLINE,

    /** The lookup is in-flight. Surfaces "Looking up…" in the UI after a short delay. */
    LOADING,

    /** Server returned a persistent error (5xx / 4xx). Session-scoped. */
    TRANSIENT_FAILURE
}

/**
 * Outcome of an OUI lookup for a single MAC address.
 *
 * The [vendor] field is non-null only when [status] is [OuiStatus.RESOLVED] or
 * [OuiStatus.PRIVATE] (the latter carries the literal "Private" string).
 *
 * UI-only. Do not persist to CSV, protobuf, MQTT, or gRPC outputs.
 */
data class OuiResult(
    val status: OuiStatus,
    val vendor: String? = null
) {
    companion object {
        val LAA = OuiResult(OuiStatus.LAA)
        val UNKNOWN = OuiResult(OuiStatus.UNKNOWN)
        val SHARED_VENDOR_BLOCK = OuiResult(OuiStatus.SHARED_VENDOR_BLOCK)
        val LOOKUP_DISABLED = OuiResult(OuiStatus.LOOKUP_DISABLED)
        val OFFLINE = OuiResult(OuiStatus.OFFLINE)
        val LOADING = OuiResult(OuiStatus.LOADING)
        val TRANSIENT_FAILURE = OuiResult(OuiStatus.TRANSIENT_FAILURE)

        fun resolved(vendor: String) = OuiResult(OuiStatus.RESOLVED, vendor)
        fun privateVendor(vendor: String) = OuiResult(OuiStatus.PRIVATE, vendor)
    }
}
