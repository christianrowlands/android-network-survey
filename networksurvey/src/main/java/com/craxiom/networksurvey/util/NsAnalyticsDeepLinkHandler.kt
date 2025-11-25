package com.craxiom.networksurvey.util

import android.content.Intent
import android.net.Uri
import com.craxiom.networksurvey.constants.NsAnalyticsConstants
import com.craxiom.networksurvey.data.api.NsAnalyticsQrData
import timber.log.Timber

/**
 * Handles parsing and validation of NS Analytics deep links.
 *
 * Deep links have the format:
 * https://networksurvey.app/app/register?token=xxx&workspace_id=yyy&api_url=https://...
 */
object NsAnalyticsDeepLinkHandler {

    /** Maximum allowed length for API URLs to prevent DoS attacks. */
    private const val MAX_API_URL_LENGTH = 255

    /** Maximum allowed length for workspace names to prevent UI rendering issues. */
    private const val MAX_WORKSPACE_NAME_LENGTH = 100

    /**
     * Result of parsing a deep link.
     */
    sealed class DeepLinkResult {
        data class Success(val qrData: NsAnalyticsQrData) : DeepLinkResult()
        data class Error(val message: String) : DeepLinkResult()
        data object NotApplicable : DeepLinkResult()
    }

    /**
     * Check if a URI is an NS Analytics registration deep link.
     */
    fun isNsAnalyticsDeepLinkUri(uri: Uri): Boolean {
        return uri.host == NsAnalyticsConstants.NS_ANALYTICS_DEEP_LINK_HOST &&
                uri.path?.startsWith(NsAnalyticsConstants.NS_ANALYTICS_DEEP_LINK_PATH) == true
    }

    /**
     * Parse an intent to extract NS Analytics QR data from the deep link.
     *
     * @param intent The incoming intent from the deep link
     * @return DeepLinkResult indicating success, error, or not applicable
     */
    fun parseIntent(intent: Intent?): DeepLinkResult {
        if (intent?.action != Intent.ACTION_VIEW) {
            return DeepLinkResult.NotApplicable
        }

        val uri = intent.data ?: return DeepLinkResult.NotApplicable

        return parseUri(uri)
    }

    /**
     * Parse a URI to extract NS Analytics QR data.
     *
     * @param uri The deep link URI
     * @return DeepLinkResult indicating success, error, or not applicable
     */
    fun parseUri(uri: Uri): DeepLinkResult {
        if (!isNsAnalyticsDeepLinkUri(uri)) {
            return DeepLinkResult.NotApplicable
        }

        Timber.d("Parsing NS Analytics deep link: %s", uri)

        // Extract required parameters
        val token = uri.getQueryParameter(NsAnalyticsConstants.DEEP_LINK_PARAM_TOKEN)
        val workspaceId = uri.getQueryParameter(NsAnalyticsConstants.DEEP_LINK_PARAM_WORKSPACE_ID)
        val apiUrl = uri.getQueryParameter(NsAnalyticsConstants.DEEP_LINK_PARAM_API_URL)

        // Extract optional parameters (URL decoding is handled automatically by Android)
        val workspaceName =
            uri.getQueryParameter(NsAnalyticsConstants.DEEP_LINK_PARAM_WORKSPACE_NAME)
                ?.takeIf { it.isNotBlank() }

        // Validate workspace name length if provided
        if (workspaceName != null && workspaceName.length > MAX_WORKSPACE_NAME_LENGTH) {
            Timber.w("Deep link workspace_name exceeds max length: %d", workspaceName.length)
            return DeepLinkResult.Error("Workspace name too long")
        }

        // Validate required parameters
        if (token.isNullOrBlank()) {
            Timber.w("Deep link missing required parameter: token")
            return DeepLinkResult.Error("Missing registration token")
        }

        if (workspaceId.isNullOrBlank()) {
            Timber.w("Deep link missing required parameter: workspace_id")
            return DeepLinkResult.Error("Missing workspace ID")
        }

        if (apiUrl.isNullOrBlank()) {
            Timber.w("Deep link missing required parameter: api_url")
            return DeepLinkResult.Error("Missing API URL")
        }

        // Validate API URL format
        if (!apiUrl.startsWith("https://")) {
            Timber.w("Deep link has invalid api_url: %s", apiUrl)
            return DeepLinkResult.Error("Invalid API URL: must use HTTPS")
        }

        // Validate URL is parseable (URI is stricter than URL and consistent across platforms)
        val parsedUri = try {
            java.net.URI(apiUrl)
        } catch (e: Exception) {
            Timber.w("Deep link has malformed api_url: %s", apiUrl)
            return DeepLinkResult.Error("Invalid API URL format")
        }

        // Block internal/private addresses (SSRF prevention)
        val host = parsedUri.host?.lowercase()
            ?: return DeepLinkResult.Error("Invalid API URL format")
        if (host == "localhost" ||
            host.matches(Regex("^127\\.\\d+\\.\\d+\\.\\d+$")) ||
            host.matches(Regex("^10\\.\\d+\\.\\d+\\.\\d+$")) ||
            host.matches(Regex("^172\\.(1[6-9]|2\\d|3[01])\\.\\d+\\.\\d+$")) ||
            host.matches(Regex("^192\\.168\\.\\d+\\.\\d+$")) ||
            host.matches(Regex("^169\\.254\\.\\d+\\.\\d+$"))
        ) {
            Timber.w("Deep link has blocked internal api_url: %s", apiUrl)
            return DeepLinkResult.Error("API URL cannot be an internal address")
        }

        // Validate max length
        if (apiUrl.length > MAX_API_URL_LENGTH) {
            Timber.w("Deep link api_url exceeds max length: %d", apiUrl.length)
            return DeepLinkResult.Error("API URL too long")
        }

        val qrData = NsAnalyticsQrData(
            token = token,
            workspaceId = workspaceId,
            apiUrl = apiUrl,
            workspaceName = workspaceName
        )

        Timber.i(
            "Successfully parsed NS Analytics deep link for workspace: %s (%s)",
            workspaceName ?: workspaceId,
            workspaceId
        )
        return DeepLinkResult.Success(qrData)
    }
}
