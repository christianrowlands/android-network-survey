package com.craxiom.networksurvey.model

import com.google.gson.annotations.SerializedName

/**
 * A validated set of watched networks parsed from a watchlist import deep link. The set name, when
 * present, is sanitized untrusted display text used only to title the import confirmation prompt; it
 * is not persisted. Each entry has already been validated and normalized (SSID trimmed, BSSID
 * lowercased) by the parser.
 */
data class WatchlistImportSet(
    val name: String?,
    val entries: List<WatchlistImportEntry>
)

/**
 * A single network to add to the watchlist, carrying only identity fields. At least one of [ssid] or
 * [bssid] is non-null. [label] is the optional user-facing name; when null the consumer falls back to
 * the SSID, then the BSSID. This is also the Gson wire model for an entry in an import link, so the
 * fields are annotated to survive minification.
 */
data class WatchlistImportEntry(
    @SerializedName("label") val label: String?,
    @SerializedName("ssid") val ssid: String?,
    @SerializedName("bssid") val bssid: String?
)
