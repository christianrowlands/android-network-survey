package com.craxiom.networksurvey.util

import android.content.Intent
import android.net.Uri
import android.util.Base64
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.constants.NsAnalyticsConstants
import com.craxiom.networksurvey.model.WatchlistImportEntry
import com.craxiom.networksurvey.model.WatchlistImportSet
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import timber.log.Timber

/**
 * Parses and validates watchlist import deep links.
 *
 * Links have the format:
 * https://networksurvey.app/app/watchlist?d=&lt;base64url-json&gt;
 *
 * The "d" parameter is the base64url-encoded (URL-safe alphabet, no padding) UTF-8 JSON payload:
 *
 *     { "v": 1, "name": "Acme networks",
 *       "entries": [ { "label": "HQ", "ssid": "HQ-Guest" }, { "bssid": "a4:..:1c" } ] }
 *
 * The link is treated as coming from a semi-trusted sender, so every field is bounded and sanitized
 * here before it can reach the UI or the database. This mirrors the hardening already established by
 * [NsAnalyticsDeepLinkHandler]: a sealed Success/Error/NotApplicable result, strict all-or-nothing
 * parsing, and rejection (never truncation) of anything over a cap.
 */
object WatchlistDeepLinkHandler {

    /** Maximum length of the raw "d" query parameter, checked before decoding to bound work. */
    const val MAX_ENCODED_LENGTH = 16 * 1024

    /** Maximum number of networks a single link may carry, to prevent flooding the watchlist. */
    const val MAX_ENTRIES = 100

    /** Maximum SSID length (the 802.11 limit). */
    const val MAX_SSID_LENGTH = 32

    /** Maximum label length, to bound UI rendering. */
    const val MAX_LABEL_LENGTH = 100

    /** Maximum set-name length, to bound the prompt title. */
    const val MAX_SET_NAME_LENGTH = 100

    /** The only payload schema version this app understands. */
    const val SUPPORTED_VERSION = 1

    const val ERROR_NO_DATA = "This link has no watchlist data"
    const val ERROR_TOO_LARGE = "This watchlist link is too large"
    const val ERROR_UNREADABLE = "This watchlist link could not be read"
    const val ERROR_UNSUPPORTED_VERSION = "This watchlist link needs a newer app version"
    const val ERROR_NO_NETWORKS = "This link contains no networks"
    const val ERROR_NO_VALID_NETWORKS = "This link contains no valid networks"
    const val ERROR_TOO_MANY = "This link contains too many networks"
    const val ERROR_INVALID_BSSID = "This link contains an invalid BSSID"
    const val ERROR_SSID_TOO_LONG = "This link contains an SSID that is too long"
    const val ERROR_LABEL_TOO_LONG = "This link contains a label that is too long"
    const val ERROR_NAME_TOO_LONG = "This link has a list name that is too long"

    private val BSSID_REGEX = Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")

    private val gson = Gson()

    /** Result of parsing a watchlist import deep link. */
    sealed class Result {
        data class Success(val importSet: WatchlistImportSet) : Result()
        data class Error(val message: String) : Result()
        data object NotApplicable : Result()
    }

    /**
     * The JSON payload as received on the wire, before validation. Entry elements are nullable because
     * Gson does not honor Kotlin nullability and will inject a null element for a JSON null.
     */
    private data class Payload(
        @SerializedName("v") val version: Int?,
        @SerializedName("name") val name: String?,
        @SerializedName("entries") val entries: List<WatchlistImportEntry?>?
    )

    /** True when [uri] targets the watchlist import host and path. */
    fun isWatchlistImportUri(uri: Uri): Boolean {
        return uri.host == NsAnalyticsConstants.NS_ANALYTICS_DEEP_LINK_HOST &&
                uri.path?.startsWith(NetworkSurveyConstants.WATCHLIST_DEEP_LINK_PATH) == true
    }

    /**
     * Parse an incoming intent. Returns [Result.NotApplicable] for anything that is not a watchlist
     * import VIEW intent, so the caller can run this alongside other deep-link handlers harmlessly.
     */
    fun parseIntent(intent: Intent?): Result {
        if (intent?.action != Intent.ACTION_VIEW) {
            return Result.NotApplicable
        }
        val uri = intent.data ?: return Result.NotApplicable
        return parseUri(uri)
    }

    /** Parse and validate a watchlist import URI. */
    fun parseUri(uri: Uri): Result {
        if (!isWatchlistImportUri(uri)) {
            return Result.NotApplicable
        }

        val encoded = uri.getQueryParameter(NetworkSurveyConstants.WATCHLIST_DEEP_LINK_PARAM_DATA)
        if (encoded.isNullOrBlank()) {
            return Result.Error(ERROR_NO_DATA)
        }
        if (encoded.length > MAX_ENCODED_LENGTH) {
            Timber.w("Watchlist link payload exceeds max encoded length: %d", encoded.length)
            return Result.Error(ERROR_TOO_LARGE)
        }

        val payload = decodePayload(encoded) ?: return Result.Error(ERROR_UNREADABLE)

        if (payload.version != SUPPORTED_VERSION) {
            Timber.w("Watchlist link has unsupported version: %s", payload.version)
            return Result.Error(ERROR_UNSUPPORTED_VERSION)
        }

        val rawEntries = payload.entries
        if (rawEntries.isNullOrEmpty()) {
            return Result.Error(ERROR_NO_NETWORKS)
        }
        if (rawEntries.size > MAX_ENTRIES) {
            Timber.w("Watchlist link has too many entries: %d", rawEntries.size)
            return Result.Error(ERROR_TOO_MANY)
        }

        val validated = ArrayList<WatchlistImportEntry>(rawEntries.size)
        for (raw in rawEntries) {
            if (raw == null) continue // Gson can inject a null list element for a JSON null
            when (val outcome = validateEntry(raw)) {
                is EntryOutcome.Valid -> validated.add(outcome.entry)
                is EntryOutcome.Drop -> Unit // entry has no identity; skip it
                is EntryOutcome.Reject -> return Result.Error(outcome.message)
            }
        }
        if (validated.isEmpty()) {
            return Result.Error(ERROR_NO_VALID_NETWORKS)
        }

        val name = sanitize(payload.name)?.takeIf { it.isNotBlank() }
        if (name != null && name.length > MAX_SET_NAME_LENGTH) {
            return Result.Error(ERROR_NAME_TOO_LONG)
        }

        Timber.i("Parsed watchlist import link: %d networks (name=%s)", validated.size, name)
        return Result.Success(WatchlistImportSet(name = name, entries = validated))
    }

    /** Decode and JSON-parse the payload, returning null on any base64 or JSON failure. */
    private fun decodePayload(encoded: String): Payload? {
        return try {
            val bytes =
                Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            val json = String(bytes, Charsets.UTF_8)
            gson.fromJson(json, Payload::class.java)
        } catch (e: Exception) {
            Timber.w(e, "Failed to decode watchlist link payload")
            null
        }
    }

    private sealed class EntryOutcome {
        data class Valid(val entry: WatchlistImportEntry) : EntryOutcome()
        data object Drop : EntryOutcome()
        data class Reject(val message: String) : EntryOutcome()
    }

    /** Normalize and validate a single entry, mirroring the rules in WatchlistViewModel.addEntry. */
    private fun validateEntry(raw: WatchlistImportEntry): EntryOutcome {
        val ssid = sanitize(raw.ssid)?.takeIf { it.isNotEmpty() }
        val bssidTrimmed = raw.bssid?.trim()?.takeIf { it.isNotEmpty() }

        if (bssidTrimmed != null && !BSSID_REGEX.matches(bssidTrimmed)) {
            return EntryOutcome.Reject(ERROR_INVALID_BSSID)
        }
        val bssid = bssidTrimmed?.lowercase()

        if (ssid == null && bssid == null) {
            return EntryOutcome.Drop
        }
        if (ssid != null && ssid.length > MAX_SSID_LENGTH) {
            return EntryOutcome.Reject(ERROR_SSID_TOO_LONG)
        }

        val label = sanitize(raw.label)?.takeIf { it.isNotEmpty() }
        if (label != null && label.length > MAX_LABEL_LENGTH) {
            return EntryOutcome.Reject(ERROR_LABEL_TOO_LONG)
        }

        return EntryOutcome.Valid(WatchlistImportEntry(label = label, ssid = ssid, bssid = bssid))
    }

    /**
     * Strip control characters and Unicode format characters (zero-width spaces, bidi overrides, and
     * the like) from attacker-controlled display text, then trim. This prevents the set name or a label
     * from breaking or spoofing the prompt layout.
     */
    private fun sanitize(value: String?): String? {
        return value?.filterNot { it.isISOControl() || it.category == CharCategory.FORMAT }?.trim()
    }
}
