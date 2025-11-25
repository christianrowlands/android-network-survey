package com.craxiom.networksurvey.util

import android.net.Uri
import com.craxiom.networksurvey.constants.NsAnalyticsConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [NsAnalyticsDeepLinkHandler].
 * Tests deep link parsing, validation, and security checks.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NsAnalyticsDeepLinkHandlerTest {

    companion object {
        private const val VALID_HOST = NsAnalyticsConstants.NS_ANALYTICS_DEEP_LINK_HOST
        private const val VALID_PATH = NsAnalyticsConstants.NS_ANALYTICS_DEEP_LINK_PATH
        private const val VALID_TOKEN = "test_token_123"
        private const val VALID_WORKSPACE_ID = "ws_abc123"
        private const val VALID_API_URL = "https://api.example.com"

        private fun buildValidUri(
            token: String? = VALID_TOKEN,
            workspaceId: String? = VALID_WORKSPACE_ID,
            apiUrl: String? = VALID_API_URL,
            workspaceName: String? = null
        ): Uri {
            val builder = Uri.Builder()
                .scheme("https")
                .authority(VALID_HOST)
                .path(VALID_PATH)

            token?.let { builder.appendQueryParameter("token", it) }
            workspaceId?.let { builder.appendQueryParameter("workspace_id", it) }
            apiUrl?.let { builder.appendQueryParameter("api_url", it) }
            workspaceName?.let { builder.appendQueryParameter("workspace_name", it) }

            return builder.build()
        }
    }

    // === Valid Deep Links ===

    @Test
    fun `parseUri returns Success for valid deep link with all parameters`() {
        val uri = buildValidUri()

        val result = NsAnalyticsDeepLinkHandler.parseUri(uri)

        assertTrue(result is NsAnalyticsDeepLinkHandler.DeepLinkResult.Success)
        val success = result as NsAnalyticsDeepLinkHandler.DeepLinkResult.Success
        assertEquals(VALID_TOKEN, success.qrData.token)
        assertEquals(VALID_WORKSPACE_ID, success.qrData.workspaceId)
        assertEquals(VALID_API_URL, success.qrData.apiUrl)
    }

    @Test
    fun `parseUri handles URL-encoded parameters correctly`() {
        val encodedApiUrl = "https://api.example.com/v1/path?param=value"
        val uri = buildValidUri(apiUrl = encodedApiUrl)

        val result = NsAnalyticsDeepLinkHandler.parseUri(uri)

        assertTrue(result is NsAnalyticsDeepLinkHandler.DeepLinkResult.Success)
        val success = result as NsAnalyticsDeepLinkHandler.DeepLinkResult.Success
        assertEquals(encodedApiUrl, success.qrData.apiUrl)
    }

    // === Missing Parameters ===

    @Test
    fun `parseUri returns Error when token is missing`() {
        val uri = buildValidUri(token = null)

        val result = NsAnalyticsDeepLinkHandler.parseUri(uri)

        assertTrue(result is NsAnalyticsDeepLinkHandler.DeepLinkResult.Error)
        val error = result as NsAnalyticsDeepLinkHandler.DeepLinkResult.Error
        assertEquals("Missing registration token", error.message)
    }

    @Test
    fun `parseUri returns Error when workspace_id is missing`() {
        val uri = buildValidUri(workspaceId = null)

        val result = NsAnalyticsDeepLinkHandler.parseUri(uri)

        assertTrue(result is NsAnalyticsDeepLinkHandler.DeepLinkResult.Error)
        val error = result as NsAnalyticsDeepLinkHandler.DeepLinkResult.Error
        assertEquals("Missing workspace ID", error.message)
    }

    @Test
    fun `parseUri returns Error when api_url is missing`() {
        val uri = buildValidUri(apiUrl = null)

        val result = NsAnalyticsDeepLinkHandler.parseUri(uri)

        assertTrue(result is NsAnalyticsDeepLinkHandler.DeepLinkResult.Error)
        val error = result as NsAnalyticsDeepLinkHandler.DeepLinkResult.Error
        assertEquals("Missing API URL", error.message)
    }

    @Test
    fun `parseUri returns Error when token is blank`() {
        val uri = buildValidUri(token = "   ")

        val result = NsAnalyticsDeepLinkHandler.parseUri(uri)

        assertTrue(result is NsAnalyticsDeepLinkHandler.DeepLinkResult.Error)
        val error = result as NsAnalyticsDeepLinkHandler.DeepLinkResult.Error
        assertEquals("Missing registration token", error.message)
    }

    @Test
    fun `parseUri returns Error when workspace_id is blank`() {
        val uri = buildValidUri(workspaceId = "")

        val result = NsAnalyticsDeepLinkHandler.parseUri(uri)

        assertTrue(result is NsAnalyticsDeepLinkHandler.DeepLinkResult.Error)
        val error = result as NsAnalyticsDeepLinkHandler.DeepLinkResult.Error
        assertEquals("Missing workspace ID", error.message)
    }

    // === Invalid URLs ===

    @Test
    fun `parseUri returns Error for HTTP api_url (non-HTTPS)`() {
        val uri = buildValidUri(apiUrl = "http://api.example.com")

        val result = NsAnalyticsDeepLinkHandler.parseUri(uri)

        assertTrue(result is NsAnalyticsDeepLinkHandler.DeepLinkResult.Error)
        val error = result as NsAnalyticsDeepLinkHandler.DeepLinkResult.Error
        assertEquals("Invalid API URL: must use HTTPS", error.message)
    }

    @Test
    fun `parseUri returns Error for malformed api_url`() {
        // Use a URL with invalid characters that java.net.URL will reject
        val uri = buildValidUri(apiUrl = "https://invalid url with spaces")

        val result = NsAnalyticsDeepLinkHandler.parseUri(uri)

        assertTrue(result is NsAnalyticsDeepLinkHandler.DeepLinkResult.Error)
        val error = result as NsAnalyticsDeepLinkHandler.DeepLinkResult.Error
        assertEquals("Invalid API URL format", error.message)
    }

    @Test
    fun `parseUri returns Error for api_url exceeding max length`() {
        val longUrl = "https://api.example.com/" + "a".repeat(300)
        val uri = buildValidUri(apiUrl = longUrl)

        val result = NsAnalyticsDeepLinkHandler.parseUri(uri)

        assertTrue(result is NsAnalyticsDeepLinkHandler.DeepLinkResult.Error)
        val error = result as NsAnalyticsDeepLinkHandler.DeepLinkResult.Error
        assertEquals("API URL too long", error.message)
    }

    // === SSRF Prevention ===

    @Test
    fun `parseUri returns Error for localhost api_url`() {
        val uri = buildValidUri(apiUrl = "https://localhost/api")

        val result = NsAnalyticsDeepLinkHandler.parseUri(uri)

        assertTrue(result is NsAnalyticsDeepLinkHandler.DeepLinkResult.Error)
        val error = result as NsAnalyticsDeepLinkHandler.DeepLinkResult.Error
        assertEquals("API URL cannot be an internal address", error.message)
    }

    @Test
    fun `parseUri returns Error for 127-0-0-1 api_url`() {
        val uri = buildValidUri(apiUrl = "https://127.0.0.1/api")

        val result = NsAnalyticsDeepLinkHandler.parseUri(uri)

        assertTrue(result is NsAnalyticsDeepLinkHandler.DeepLinkResult.Error)
        val error = result as NsAnalyticsDeepLinkHandler.DeepLinkResult.Error
        assertEquals("API URL cannot be an internal address", error.message)
    }

    @Test
    fun `parseUri returns Error for 127-x-x-x api_url`() {
        val uri = buildValidUri(apiUrl = "https://127.255.255.255/api")

        val result = NsAnalyticsDeepLinkHandler.parseUri(uri)

        assertTrue(result is NsAnalyticsDeepLinkHandler.DeepLinkResult.Error)
        val error = result as NsAnalyticsDeepLinkHandler.DeepLinkResult.Error
        assertEquals("API URL cannot be an internal address", error.message)
    }

    @Test
    fun `parseUri returns Error for 10-x-x-x private range api_url`() {
        val uri = buildValidUri(apiUrl = "https://10.0.0.1/api")

        val result = NsAnalyticsDeepLinkHandler.parseUri(uri)

        assertTrue(result is NsAnalyticsDeepLinkHandler.DeepLinkResult.Error)
        val error = result as NsAnalyticsDeepLinkHandler.DeepLinkResult.Error
        assertEquals("API URL cannot be an internal address", error.message)
    }

    @Test
    fun `parseUri returns Error for 172-16 private range api_url`() {
        val uri = buildValidUri(apiUrl = "https://172.16.0.1/api")

        val result = NsAnalyticsDeepLinkHandler.parseUri(uri)

        assertTrue(result is NsAnalyticsDeepLinkHandler.DeepLinkResult.Error)
        val error = result as NsAnalyticsDeepLinkHandler.DeepLinkResult.Error
        assertEquals("API URL cannot be an internal address", error.message)
    }

    @Test
    fun `parseUri returns Error for 172-31 private range api_url`() {
        val uri = buildValidUri(apiUrl = "https://172.31.255.255/api")

        val result = NsAnalyticsDeepLinkHandler.parseUri(uri)

        assertTrue(result is NsAnalyticsDeepLinkHandler.DeepLinkResult.Error)
        val error = result as NsAnalyticsDeepLinkHandler.DeepLinkResult.Error
        assertEquals("API URL cannot be an internal address", error.message)
    }

    @Test
    fun `parseUri allows 172-15 which is not private range`() {
        val uri = buildValidUri(apiUrl = "https://172.15.0.1/api")

        val result = NsAnalyticsDeepLinkHandler.parseUri(uri)

        assertTrue(result is NsAnalyticsDeepLinkHandler.DeepLinkResult.Success)
    }

    @Test
    fun `parseUri allows 172-32 which is not private range`() {
        val uri = buildValidUri(apiUrl = "https://172.32.0.1/api")

        val result = NsAnalyticsDeepLinkHandler.parseUri(uri)

        assertTrue(result is NsAnalyticsDeepLinkHandler.DeepLinkResult.Success)
    }

    @Test
    fun `parseUri returns Error for 192-168 private range api_url`() {
        val uri = buildValidUri(apiUrl = "https://192.168.1.1/api")

        val result = NsAnalyticsDeepLinkHandler.parseUri(uri)

        assertTrue(result is NsAnalyticsDeepLinkHandler.DeepLinkResult.Error)
        val error = result as NsAnalyticsDeepLinkHandler.DeepLinkResult.Error
        assertEquals("API URL cannot be an internal address", error.message)
    }

    @Test
    fun `parseUri returns Error for 169-254 link-local api_url`() {
        val uri = buildValidUri(apiUrl = "https://169.254.1.1/api")

        val result = NsAnalyticsDeepLinkHandler.parseUri(uri)

        assertTrue(result is NsAnalyticsDeepLinkHandler.DeepLinkResult.Error)
        val error = result as NsAnalyticsDeepLinkHandler.DeepLinkResult.Error
        assertEquals("API URL cannot be an internal address", error.message)
    }

    // === Non-Applicable URIs ===

    @Test
    fun `parseUri returns NotApplicable for wrong host`() {
        val uri = Uri.Builder()
            .scheme("https")
            .authority("example.com")
            .path(VALID_PATH)
            .appendQueryParameter("token", VALID_TOKEN)
            .appendQueryParameter("workspace_id", VALID_WORKSPACE_ID)
            .appendQueryParameter("api_url", VALID_API_URL)
            .build()

        val result = NsAnalyticsDeepLinkHandler.parseUri(uri)

        assertTrue(result is NsAnalyticsDeepLinkHandler.DeepLinkResult.NotApplicable)
    }

    @Test
    fun `parseUri returns NotApplicable for wrong path`() {
        val uri = Uri.Builder()
            .scheme("https")
            .authority(VALID_HOST)
            .path("/other/path")
            .appendQueryParameter("token", VALID_TOKEN)
            .appendQueryParameter("workspace_id", VALID_WORKSPACE_ID)
            .appendQueryParameter("api_url", VALID_API_URL)
            .build()

        val result = NsAnalyticsDeepLinkHandler.parseUri(uri)

        assertTrue(result is NsAnalyticsDeepLinkHandler.DeepLinkResult.NotApplicable)
    }

    // === isNsAnalyticsDeepLinkUri ===

    @Test
    fun `isNsAnalyticsDeepLinkUri returns true for valid URI`() {
        val uri = buildValidUri()

        assertTrue(NsAnalyticsDeepLinkHandler.isNsAnalyticsDeepLinkUri(uri))
    }

    @Test
    fun `isNsAnalyticsDeepLinkUri returns false for invalid host`() {
        val uri = Uri.Builder()
            .scheme("https")
            .authority("wrong.host.com")
            .path(VALID_PATH)
            .build()

        assertFalse(NsAnalyticsDeepLinkHandler.isNsAnalyticsDeepLinkUri(uri))
    }

    @Test
    fun `isNsAnalyticsDeepLinkUri returns false for invalid path`() {
        val uri = Uri.Builder()
            .scheme("https")
            .authority(VALID_HOST)
            .path("/wrong/path")
            .build()

        assertFalse(NsAnalyticsDeepLinkHandler.isNsAnalyticsDeepLinkUri(uri))
    }

    @Test
    fun `isNsAnalyticsDeepLinkUri returns true for path with trailing content`() {
        // The path check uses startsWith, so additional path segments should still match
        val uri = Uri.Builder()
            .scheme("https")
            .authority(VALID_HOST)
            .path("$VALID_PATH/extra")
            .build()

        assertTrue(NsAnalyticsDeepLinkHandler.isNsAnalyticsDeepLinkUri(uri))
    }

    // === Workspace Name Tests ===

    @Test
    fun `parseUri extracts workspace_name when provided`() {
        val uri = buildValidUri(workspaceName = "My Test Workspace")

        val result = NsAnalyticsDeepLinkHandler.parseUri(uri)

        assertTrue(result is NsAnalyticsDeepLinkHandler.DeepLinkResult.Success)
        val success = result as NsAnalyticsDeepLinkHandler.DeepLinkResult.Success
        assertEquals("My Test Workspace", success.qrData.workspaceName)
    }

    @Test
    fun `parseUri returns null workspace_name when not provided`() {
        val uri = buildValidUri()

        val result = NsAnalyticsDeepLinkHandler.parseUri(uri)

        assertTrue(result is NsAnalyticsDeepLinkHandler.DeepLinkResult.Success)
        val success = result as NsAnalyticsDeepLinkHandler.DeepLinkResult.Success
        assertNull(success.qrData.workspaceName)
    }

    @Test
    fun `parseUri handles URL-encoded workspace_name with special characters`() {
        // Test with spaces, apostrophes, and ampersands
        val uri = buildValidUri(workspaceName = "John's Team & Co.")

        val result = NsAnalyticsDeepLinkHandler.parseUri(uri)

        assertTrue(result is NsAnalyticsDeepLinkHandler.DeepLinkResult.Success)
        val success = result as NsAnalyticsDeepLinkHandler.DeepLinkResult.Success
        assertEquals("John's Team & Co.", success.qrData.workspaceName)
    }

    @Test
    fun `parseUri treats empty workspace_name as null`() {
        val uri = buildValidUri(workspaceName = "")

        val result = NsAnalyticsDeepLinkHandler.parseUri(uri)

        assertTrue(result is NsAnalyticsDeepLinkHandler.DeepLinkResult.Success)
        val success = result as NsAnalyticsDeepLinkHandler.DeepLinkResult.Success
        assertNull(success.qrData.workspaceName)
    }

    @Test
    fun `parseUri treats blank workspace_name as null`() {
        val uri = buildValidUri(workspaceName = "   ")

        val result = NsAnalyticsDeepLinkHandler.parseUri(uri)

        assertTrue(result is NsAnalyticsDeepLinkHandler.DeepLinkResult.Success)
        val success = result as NsAnalyticsDeepLinkHandler.DeepLinkResult.Success
        assertNull(success.qrData.workspaceName)
    }

    @Test
    fun `parseUri handles unicode characters in workspace_name`() {
        val uri = buildValidUri(workspaceName = "日本語のワークスペース")

        val result = NsAnalyticsDeepLinkHandler.parseUri(uri)

        assertTrue(result is NsAnalyticsDeepLinkHandler.DeepLinkResult.Success)
        val success = result as NsAnalyticsDeepLinkHandler.DeepLinkResult.Success
        assertEquals("日本語のワークスペース", success.qrData.workspaceName)
    }

    @Test
    fun `parseUri returns Error for workspace_name exceeding max length`() {
        val longName = "a".repeat(101)
        val uri = buildValidUri(workspaceName = longName)

        val result = NsAnalyticsDeepLinkHandler.parseUri(uri)

        assertTrue(result is NsAnalyticsDeepLinkHandler.DeepLinkResult.Error)
        val error = result as NsAnalyticsDeepLinkHandler.DeepLinkResult.Error
        assertEquals("Workspace name too long", error.message)
    }

    @Test
    fun `parseUri accepts workspace_name at max length boundary`() {
        val maxLengthName = "a".repeat(100)
        val uri = buildValidUri(workspaceName = maxLengthName)

        val result = NsAnalyticsDeepLinkHandler.parseUri(uri)

        assertTrue(result is NsAnalyticsDeepLinkHandler.DeepLinkResult.Success)
        val success = result as NsAnalyticsDeepLinkHandler.DeepLinkResult.Success
        assertEquals(maxLengthName, success.qrData.workspaceName)
    }
}
